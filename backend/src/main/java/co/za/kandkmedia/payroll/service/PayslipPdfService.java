package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Company;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.repository.CompanyRepository;
import co.za.kandkmedia.payroll.repository.PayrollRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws the company payslip with PDFBox, reproducing the approved K &amp; K
 * Media payslip design (Sept 2026 reference) exactly: a US Letter page with a
 * navy "PAYSLIP" header bar, a grey three-column details panel, side-by-side
 * EARNINGS / DEDUCTIONS boxes with shaded totals rows, a navy NETT PAY bar,
 * and YEAR TO DATE TOTALS / ADDITIONAL INFO boxes along the bottom.
 *
 * Every coordinate, colour and font size below comes from that reference
 * document; only the per-employee values change. It uses the PDF standard
 * Helvetica fonts, so output is identical on every server with no system
 * fonts or office software installed.
 */
@Service
@RequiredArgsConstructor
public class PayslipPdfService {

    // Colours from the reference design.
    private static final Color NAVY = new Color(0x1F, 0x2A, 0x44);
    private static final Color PANEL = new Color(0xF2, 0xF4, 0xF7);
    private static final Color RULE = new Color(0x9A, 0xA3, 0xB2);
    private static final Color FOOTER_GREY = new Color(0x66, 0x66, 0x66);

    private static final PDFont REGULAR = PDType1Font.HELVETICA;
    private static final PDFont BOLD = PDType1Font.HELVETICA_BOLD;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter GENERATED_FMT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");
    private static final String DEFAULT_COMPANY_NAME = "K & K MEDIA (PTY) LTD";
    /** The company address block exactly as it appears on the approved design. */
    private static final List<String> COMPANY_ADDRESS = List.of(
            "CONSTANTIA SQUARE OFFICE", "16TH ROAD", "RANDJESFONTEIN, MIDRAND", "1685");

    // Page frame: 36pt margins on a 612 x 792 page, two 265pt columns with a 10pt gutter.
    private static final float LEFT_X = 36, RIGHT_X = 311, COL_W = 265, FULL_W = 540;
    private static final float ROW_STEP = 14;

    private final PayrollRepository payrollRepository;
    private final CompanyRepository companyRepository;

    @Value("${app.verification-base-url}")
    private String verificationBaseUrl;

    public byte[] generate(Payroll payroll) {
        Employee employee = payroll.getEmployee();
        BigDecimal[] ytd = yearToDateTotals(employee.getId(), payroll.getPayPeriod());
        String companyName = companyName();

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle("Payslip - K & K Media (Pty) Ltd");
            info.setAuthor("K & K Media (Pty) Ltd");

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setLineWidth(0.8f);
                drawHeader(cs, companyName);
                drawDetailsPanel(cs, payroll, employee, companyName);
                drawEarnings(cs, payroll);
                drawDeductions(cs, payroll);
                drawNettPay(cs, payroll);
                drawYearToDate(cs, payroll, ytd);
                drawAdditionalInfo(doc, cs, payroll, employee);
                drawFooter(cs);
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate payslip PDF", e);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Sections                                                           */
    /* ------------------------------------------------------------------ */

    private void drawHeader(PDPageContentStream cs, String companyName) throws IOException {
        fillRect(cs, NAVY, LEFT_X, 726, FULL_W, 30);
        text(cs, BOLD, 15, 46, 736, "PAYSLIP", Color.WHITE);
        textRight(cs, REGULAR, 9, 566, 737, companyName, Color.WHITE);
    }

    private void drawDetailsPanel(PDPageContentStream cs, Payroll payroll, Employee employee, String companyName) throws IOException {
        cs.setNonStrokingColor(PANEL);
        cs.setStrokingColor(RULE);
        cs.addRect(LEFT_X, 626, FULL_W, 92);
        cs.fillAndStroke();

        // Column 1 — employee
        label(cs, 46, 702, "Company");
        value(cs, 104, 702, fit(companyName, 122));
        label(cs, 46, 689, "Emp Code");
        value(cs, 104, 689, dash(employee.getEmployeeCode()));
        label(cs, 46, 676, "Emp Name");
        value(cs, 104, 676, fit(upper(employee.getFullName()), 122));
        label(cs, 46, 663, "Emp Address");
        List<String> address = employeeAddressLines(employee);
        for (int i = 0; i < address.size(); i++) {
            value(cs, 104, 663 - 11 * i, address.get(i));
        }

        // Column 2 — company address
        label(cs, 238.4f, 702, "Co. Address");
        for (int i = 0; i < COMPANY_ADDRESS.size(); i++) {
            value(cs, 294.4f, 702 - 11 * i, COMPANY_ADDRESS.get(i));
        }

        // Column 3 — payment
        label(cs, 432.8f, 702, "Payment Date");
        value(cs, 496.8f, 702, paymentDate(payroll.getPayPeriod()));
        label(cs, 432.8f, 689, "Date Engaged");
        value(cs, 496.8f, 689, employee.getStartDate() != null ? employee.getStartDate().format(DATE_FMT) : "-");
        label(cs, 432.8f, 676, "Account No");
        value(cs, 496.8f, 676, fit(dash(employee.getBankAccountNumber()), 76));
        label(cs, 432.8f, 663, "Branch Code");
        value(cs, 496.8f, 663, fit(dash(employee.getBankBranchCode()), 76));

        cs.setStrokingColor(RULE);
        line(cs, 230.4f, 634, 230.4f, 710);
        line(cs, 424.8f, 634, 424.8f, 710);
    }

    private void drawEarnings(PDPageContentStream cs, Payroll payroll) throws IOException {
        boxWithTitle(cs, LEFT_X, 286, 330, "EARNINGS");
        columnHeader(cs, 44, 584, "Description");
        headerRight(cs, 221, 584, "Days");
        headerRight(cs, 293, 584, "Amount (R)");
        cs.setStrokingColor(RULE);
        line(cs, 42, 580, 295, 580);

        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"Normal Time", payroll.getBasicSalary()});
        addIfPositive(rows, "Housing Allowance", payroll.getHousingAllowance());
        addIfPositive(rows, "Transport Allowance", payroll.getTransportAllowance());
        addIfPositive(rows, "Overtime", payroll.getOvertime());
        addIfPositive(rows, "Bonus", payroll.getBonus());

        float y = 567;
        for (Object[] row : rows) {
            text(cs, REGULAR, 8.5f, 44, y, (String) row[0], Color.BLACK);
            textRight(cs, REGULAR, 8.5f, 221, y, "-", Color.BLACK);
            textRight(cs, REGULAR, 8.5f, 293, y, money((BigDecimal) row[1]), Color.BLACK);
            y -= ROW_STEP;
        }

        totalsRow(cs, LEFT_X);
        text(cs, BOLD, 8.5f, 44, 293, "Total Earnings", Color.BLACK);
        textRight(cs, BOLD, 8.5f, 293, 293, money(payroll.getGrossPay()), Color.BLACK);
    }

    private void drawDeductions(PDPageContentStream cs, Payroll payroll) throws IOException {
        boxWithTitle(cs, RIGHT_X, 286, 330, "DEDUCTIONS");
        columnHeader(cs, 319, 584, "Description");
        headerRight(cs, 456, 584, "Days");
        headerRight(cs, 508, 584, "Amount (R)");
        headerRight(cs, 568, 584, "Opening Bal.");
        cs.setStrokingColor(RULE);
        line(cs, 317, 580, 570, 580);

        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"Tax", payroll.getPaye()});
        rows.add(new Object[]{"U.I.F.", payroll.getUif()});
        addIfPositive(rows, "Other Deductions", payroll.getOtherDeductions());

        float y = 567;
        for (Object[] row : rows) {
            text(cs, REGULAR, 8.5f, 319, y, (String) row[0], Color.BLACK);
            textRight(cs, REGULAR, 8.5f, 456, y, "-", Color.BLACK);
            textRight(cs, REGULAR, 8.5f, 508, y, money((BigDecimal) row[1]), Color.BLACK);
            textRight(cs, REGULAR, 8.5f, 568, y, "-", Color.BLACK);
            y -= ROW_STEP;
        }

        totalsRow(cs, RIGHT_X);
        text(cs, BOLD, 8.5f, 319, 293, "Total Deductions", Color.BLACK);
        textRight(cs, BOLD, 8.5f, 508, 293, money(payroll.getTotalDeductions()), Color.BLACK);
    }

    private void drawNettPay(PDPageContentStream cs, Payroll payroll) throws IOException {
        fillRect(cs, NAVY, RIGHT_X, 248, COL_W, 28);
        text(cs, BOLD, 11, 321, 258, "NETT PAY", Color.WHITE);
        textRight(cs, BOLD, 14, 566, 257, "R " + money(payroll.getNetPay()), Color.WHITE);
    }

    private void drawYearToDate(PDPageContentStream cs, Payroll payroll, BigDecimal[] ytd) throws IOException {
        boxWithTitle(cs, LEFT_X, 88, 150, "YEAR TO DATE TOTALS");
        text(cs, REGULAR, 8.5f, 44, 202, "Total Earnings", Color.BLACK);
        textRight(cs, REGULAR, 8.5f, 293, 202, money(ytd[0]), Color.BLACK);
        text(cs, REGULAR, 8.5f, 44, 188, "Total Deductions", Color.BLACK);
        textRight(cs, REGULAR, 8.5f, 293, 188, money(ytd[1]), Color.BLACK);

        cs.setStrokingColor(RULE);
        line(cs, LEFT_X, 168, LEFT_X + COL_W, 168);
        fillRect(cs, PANEL, LEFT_X + 0.4f, 150, COL_W - 0.8f, 18);
        textCentered(cs, BOLD, 9, LEFT_X + COL_W / 2, 155, "CURRENT PERIOD", Color.BLACK);
        cs.setStrokingColor(RULE);
        line(cs, LEFT_X, 150, LEFT_X + COL_W, 150);

        // Employer UIF contribution matches the employee's 1% UIF deduction.
        text(cs, REGULAR, 8.5f, 44, 134, "Co. Contributions", Color.BLACK);
        textRight(cs, REGULAR, 8.5f, 293, 134, money(payroll.getUif()), Color.BLACK);
    }

    /** The reference leaves this panel empty; it carries the details an employee needs to identify and verify the payslip. */
    private void drawAdditionalInfo(PDDocument doc, PDPageContentStream cs, Payroll payroll, Employee employee) throws IOException {
        boxWithTitle(cs, RIGHT_X, 88, 150, "ADDITIONAL INFO");

        boolean sealed = payroll.getPayslipId() != null && payroll.getVerificationCode() != null;
        float textWidth = sealed ? 150 : 245;
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Pay Period", payPeriodLabel(payroll.getPayPeriod())});
        rows.add(new String[]{"Job Title", dash(employee.getPosition())});
        rows.add(new String[]{"Department", employee.getDepartment() != null ? dash(employee.getDepartment().getName()) : "-"});
        if (notBlank(employee.getIncomeTaxNumber())) rows.add(new String[]{"Tax No", employee.getIncomeTaxNumber()});
        if (sealed) {
            rows.add(new String[]{"Payslip ID", payroll.getPayslipId()});
            rows.add(new String[]{"Verify Code", payroll.getVerificationCode()});
            if (payroll.getDocumentGeneratedAt() != null) {
                rows.add(new String[]{"Generated", payroll.getDocumentGeneratedAt().format(GENERATED_FMT)});
            }
        }

        float y = 204;
        for (String[] row : rows) {
            text(cs, BOLD, 8, 319, y, row[0], Color.BLACK);
            text(cs, REGULAR, 8, 372, y, fit(row[1], textWidth - 53), Color.BLACK);
            y -= 12;
        }

        if (sealed) {
            String verifyUrl = verificationBaseUrl + "/" + payroll.getVerificationCode();
            PDImageXObject qr = qrImage(doc, verifyUrl);
            if (qr != null) {
                cs.drawImage(qr, 484, 104, 84, 84);
                textCentered(cs, REGULAR, 6.5f, 526, 96, "Scan to verify", FOOTER_GREY);
            }
        }
    }

    private void drawFooter(PDPageContentStream cs) throws IOException {
        text(cs, REGULAR, 7.5f, 36, 30, "This payslip is computer generated. Amounts in South African Rand (ZAR).", FOOTER_GREY);
        textRight(cs, REGULAR, 7.5f, 576, 30, "Page 1 of 1", FOOTER_GREY);
    }

    /* ------------------------------------------------------------------ */
    /* Drawing helpers                                                    */
    /* ------------------------------------------------------------------ */

    /** Outlined box with a navy title bar across its top 18pt. */
    private void boxWithTitle(PDPageContentStream cs, float x, float y, float height, String title) throws IOException {
        cs.setStrokingColor(RULE);
        cs.addRect(x, y, COL_W, height);
        cs.stroke();
        fillRect(cs, NAVY, x, y + height - 18, COL_W, 18);
        textCentered(cs, BOLD, 10, x + COL_W / 2, y + height - 12.5f, title, Color.WHITE);
    }

    /** Shaded 20pt totals strip along the bottom of an EARNINGS/DEDUCTIONS box. */
    private void totalsRow(PDPageContentStream cs, float x) throws IOException {
        fillRect(cs, PANEL, x + 0.4f, 286.4f, COL_W - 0.8f, 20);
        cs.setStrokingColor(RULE);
        line(cs, x, 306, x + COL_W, 306);
    }

    private void columnHeader(PDPageContentStream cs, float x, float y, String s) throws IOException {
        text(cs, BOLD, 8.5f, x, y, s, Color.BLACK);
    }

    private void headerRight(PDPageContentStream cs, float right, float y, String s) throws IOException {
        textRight(cs, BOLD, 8.5f, right, y, s, Color.BLACK);
    }

    private void label(PDPageContentStream cs, float x, float y, String s) throws IOException {
        text(cs, BOLD, 8, x, y, s, Color.BLACK);
    }

    private void value(PDPageContentStream cs, float x, float y, String s) throws IOException {
        text(cs, REGULAR, 8, x, y, s, Color.BLACK);
    }

    private void fillRect(PDPageContentStream cs, Color color, float x, float y, float w, float h) throws IOException {
        cs.setNonStrokingColor(color);
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    private void line(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws IOException {
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private void text(PDPageContentStream cs, PDFont font, float size, float x, float y, String s, Color color) throws IOException {
        cs.beginText();
        cs.setNonStrokingColor(color);
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(safe(s));
        cs.endText();
    }

    private void textRight(PDPageContentStream cs, PDFont font, float size, float right, float y, String s, Color color) throws IOException {
        text(cs, font, size, right - width(font, size, s), y, s, color);
    }

    private void textCentered(PDPageContentStream cs, PDFont font, float size, float center, float y, String s, Color color) throws IOException {
        text(cs, font, size, center - width(font, size, s) / 2, y, s, color);
    }

    private float width(PDFont font, float size, String s) throws IOException {
        return font.getStringWidth(safe(s)) / 1000f * size;
    }

    /** Shortens a value with "..." so it never runs into the next column. */
    private String fit(String s, float maxWidth) throws IOException {
        String value = safe(s);
        if (width(REGULAR, 8, value) <= maxWidth) return value;
        while (!value.isEmpty() && width(REGULAR, 8, value + "...") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "...";
    }

    private PDImageXObject qrImage(PDDocument doc, String content) {
        try {
            var matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 300, 300);
            return LosslessFactory.createFromImage(doc, MatrixToImageWriter.toBufferedImage(matrix));
        } catch (WriterException | IOException e) {
            return null; // non-fatal — the verify code is still printed as text
        }
    }

    /* ------------------------------------------------------------------ */
    /* Data                                                               */
    /* ------------------------------------------------------------------ */

    private String companyName() {
        Company company = companyRepository.findAll().stream().findFirst().orElse(null);
        return company != null && notBlank(company.getName()) ? upper(company.getName()) : DEFAULT_COMPANY_NAME;
    }

    /** Residential address in the design's style: uppercase, word-wrapped over up to 4 lines. */
    private List<String> employeeAddressLines(Employee e) throws IOException {
        List<String> parts = new ArrayList<>();
        String complex = String.join(" ", nonBlank(e.getResUnitNumber(), e.getResComplexName()));
        if (!complex.isBlank()) parts.add(complex);
        String street = String.join(" ", nonBlank(e.getResStreetNumber(), e.getResStreetName()));
        if (!street.isBlank()) parts.add(street);
        String area = String.join(", ", nonBlank(e.getResSuburb(), e.getResCity()));
        if (!area.isBlank()) parts.add(area);
        if (notBlank(e.getResPostalCode())) parts.add(e.getResPostalCode());
        if (parts.isEmpty()) return List.of("-");

        float maxWidth = 122;
        List<String> lines = new ArrayList<>();
        for (String part : parts) {
            StringBuilder current = new StringBuilder();
            for (String word : upper(part).split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (width(REGULAR, 8, candidate) > maxWidth && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (!current.isEmpty()) lines.add(current.toString());
        }
        List<String> fitted = new ArrayList<>();
        for (String l : lines.subList(0, Math.min(4, lines.size()))) fitted.add(fit(l, maxWidth));
        return fitted;
    }

    private BigDecimal[] yearToDateTotals(Long employeeId, String currentPeriod) {
        try {
            YearMonth current = YearMonth.parse(currentPeriod);
            // South African tax year runs March to February.
            YearMonth taxYearStart = current.getMonthValue() >= 3
                    ? YearMonth.of(current.getYear(), 3)
                    : YearMonth.of(current.getYear() - 1, 3);

            BigDecimal gross = BigDecimal.ZERO, deductions = BigDecimal.ZERO;
            for (Payroll p : payrollRepository.findByEmployeeIdOrderByPayPeriodDesc(employeeId)) {
                YearMonth period = YearMonth.parse(p.getPayPeriod());
                if (!period.isBefore(taxYearStart) && !period.isAfter(current)) {
                    gross = gross.add(nz(p.getGrossPay()));
                    deductions = deductions.add(nz(p.getTotalDeductions()));
                }
            }
            return new BigDecimal[]{gross, deductions};
        } catch (Exception e) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        }
    }

    private String paymentDate(String payPeriod) {
        try {
            LocalDate lastDay = YearMonth.parse(payPeriod).atEndOfMonth();
            return lastDay.format(DATE_FMT);
        } catch (Exception e) {
            return "-";
        }
    }

    private String payPeriodLabel(String payPeriod) {
        try {
            return YearMonth.parse(payPeriod).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        } catch (Exception e) {
            return dash(payPeriod);
        }
    }

    private static String money(BigDecimal value) {
        DecimalFormat fmt = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return fmt.format(value == null ? BigDecimal.ZERO : value);
    }

    private static void addIfPositive(List<Object[]> rows, String label, BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) rows.add(new Object[]{label, amount});
    }

    private static List<String> nonBlank(String... values) {
        List<String> out = new ArrayList<>();
        for (String v : values) if (notBlank(v)) out.add(v.trim());
        return out;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String dash(String s) {
        return notBlank(s) ? s.trim() : "-";
    }

    private static String upper(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ENGLISH);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Standard-14 fonts only cover WinAnsi (Latin-1) — replace anything else so PDFBox can't throw on a name. */
    private static String safe(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(c >= 32 && c <= 255 ? c : (c == '’' || c == '‘' ? '\'' : '?'));
        }
        return sb.toString();
    }
}
