package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Company;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.repository.CompanyRepository;
import co.za.kandkmedia.payroll.repository.PayrollRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Redesigned to match the company's actual payslip layout (a bordered
 * grid: header info block, side-by-side Earnings/Deductions, boxed Nett
 * Pay, then Year-To-Date/Additional-Info) rather than the system's
 * original single-column stacked design. The company logo is fetched and
 * placed in the header; the verification/security block (payslip ID, hash,
 * QR code) — a genuinely important feature, not decorative — is kept, just
 * relocated into the "Additional Info" panel where the reference layout
 * has free space for it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayslipPdfService {

    private final CompanyRepository companyRepository;
    private final PayrollRepository payrollRepository;

    @Value("${app.verification-base-url}")
    private String verificationBaseUrl;

    private static final NumberFormat AMOUNT_FMT = NumberFormat.getNumberInstance(new Locale("en", "ZA"));
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter GENERATED_FMT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

    static {
        AMOUNT_FMT.setMinimumFractionDigits(2);
        AMOUNT_FMT.setMaximumFractionDigits(2);
    }

    private static final float PAGE_W = PDRectangle.A4.getWidth();
    private static final float PAGE_H = PDRectangle.A4.getHeight();
    private static final float MARGIN = 36;
    private static final float CONTENT_W = PAGE_W - 2 * MARGIN;
    private static final float GAP = 10;
    private static final float COL_W = (CONTENT_W - GAP) / 2;

    public byte[] generate(Payroll payroll) {
        Employee employee = payroll.getEmployee();
        Company company = companyRepository.findAll().stream().findFirst().orElse(null);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDFont bold = PDType1Font.HELVETICA_BOLD;
            PDFont regular = PDType1Font.HELVETICA;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setLineWidth(0.75f);

                float y = PAGE_H - MARGIN;

                PDImageXObject logo = company != null ? fetchLogo(doc, company.getLogoUrl()) : null;
                if (logo != null) {
                    float maxH = 42, maxW = 130;
                    float scale = Math.min(maxW / logo.getWidth(), maxH / logo.getHeight());
                    float w = logo.getWidth() * scale, h = logo.getHeight() * scale;
                    cs.drawImage(logo, PAGE_W - MARGIN - w, y - h, w, h);
                }

                text(cs, bold, 14, MARGIN, y - 12, "PAYSLIP");
                cs.setNonStrokingColor(90, 82, 80);
                text(cs, regular, 8.5f, MARGIN, y - 26, "Pay Period: " + formatPeriod(payroll.getPayPeriod()));
                cs.setNonStrokingColor(0, 0, 0);

                y -= 46;

                float headerTop = y;
                float headerH = 148;
                box(cs, MARGIN, headerTop - headerH, CONTENT_W, headerH);
                float midX = MARGIN + CONTENT_W * 0.62f;
                line(cs, midX, headerTop, midX, headerTop - headerH);

                float leftX = MARGIN + 10;
                float ly = headerTop - 16;
                ly = labelValue(cs, bold, regular, leftX, ly, "Co. Name", company != null ? company.getName() : "K and K Media (Pty) Ltd", 150);
                ly = labelValueWrapped(cs, bold, regular, leftX, ly, "Co. Address", company != null ? company.getAddress() : "", 150, midX - leftX - 155);
                ly -= 6;
                ly = labelValue(cs, bold, regular, leftX, ly, "Emp Code", employee.getEmployeeCode(), 150);
                ly = labelValue(cs, bold, regular, leftX, ly, "Emp Name", employee.getFullName(), 150);
                labelValueWrapped(cs, bold, regular, leftX, ly, "Emp Address", employeeAddress(employee), 150, midX - leftX - 155);

                float rightX = midX + 10;
                float ry = headerTop - 16;
                ry = labelValue(cs, bold, regular, rightX, ry, "Payment Dt", paymentDate(payroll.getPayPeriod()), 90);
                ry = labelValue(cs, bold, regular, rightX, ry, "Dt Engaged", employee.getStartDate() != null ? employee.getStartDate().format(DATE_FMT) : "-", 90);
                ry = labelValue(cs, bold, regular, rightX, ry, "Account No", nullToDash(employee.getBankAccountNumber()), 90);
                labelValue(cs, bold, regular, rightX, ry, "Branch Code", nullToDash(employee.getBankBranchCode()), 90);

                y = headerTop - headerH - GAP;

                float gridTop = y;
                float gridH = 250;
                float totalsRowH = 24;

                box(cs, MARGIN, gridTop - gridH, COL_W, gridH);
                box(cs, MARGIN + COL_W + GAP, gridTop - gridH, COL_W, gridH);

                List<String[]> earnings = new ArrayList<>();
                earnings.add(new String[]{"Basic Salary", money(payroll.getBasicSalary())});
                if (positive(payroll.getHousingAllowance())) earnings.add(new String[]{"Housing Allowance", money(payroll.getHousingAllowance())});
                if (positive(payroll.getTransportAllowance())) earnings.add(new String[]{"Transport Allowance", money(payroll.getTransportAllowance())});
                if (positive(payroll.getOvertime())) earnings.add(new String[]{"Overtime", money(payroll.getOvertime())});
                if (positive(payroll.getBonus())) earnings.add(new String[]{"Bonus", money(payroll.getBonus())});

                List<String[]> deductions = new ArrayList<>();
                deductions.add(new String[]{"PAYE (Tax)", money(payroll.getPaye())});
                deductions.add(new String[]{"U.I.F.", money(payroll.getUif())});

                drawGridColumn(cs, bold, regular, MARGIN, gridTop, COL_W, gridH, totalsRowH,
                        "EARNINGS", earnings, "Total Earnings", money(payroll.getGrossPay()));
                drawGridColumn(cs, bold, regular, MARGIN + COL_W + GAP, gridTop, COL_W, gridH, totalsRowH,
                        "DEDUCTIONS", deductions, "Total Deductions", money(payroll.getTotalDeductions()));

                y = gridTop - gridH - 16;

                float nettBoxW = 150, nettBoxH = 26;
                float nettBoxX = MARGIN + CONTENT_W - nettBoxW;
                text(cs, bold, 12, nettBoxX - 10 - textWidth(bold, 12, "NETT PAY"), y - 18, "NETT PAY");
                box(cs, nettBoxX, y - nettBoxH, nettBoxW, nettBoxH);
                String nettStr = money(payroll.getNetPay());
                text(cs, bold, 13, nettBoxX + nettBoxW - 10 - textWidth(bold, 13, nettStr), y - nettBoxH + 8, nettStr);

                y -= nettBoxH + 16;

                float bottomTop = y;
                float bottomH = 200;
                box(cs, MARGIN, bottomTop - bottomH, COL_W, bottomH);
                box(cs, MARGIN + COL_W + GAP, bottomTop - bottomH, COL_W, bottomH);

                drawYearToDate(cs, bold, regular, MARGIN, bottomTop, COL_W, employee, payroll);
                drawAdditionalInfo(doc, cs, bold, regular, MARGIN + COL_W + GAP, bottomTop, COL_W, bottomH, payroll);

                cs.setNonStrokingColor(140, 130, 128);
                text(cs, regular, 7.5f, MARGIN, MARGIN - 10, "This is a computer-generated payslip. Figures reflect the payroll system's records at the time of generation.");
                String brand = company != null ? company.getName() : "K and K Media (Pty) Ltd";
                text(cs, bold, 8, PAGE_W - MARGIN - textWidth(bold, 8, brand), MARGIN - 10, brand);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate payslip PDF", e);
        }
    }

    private void drawGridColumn(PDPageContentStream cs, PDFont bold, PDFont regular, float x, float top, float w, float h,
                                 float totalsRowH, String title, List<String[]> rows, String totalLabel, String totalValue) throws IOException {
        float titleY = top - 16;
        text(cs, bold, 11, x + (w - textWidth(bold, 11, title)) / 2, titleY, title);
        line(cs, x, titleY - 6, x + w, titleY - 6);

        float colY = titleY - 20;
        cs.setNonStrokingColor(90, 82, 80);
        text(cs, bold, 8.5f, x + 8, colY, "Description");
        String amountHeader = "Amount";
        text(cs, bold, 8.5f, x + w - 8 - textWidth(bold, 8.5f, amountHeader), colY, amountHeader);
        cs.setNonStrokingColor(0, 0, 0);
        line(cs, x, colY - 5, x + w, colY - 5);

        float rowY = colY - 18;
        for (String[] row : rows) {
            text(cs, regular, 9.5f, x + 8, rowY, row[0]);
            text(cs, regular, 9.5f, x + w - 8 - textWidth(regular, 9.5f, row[1]), rowY, row[1]);
            rowY -= 16;
        }

        float totalsY = top - h + totalsRowH / 2 - 3;
        line(cs, x, top - h + totalsRowH, x + w, top - h + totalsRowH);
        text(cs, bold, 10, x + 8, totalsY, totalLabel);
        text(cs, bold, 10, x + w - 8 - textWidth(bold, 10, totalValue), totalsY, totalValue);
    }

    private void drawYearToDate(PDPageContentStream cs, PDFont bold, PDFont regular, float x, float top, float w,
                                 Employee employee, Payroll payroll) throws IOException {
        float titleY = top - 16;
        text(cs, bold, 11, x + (w - textWidth(bold, 11, "YEAR TO DATE TOTALS")) / 2, titleY, "YEAR TO DATE TOTALS");
        line(cs, x, titleY - 6, x + w, titleY - 6);

        BigDecimal[] ytd = yearToDateTotals(employee.getId(), payroll.getPayPeriod());
        float ry = titleY - 22;
        ry = ytdRow(cs, regular, x, ry, w, "Gross Earnings", money(ytd[0]));
        ry = ytdRow(cs, regular, x, ry, w, "Deductions", money(ytd[1]));
        ytdRow(cs, regular, x, ry, w, "Nett Pay", money(ytd[2]));

        ry -= 24;
        text(cs, bold, 10.5f, x + 8, ry, "CURRENT PERIOD");
        ry -= 16;
        text(cs, regular, 9.5f, x + 8, ry, "Employer UIF Contribution");
        String employerUif = money(payroll.getUif());
        text(cs, regular, 9.5f, x + w - 8 - textWidth(regular, 9.5f, employerUif), ry, employerUif);
    }

    private float ytdRow(PDPageContentStream cs, PDFont regular, float x, float y, float w, String label, String value) throws IOException {
        text(cs, regular, 9.5f, x + 8, y, label);
        text(cs, regular, 9.5f, x + w - 8 - textWidth(regular, 9.5f, value), y, value);
        return y - 16;
    }

    private BigDecimal[] yearToDateTotals(Long employeeId, String currentPeriod) {
        try {
            YearMonth current = YearMonth.parse(currentPeriod);
            YearMonth taxYearStart = current.getMonthValue() >= 3
                    ? YearMonth.of(current.getYear(), 3)
                    : YearMonth.of(current.getYear() - 1, 3);

            BigDecimal gross = BigDecimal.ZERO, deductions = BigDecimal.ZERO, net = BigDecimal.ZERO;
            for (Payroll p : payrollRepository.findByEmployeeIdOrderByPayPeriodDesc(employeeId)) {
                YearMonth period = YearMonth.parse(p.getPayPeriod());
                if (!period.isBefore(taxYearStart) && !period.isAfter(current)) {
                    gross = gross.add(nz(p.getGrossPay()));
                    deductions = deductions.add(nz(p.getTotalDeductions()));
                    net = net.add(nz(p.getNetPay()));
                }
            }
            return new BigDecimal[]{gross, deductions, net};
        } catch (Exception e) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        }
    }

    private void drawAdditionalInfo(PDDocument doc, PDPageContentStream cs, PDFont bold, PDFont regular, float x, float top, float w, float h, Payroll payroll) throws IOException {
        float titleY = top - 16;
        text(cs, bold, 11, x + (w - textWidth(bold, 11, "ADDITIONAL INFO")) / 2, titleY, "ADDITIONAL INFO");
        line(cs, x, titleY - 6, x + w, titleY - 6);

        if (payroll.getPayslipId() == null) {
            cs.setNonStrokingColor(140, 130, 128);
            text(cs, regular, 8.5f, x + 8, titleY - 22, "Verification details are added once this payslip is finalized.");
            cs.setNonStrokingColor(0, 0, 0);
            return;
        }

        float iy = titleY - 20;
        cs.setNonStrokingColor(90, 82, 80);
        text(cs, bold, 8, x + 8, iy, "Payslip ID: " + payroll.getPayslipId());
        iy -= 12;
        text(cs, regular, 8, x + 8, iy, "Verification Code:");
        iy -= 11;
        text(cs, regular, 8, x + 8, iy, payroll.getVerificationCode());
        iy -= 12;
        if (payroll.getDocumentGeneratedAt() != null) {
            text(cs, regular, 7.5f, x + 8, iy, "Generated: " + payroll.getDocumentGeneratedAt().format(GENERATED_FMT));
            iy -= 11;
        }
        if (payroll.getDocumentHash() != null) {
            String shortHash = payroll.getDocumentHash().substring(0, Math.min(20, payroll.getDocumentHash().length()));
            text(cs, regular, 7, x + 8, iy, "SHA-256: " + shortHash + "...");
            iy -= 13;
        }
        text(cs, regular, 6.5f, x + 8, iy, "No physical signature required. Scan to verify authenticity:");

        try {
            String verifyUrl = verificationBaseUrl + "/" + payroll.getVerificationCode();
            BufferedImage qr = buildQrCode(verifyUrl, 300);
            PDImageXObject qrImage = LosslessFactory.createFromImage(doc, qr);
            float qrSize = 62;
            cs.drawImage(qrImage, x + w - qrSize - 10, top - h + 10, qrSize, qrSize);
        } catch (WriterException e) {
            // Non-fatal — the payslip is still valid and verifiable via the printed code.
        }
        cs.setNonStrokingColor(0, 0, 0);
    }

    private PDImageXObject fetchLogo(PDDocument doc, String logoUrl) {
        if (logoUrl == null || logoUrl.isBlank()) return null;
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(logoUrl)).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return null;
            byte[] bytes = response.body();
            try {
                return PDImageXObject.createFromByteArray(doc, bytes, "logo");
            } catch (IOException e) {
                BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                return img != null ? LosslessFactory.createFromImage(doc, img) : null;
            }
        } catch (Exception e) {
            log.warn("Could not fetch company logo for payslip ({}): {}", logoUrl, e.getMessage());
            return null;
        }
    }

    private void box(PDPageContentStream cs, float x, float y, float w, float h) throws IOException {
        cs.addRect(x, y, w, h);
        cs.stroke();
    }

    private void line(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws IOException {
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private float labelValue(PDPageContentStream cs, PDFont bold, PDFont regular, float x, float y, String label, String value, float valueOffset) throws IOException {
        text(cs, bold, 8.5f, x, y, label);
        text(cs, regular, 8.5f, x + valueOffset, y, value == null ? "-" : value);
        return y - 13;
    }

    private float labelValueWrapped(PDPageContentStream cs, PDFont bold, PDFont regular, float x, float y, String label, String value, float valueOffset, float maxWidth) throws IOException {
        text(cs, bold, 8.5f, x, y, label);
        List<String> lines = wrap(regular, 8.5f, value == null ? "" : value, maxWidth);
        float ly = y;
        for (String line : lines) {
            text(cs, regular, 8.5f, x + valueOffset, ly, line);
            ly -= 11;
        }
        return ly - 2;
    }

    private List<String> wrap(PDFont font, float size, String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            lines.add("-");
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (textWidth(font, size, candidate) > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    private String employeeAddress(Employee e) {
        List<String> parts = new ArrayList<>();
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

    private String formatPeriod(String payPeriod) {
        try {
            YearMonth ym = YearMonth.parse(payPeriod);
            return ym.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        } catch (Exception e) {
            return payPeriod;
        }
    }

    private BufferedImage buildQrCode(String content, int size) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    private void text(PDPageContentStream cs, PDFont font, float size, float x, float y, String value) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(value == null ? "" : sanitize(value));
        cs.endText();
    }

    private float textWidth(PDFont font, float size, String text) {
        try {
            return font.getStringWidth(sanitize(text)) / 1000 * size;
        } catch (IOException e) {
            return 0;
        }
    }

    private String sanitize(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c <= 0xFF ? c : '?');
        }
        return sb.toString();
    }

    private String money(BigDecimal value) {
        return "R " + AMOUNT_FMT.format(nz(value));
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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
}
