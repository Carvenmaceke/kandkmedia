package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Company;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.Payroll;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Fills the company's real Sage VIP payslip template (a Word document built
 * from absolutely-positioned textbox shapes, not a table) with per-employee
 * data, producing a byte-for-byte structural duplicate of the reference
 * document — same fonts, borders, column layout and page size — with only
 * the variable fields swapped in.
 *
 * How the template works: every value that varies (dates, amounts, names)
 * sits in its own DrawingML shape (a "Rectangle N" textbox) at a fixed
 * position, duplicated once for modern Word (mc:Choice, the branch every
 * renderer that understands 2010+ shapes — including the LibreOffice
 * conversion this feeds into — actually uses) and once for legacy Word
 * (mc:Fallback, effectively dead markup for our purposes since nothing
 * downstream ever renders it). Only mc:Choice is edited. The base template
 * resource already has each single-value field's shape collapsed to one
 * run holding a placeholder token, widened just enough to hold realistic
 * data without word-wrapping (the reference sample's shapes were sized
 * exactly to that one sample's text).
 *
 * Two sections legitimately need a variable NUMBER of rows the template
 * itself only shows one example of: extra earnings lines beyond basic
 * salary, and an extra "Other Deductions" line — the template's earnings/
 * deductions boxes are far taller than one row, reserved for exactly this.
 * Those rows, the three Year-To-Date total rows the template's "YEAR TO
 * DATE TOTALS" box reserves space for but leaves blank in the reference
 * sample, and the verification/QR block are built as new shapes cloned
 * from the template's own row styling and inserted at runtime.
 */
@Service
public class PayslipDocxTemplateService {

    private static final String TEMPLATE_RESOURCE = "templates/payslip-template.docx";
    private static final String DOCUMENT_ENTRY = "word/document.xml";
    private static final String RELS_ENTRY = "word/_rels/document.xml.rels";
    private static final String CONTENT_TYPES_ENTRY = "[Content_Types].xml";
    private static final String QR_MEDIA_ENTRY = "word/media/verification-qr.png";
    private static final String QR_RELATIONSHIP_ID = "rIdVerificationQr";

    private static final DecimalFormat AMOUNT_FMT = new DecimalFormat("0.00");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** EMU spacing between successive cloned rows — matches the template's own Tax/U.I.F. row spacing. */
    private static final long ROW_HEIGHT = 119670L;

    // Earnings box (left column) row geometry, taken from the template's own "Normal Time" row.
    private static final long EARNINGS_LABEL_X = 86868L, EARNINGS_LABEL_CX = 1900000L;
    private static final long EARNINGS_VALUE_X = 2797611L, EARNINGS_VALUE_CX = 600000L;
    private static final long EARNINGS_ROW1_Y = 1592141L;

    // Deductions box (right of earnings) row geometry, taken from the template's own "U.I.F." row.
    private static final long DEDUCTIONS_LABEL_X = 3397758L, DEDUCTIONS_LABEL_CX = 1900000L;
    private static final long DEDUCTIONS_VALUE_X = 6071634L, DEDUCTIONS_VALUE_CX = 900000L;
    private static final long DEDUCTIONS_ROW2_Y = 1711811L; // U.I.F. row — the last static row

    // Year-To-Date panel (left column, under "YEAR TO DATE TOTALS" title).
    private static final long YTD_LABEL_X = 90678L, YTD_LABEL_CX = 1900000L;
    private static final long YTD_VALUE_X = 2278561L, YTD_VALUE_CX = 700000L;
    private static final long YTD_ROWS_START_Y = 6965695L;

    // Additional Info panel (right column) — verification block + QR, below the signature line.
    private static final long VERIFICATION_LABEL_X = 3397758L, VERIFICATION_LABEL_CX = 3200000L;
    private static final long VERIFICATION_START_Y = 7250000L;
    private static final long QR_X = 5900000L, QR_Y = 7250000L, QR_SIZE = 900000L;

    private static final Map<String, byte[]> TEMPLATE_ENTRIES = loadTemplateEntries();

    @Value("${app.verification-base-url}")
    private String verificationBaseUrl;

    private static Map<String, byte[]> loadTemplateEntries() {
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        try (InputStream in = new ClassPathResource(TEMPLATE_RESOURCE).getInputStream();
             ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), zis.readAllBytes());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load payslip docx template: " + TEMPLATE_RESOURCE, e);
        }
        return entries;
    }

    public byte[] build(Payroll payroll, BigDecimal[] yearToDateTotals) {
        Employee employee = payroll.getEmployee();

        String xml = new String(TEMPLATE_ENTRIES.get(DOCUMENT_ENTRY), StandardCharsets.UTF_8);
        xml = substitute(xml, fieldValues(payroll, employee));
        xml = insertEarningsRows(xml, payroll);
        xml = insertDeductionRow(xml, payroll);
        xml = insertYtdRows(xml, yearToDateTotals);

        boolean hasVerification = payroll.getPayslipId() != null;
        byte[] qrPng = null;
        Map<String, byte[]> entries = new LinkedHashMap<>(TEMPLATE_ENTRIES);

        if (hasVerification) {
            try {
                String verifyUrl = verificationBaseUrl + "/" + payroll.getVerificationCode();
                qrPng = buildQrPng(verifyUrl);
            } catch (WriterException e) {
                qrPng = null; // non-fatal — verification text still prints, just no scannable code
            }
            xml = insertVerificationBlock(xml, payroll, qrPng != null);
            if (qrPng != null) {
                entries.put(QR_MEDIA_ENTRY, qrPng);
                entries.put(RELS_ENTRY, addImageRelationship(new String(TEMPLATE_ENTRIES.get(RELS_ENTRY), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
                entries.put(CONTENT_TYPES_ENTRY, ensurePngContentType(new String(TEMPLATE_ENTRIES.get(CONTENT_TYPES_ENTRY), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
            }
        }

        entries.put(DOCUMENT_ENTRY, xml.getBytes(StandardCharsets.UTF_8));
        return zip(entries);
    }

    private Map<String, String> fieldValues(Payroll payroll, Employee employee) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("___PAYMENT_DATE___", paymentDate(payroll.getPayPeriod()));
        values.put("___DATE_ENGAGED___", employee.getStartDate() != null ? employee.getStartDate().format(DATE_FMT) : "-");
        values.put("___BANK_ACCOUNT_NO___", nullToDash(employee.getBankAccountNumber()));
        values.put("___BANK_BRANCH_CODE___", nullToDash(employee.getBankBranchCode()));
        values.put("___EMP_CODE___", nullToDash(employee.getEmployeeCode()));
        values.put("___EMP_NAME___", " " + employee.getFullName());

        List<String> addressLines = wrapAddress(employeeAddress(employee));
        values.put("___EMP_ADDR_LINE1___", addressLines.get(0));
        values.put("___EMP_ADDR_LINE2___", addressLines.get(1));
        values.put("___EMP_ADDR_LINE3___", addressLines.get(2));
        values.put("___EMP_ADDR_LINE4___", addressLines.get(3));

        values.put("___TAX_AMOUNT___", money(payroll.getPaye()));
        values.put("___UIF_AMOUNT___", money(payroll.getUif()));
        values.put("___EARNINGS_ROW1_LABEL___", "Basic Salary");
        values.put("___EARNINGS_ROW1_AMOUNT___", money(payroll.getBasicSalary()));
        values.put("___TOTAL_EARNINGS___", money(payroll.getGrossPay()));
        values.put("___TOTAL_DEDUCTIONS___", money(payroll.getTotalDeductions()));
        values.put("___NETT_PAY___", money(payroll.getNetPay()));
        values.put("___EMPLOYER_CONTRIBUTION___", money(payroll.getUif()));
        return values;
    }

    private String substitute(String xml, Map<String, String> values) {
        for (Map.Entry<String, String> e : values.entrySet()) {
            xml = xml.replace(e.getKey(), xmlEscape(e.getValue()));
        }
        return xml;
    }

    /** Extra earnings lines beyond Basic Salary (row 1, already in the base template). */
    private String insertEarningsRows(String xml, Payroll payroll) {
        record Line(String label, BigDecimal amount) {}
        List<Line> extra = List.of(
                new Line("Housing Allowance", payroll.getHousingAllowance()),
                new Line("Transport Allowance", payroll.getTransportAllowance()),
                new Line("Overtime", payroll.getOvertime()),
                new Line("Bonus", payroll.getBonus())
        ).stream().filter(l -> positive(l.amount())).toList();

        StringBuilder rows = new StringBuilder();
        long y = EARNINGS_ROW1_Y;
        for (Line line : extra) {
            y += ROW_HEIGHT;
            rows.append(row(EARNINGS_LABEL_X, y, EARNINGS_LABEL_CX, line.label()));
            rows.append(row(EARNINGS_VALUE_X, y, EARNINGS_VALUE_CX, money(line.amount())));
        }
        return insertAfterShape(xml, "Rectangle 46", rows.toString());
    }

    /** "Other Deductions" line, only when the payroll actually has one. */
    private String insertDeductionRow(String xml, Payroll payroll) {
        if (!positive(payroll.getOtherDeductions())) {
            return xml;
        }
        long y = DEDUCTIONS_ROW2_Y + ROW_HEIGHT;
        String rows = row(DEDUCTIONS_LABEL_X, y, DEDUCTIONS_LABEL_CX, "Other Deductions")
                + row(DEDUCTIONS_VALUE_X, y, DEDUCTIONS_VALUE_CX, money(payroll.getOtherDeductions()));
        return insertAfterShape(xml, "Rectangle 39", rows);
    }

    /** Fills the template's reserved-but-empty "YEAR TO DATE TOTALS" space with the 3 real totals. */
    private String insertYtdRows(String xml, BigDecimal[] yearToDateTotals) {
        String[] labels = {"Gross Earnings", "Deductions", "Nett Pay"};
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            long y = YTD_ROWS_START_Y + ROW_HEIGHT * i;
            rows.append(row(YTD_LABEL_X, y, YTD_LABEL_CX, labels[i]));
            rows.append(row(YTD_VALUE_X, y, YTD_VALUE_CX, money(yearToDateTotals[i])));
        }
        return insertAfterShape(xml, "Rectangle 61", rows.toString());
    }

    /** Payslip ID / verification code / hash / QR, in the "ADDITIONAL INFO" panel's free space. */
    private String insertVerificationBlock(String xml, Payroll payroll, boolean includeQr) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("Payslip ID: " + payroll.getPayslipId());
        lines.add("Verification Code: " + payroll.getVerificationCode());
        if (payroll.getDocumentGeneratedAt() != null) {
            lines.add("Generated: " + payroll.getDocumentGeneratedAt().format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm")));
        }
        if (payroll.getDocumentHash() != null) {
            String shortHash = payroll.getDocumentHash().substring(0, Math.min(24, payroll.getDocumentHash().length()));
            lines.add("SHA-256: " + shortHash + "...");
        }
        lines.add("No physical signature required.");
        lines.add(includeQr ? "Scan to verify authenticity:" : "Verify at: " + verificationBaseUrl + "/" + payroll.getVerificationCode());

        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            long y = VERIFICATION_START_Y + ROW_HEIGHT * i;
            rows.append(row(VERIFICATION_LABEL_X, y, VERIFICATION_LABEL_CX, lines.get(i)));
        }
        xml = insertAfterShape(xml, "Rectangle 64", rows.toString());

        if (includeQr) {
            String pic = "<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                    + "<pic:nvPicPr><pic:cNvPr id=\"9999\" name=\"Verification QR Code\"/><pic:cNvPicPr/></pic:nvPicPr>"
                    + "<pic:blipFill><a:blip r:embed=\"" + QR_RELATIONSHIP_ID + "\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>"
                    + "<pic:spPr><a:xfrm><a:off x=\"" + QR_X + "\" y=\"" + QR_Y + "\"/><a:ext cx=\"" + QR_SIZE + "\" cy=\"" + QR_SIZE + "\"/></a:xfrm>"
                    + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>";
            xml = insertAfterShape(xml, "Rectangle 64", pic);
        }
        return xml;
    }

    /** One label/value textbox shape, styled exactly like the template's own row shapes. */
    private String row(long x, long y, long cx, String text) {
        return "<wps:wsp><wps:cNvPr id=\"" + nextShapeId() + "\" name=\"GeneratedRow\"/><wps:cNvSpPr/>"
                + "<wps:spPr><a:xfrm><a:off x=\"" + x + "\" y=\"" + y + "\"/><a:ext cx=\"" + cx + "\" cy=\"133825\"/></a:xfrm>"
                + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:ln><a:noFill/></a:ln></wps:spPr>"
                + "<wps:txbx><w:txbxContent><w:p><w:r><w:rPr><w:rFonts w:ascii=\"Microsoft Sans Serif\" w:eastAsia=\"Microsoft Sans Serif\" "
                + "w:hAnsi=\"Microsoft Sans Serif\" w:cs=\"Microsoft Sans Serif\"/><w:sz w:val=\"14\"/></w:rPr>"
                + "<w:t xml:space=\"preserve\">" + xmlEscape(text) + "</w:t></w:r></w:p></w:txbxContent></wps:txbx>"
                + "<wps:bodyPr horzOverflow=\"overflow\" vert=\"horz\" lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\" rtlCol=\"0\" wrap=\"none\">"
                + "<a:noAutofit/></wps:bodyPr></wps:wsp>";
    }

    private final java.util.concurrent.atomic.AtomicInteger shapeIdSeq = new java.util.concurrent.atomic.AtomicInteger(20000);

    private int nextShapeId() {
        return shapeIdSeq.incrementAndGet();
    }

    /** Inserts new shape XML immediately after the named shape's closing </wps:wsp> tag. */
    private String insertAfterShape(String xml, String shapeName, String newShapesXml) {
        if (newShapesXml.isEmpty()) {
            return xml;
        }
        String marker = "name=\"" + shapeName + "\"";
        int idx = xml.indexOf(marker);
        if (idx == -1) {
            throw new IllegalStateException("Payslip template shape not found: " + shapeName);
        }
        int insertAt = xml.indexOf("</wps:wsp>", idx) + "</wps:wsp>".length();
        return xml.substring(0, insertAt) + newShapesXml + xml.substring(insertAt);
    }

    private String addImageRelationship(String rels) {
        String relationship = "<Relationship Id=\"" + QR_RELATIONSHIP_ID + "\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" "
                + "Target=\"media/verification-qr.png\"/>";
        return rels.replace("</Relationships>", relationship + "</Relationships>");
    }

    private String ensurePngContentType(String contentTypes) {
        if (contentTypes.contains("Extension=\"png\"")) {
            return contentTypes;
        }
        return contentTypes.replace("<Default Extension=\"xml\"",
                "<Default Extension=\"png\" ContentType=\"image/png\"/><Default Extension=\"xml\"");
    }

    private byte[] zip(Map<String, byte[]> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to package generated payslip docx", e);
        }
        return out.toByteArray();
    }

    private byte[] buildQrPng(String content) throws WriterException {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 300, 300);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode verification QR code", e);
        }
    }

    /** Greedy word-wrap across the 4 fixed address lines the template reserves. */
    private List<String> wrapAddress(String address) {
        String[] lines = {"", "", "", ""};
        if (address == null || address.isBlank()) {
            return List.of(lines);
        }
        int maxCharsPerLine = 34; // matches the ~2M-EMU widened address box at this font size
        List<String> wrapped = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : address.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (candidate.length() > maxCharsPerLine && !current.isEmpty()) {
                wrapped.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            wrapped.add(current.toString());
        }
        for (int i = 0; i < Math.min(4, wrapped.size()); i++) {
            lines[i] = wrapped.get(i);
        }
        return List.of(lines);
    }

    private String employeeAddress(Employee e) {
        List<String> parts = new java.util.ArrayList<>();
        if (notBlank(e.getResUnitNumber())) parts.add(e.getResUnitNumber());
        if (notBlank(e.getResComplexName())) parts.add(e.getResComplexName());
        String streetLine = ((notBlank(e.getResStreetNumber()) ? e.getResStreetNumber() + " " : "") + nullToEmpty(e.getResStreetName())).trim();
        if (!streetLine.isBlank()) parts.add(streetLine);
        if (notBlank(e.getResSuburb())) parts.add(e.getResSuburb());
        if (notBlank(e.getResCity())) parts.add(e.getResCity());
        if (notBlank(e.getResPostalCode())) parts.add(e.getResPostalCode());
        return String.join(", ", parts);
    }

    private String paymentDate(String payPeriod) {
        try {
            YearMonth ym = YearMonth.parse(payPeriod);
            LocalDate lastDay = ym.atEndOfMonth();
            return lastDay.format(DATE_FMT);
        } catch (Exception e) {
            return "-";
        }
    }

    private String money(BigDecimal value) {
        return AMOUNT_FMT.format(value == null ? BigDecimal.ZERO : value);
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private String xmlEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
