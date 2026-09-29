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
import lombok.extern.slf4j.Slf4j;
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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws the company payslip with PDFBox in the K &amp; K Media brand colours
 * (charcoal and brand red): a charcoal header with the company logo, a
 * details panel, a single EARNINGS box (staff are paid a fixed monthly
 * salary, so there are no hours, overtime or deductions), a red NETT PAY
 * bar, and YEAR TO DATE / ADDITIONAL INFO boxes. Uses the PDF standard
 * Helvetica fonts, so output is identical on every server.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayslipPdfService {

    // K and K Media brand colours (match the web app's --kk-ink / --kk-brand).
    private static final Color INK = new Color(0x11, 0x18, 0x27);
    private static final Color BRAND = new Color(0xE1, 0x1D, 0x2E);
    private static final Color TINT = new Color(0xFD, 0xEC, 0xEE);
    private static final Color PANEL = new Color(0xF5, 0xF6, 0xF8);
    private static final Color RULE = new Color(0xD1, 0xD5, 0xDB);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
    private static final Color ON_INK_MUTED = new Color(0xC9, 0xCD, 0xD6);

    private static final PDFont REGULAR = PDType1Font.HELVETICA;
    private static final PDFont BOLD = PDType1Font.HELVETICA_BOLD;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter GENERATED_FMT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");
    private static final String DEFAULT_COMPANY_NAME = "K & K MEDIA (PTY) LTD";
    private static final List<String> COMPANY_ADDRESS = List.of(
            "CONSTANTIA SQUARE OFFICE", "16TH ROAD", "RANDJESFONTEIN, MIDRAND", "1685");

    // Page frame: 36pt margins on a 612 x 792 page; bottom boxes are two 265pt columns with a 10pt gutter.
    private static final float LEFT_X = 36, RIGHT_X = 311, COL_W = 265, FULL_W = 540, RIGHT_EDGE = 576;
    private static final float BOTTOM_BOXES_Y = 214, BOTTOM_BOXES_H = 150;

    private final PayrollRepository payrollRepository;
    private final CompanyRepository companyRepository;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private volatile byte[] cachedLogo;
    private volatile String cachedLogoUrl;
    private volatile long logoRetryAfter;

    @Value("${app.verification-base-url}")
    private String verificationBaseUrl;

    /** Used when the company record has no logo URL; blank disables the logo (a text wordmark is drawn instead). */
    @Value("${app.payslip-logo-url:https://www.kandkmedia.co.za/wp-content/uploads/2024/05/cropped-cropped-K-and-K-Media-logo-New-1.png}")
    private String defaultLogoUrl;

    public byte[] generate(Payroll payroll) {
        Employee employee = payroll.getEmployee();
        BigDecimal[] ytd = yearToDateTotals(employee.getId(), payroll.getPayPeriod());
        Company company = companyRepository.findAll().stream().findFirst().orElse(null);
        String companyName = company != null && notBlank(company.getName()) ? upper(company.getName()) : DEFAULT_COMPANY_NAME;
        String logoUrl = company != null && notBlank(company.getLogoUrl()) ? company.getLogoUrl().trim() : defaultLogoUrl;

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle("Payslip - K & K Media (Pty) Ltd");
            info.setAuthor("K & K Media (Pty) Ltd");

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setLineWidth(0.8f);
                drawHeader(doc, cs, payroll, companyName, logoUrl);
                drawDetailsPanel(cs, payroll, employee, companyName);
                drawEarnings(cs, payroll);
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

    private void drawHeader(PDDocument doc, PDPageContentStream cs, Payroll payroll, String companyName, String logoUrl) throws IOException {
        fillRect(cs, INK, LEFT_X, 712, FULL_W, 48);
        fillRect(cs, BRAND, LEFT_X, 708, FULL_W, 4);

        PDImageXObject logo = logo(doc, logoUrl);
        if (logo != null) {
            float h = 28, w = Math.min(170, logo.getWidth() * (h / logo.getHeight()));
            h = w * logo.getHeight() / logo.getWidth();
            cs.drawImage(logo, 50, 736 - h / 2, w, h);
        } else {
            // Text wordmark in the brand style when the logo can't be loaded.
            text(cs, BOLD, 17, 50, 730, "K&K", BRAND);
            text(cs, BOLD, 17, 50 + width(BOLD, 17, "K&K "), 730, "MEDIA", Color.WHITE);
        }
        textRight(cs, BOLD, 16, RIGHT_EDGE - 14, 738, "PAYSLIP", Color.WHITE);
        textRight(cs, REGULAR, 9, RIGHT_EDGE - 14, 724, payPeriodLabel(payroll.getPayPeriod()) + "  |  " + companyName, ON_INK_MUTED);
    }

    private void drawDetailsPanel(PDPageContentStream cs, Payroll payroll, Employee employee, String companyName) throws IOException {
        cs.setNonStrokingColor(PANEL);
        cs.setStrokingColor(RULE);
        cs.addRect(LEFT_X, 598, FULL_W, 96);
        cs.fillAndStroke();
        fillRect(cs, BRAND, LEFT_X, 598, 3, 96);

        // Column 1 — employee
        label(cs, 46, 678, "Company");
        value(cs, 104, 678, fit(companyName, 122));
        label(cs, 46, 665, "Emp Code");
        value(cs, 104, 665, dash(employee.getEmployeeCode()));
        label(cs, 46, 652, "Emp Name");
        value(cs, 104, 652, fit(upper(employee.getFullName()), 122));
        label(cs, 46, 639, "Emp Address");
        List<String> address = employeeAddressLines(employee);
        for (int i = 0; i < address.size(); i++) {
            value(cs, 104, 639 - 11 * i, address.get(i));
        }

        // Column 2 — company address
        label(cs, 238.4f, 678, "Co. Address");
        for (int i = 0; i < COMPANY_ADDRESS.size(); i++) {
            value(cs, 294.4f, 678 - 11 * i, COMPANY_ADDRESS.get(i));
        }

        // Column 3 — payment
        label(cs, 432.8f, 678, "Payment Date");
        value(cs, 496.8f, 678, paymentDate(payroll.getPayPeriod()));
        label(cs, 432.8f, 665, "Date Engaged");
        value(cs, 496.8f, 665, employee.getStartDate() != null ? employee.getStartDate().format(DATE_FMT) : "-");
        // Banking details appear as soon as HR (or the employee at sign-up) has filled them in.
        label(cs, 432.8f, 652, "Bank");
        value(cs, 496.8f, 652, fit(orNotProvided(employee.getBankName()), 76));
        label(cs, 432.8f, 639, "Account No");
        value(cs, 496.8f, 639, fit(orNotProvided(employee.getBankAccountNumber()), 76));
        label(cs, 432.8f, 626, "Branch Code");
        value(cs, 496.8f, 626, fit(orNotProvided(employee.getBankBranchCode()), 76));

        cs.setStrokingColor(RULE);
        line(cs, 230.4f, 606, 230.4f, 686);
        line(cs, 424.8f, 606, 424.8f, 686);
    }

    /** Fixed monthly salary only — no hours, overtime or deductions. */
    private void drawEarnings(PDPageContentStream cs, Payroll payroll) throws IOException {
        float y0 = 470, h = 108;
        boxWithTitle(cs, LEFT_X, y0, FULL_W, h, "EARNINGS");
        text(cs, BOLD, 8.5f, 46, y0 + h - 32, "Description", Color.BLACK);
        textRight(cs, BOLD, 8.5f, RIGHT_EDGE - 10, y0 + h - 32, "Amount (R)", Color.BLACK);
        cs.setStrokingColor(RULE);
        line(cs, 44, y0 + h - 37, RIGHT_EDGE - 8, y0 + h - 37);

        text(cs, REGULAR, 9, 46, y0 + h - 52, "Monthly Salary", Color.BLACK);
        textRight(cs, REGULAR, 9, RIGHT_EDGE - 10, y0 + h - 52, money(payroll.getBasicSalary()), Color.BLACK);

        fillRect(cs, TINT, LEFT_X + 0.4f, y0 + 0.4f, FULL_W - 0.8f, 22);
        cs.setStrokingColor(RULE);
        line(cs, LEFT_X, y0 + 22.4f, LEFT_X + FULL_W, y0 + 22.4f);
        text(cs, BOLD, 9, 46, y0 + 8, "Total Earnings", Color.BLACK);
        textRight(cs, BOLD, 9, RIGHT_EDGE - 10, y0 + 8, money(payroll.getGrossPay()), Color.BLACK);

        text(cs, BOLD, 8.5f, 46, 452, "Deductions:", Color.BLACK);
        text(cs, REGULAR, 8.5f, 46 + width(BOLD, 8.5f, "Deductions: "), 452, "None", MUTED);
    }

    private void drawNettPay(PDPageContentStream cs, Payroll payroll) throws IOException {
        fillRect(cs, BRAND, LEFT_X, 398, FULL_W, 38);
        text(cs, BOLD, 12, 50, 413, "NETT PAY", Color.WHITE);
        textRight(cs, BOLD, 17, RIGHT_EDGE - 14, 411, "R " + money(payroll.getNetPay()), Color.WHITE);
    }

    private void drawYearToDate(PDPageContentStream cs, Payroll payroll, BigDecimal[] ytd) throws IOException {
        float y0 = BOTTOM_BOXES_Y, top = y0 + BOTTOM_BOXES_H;
        boxWithTitle(cs, LEFT_X, y0, COL_W, BOTTOM_BOXES_H, "YEAR TO DATE TOTALS");
        text(cs, REGULAR, 8.5f, 44, top - 36, "Total Earnings", Color.BLACK);
        textRight(cs, REGULAR, 8.5f, 293, top - 36, money(ytd[0]), Color.BLACK);
        text(cs, REGULAR, 8.5f, 44, top - 50, "Total Nett Pay", Color.BLACK);
        textRight(cs, REGULAR, 8.5f, 293, top - 50, money(ytd[1]), Color.BLACK);

        cs.setStrokingColor(RULE);
        line(cs, LEFT_X, top - 70, LEFT_X + COL_W, top - 70);
        fillRect(cs, PANEL, LEFT_X + 0.4f, top - 88, COL_W - 0.8f, 18);
        textCentered(cs, BOLD, 9, LEFT_X + COL_W / 2, top - 83, "CURRENT PERIOD", Color.BLACK);
        cs.setStrokingColor(RULE);
        line(cs, LEFT_X, top - 88, LEFT_X + COL_W, top - 88);

        text(cs, REGULAR, 8.5f, 44, top - 104, "Monthly Salary", Color.BLACK);
        textRight(cs, REGULAR, 8.5f, 293, top - 104, money(payroll.getBasicSalary()), Color.BLACK);
        text(cs, REGULAR, 8.5f, 44, top - 118, "Nett Pay", Color.BLACK);
        textRight(cs, BOLD, 8.5f, 293, top - 118, money(payroll.getNetPay()), BRAND);
    }

    private void drawAdditionalInfo(PDDocument doc, PDPageContentStream cs, Payroll payroll, Employee employee) throws IOException {
        float y0 = BOTTOM_BOXES_Y, top = y0 + BOTTOM_BOXES_H;
        boxWithTitle(cs, RIGHT_X, y0, COL_W, BOTTOM_BOXES_H, "ADDITIONAL INFO");

        boolean sealed = payroll.getPayslipId() != null && payroll.getVerificationCode() != null;
        float textWidth = sealed ? 150 : 245;
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Pay Period", payPeriodLabel(payroll.getPayPeriod())});
        rows.add(new String[]{"Pay Basis", "Monthly salary"});
        rows.add(new String[]{"Job Title", dash(employee.getPosition())});
        rows.add(new String[]{"Department", employee.getDepartment() != null ? dash(employee.getDepartment().getName()) : "-"});
        if (sealed) {
            rows.add(new String[]{"Payslip ID", payroll.getPayslipId()});
            rows.add(new String[]{"Verify Code", payroll.getVerificationCode()});
            if (payroll.getDocumentGeneratedAt() != null) {
                rows.add(new String[]{"Generated", payroll.getDocumentGeneratedAt().format(GENERATED_FMT)});
            }
        }

        float y = top - 34;
        for (String[] row : rows) {
            text(cs, BOLD, 8, 319, y, row[0], Color.BLACK);
            text(cs, REGULAR, 8, 372, y, fit(row[1], textWidth - 53), Color.BLACK);
            y -= 12;
        }

        if (sealed) {
            String verifyUrl = verificationBaseUrl + "/" + payroll.getVerificationCode();
            PDImageXObject qr = qrImage(doc, verifyUrl);
            if (qr != null) {
                cs.drawImage(qr, 484, y0 + 16, 84, 84);
                textCentered(cs, REGULAR, 6.5f, 526, y0 + 8, "Scan to verify", MUTED);
            }
        }
    }

    private void drawFooter(PDPageContentStream cs) throws IOException {
        fillRect(cs, BRAND, LEFT_X, 48, FULL_W, 1.5f);
        text(cs, REGULAR, 7.5f, 36, 34, "This payslip is computer generated. Amounts in South African Rand (ZAR).", MUTED);
        textRight(cs, REGULAR, 7.5f, RIGHT_EDGE, 34, "Page 1 of 1", MUTED);
    }

    /* ------------------------------------------------------------------ */
    /* Drawing helpers                                                    */
    /* ------------------------------------------------------------------ */

    /** Outlined box with a charcoal title bar (and a thin red underline) across its top. */
    private void boxWithTitle(PDPageContentStream cs, float x, float y, float w, float height, String title) throws IOException {
        cs.setStrokingColor(RULE);
        cs.addRect(x, y, w, height);
        cs.stroke();
        fillRect(cs, INK, x, y + height - 20, w, 20);
        fillRect(cs, BRAND, x, y + height - 22, w, 2);
        text(cs, BOLD, 9.5f, x + 10, y + height - 14, title, Color.WHITE);
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

    /** The company logo, downloaded once and cached; null (wordmark fallback) if it can't be fetched. */
    private PDImageXObject logo(PDDocument doc, String url) {
        if (!notBlank(url)) return null;
        try {
            byte[] bytes = cachedLogo;
            if (bytes == null || !url.equals(cachedLogoUrl)) {
                if (url.equals(cachedLogoUrl) && System.currentTimeMillis() < logoRetryAfter) return null;
                cachedLogoUrl = url;
                cachedLogo = null;
                HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() != 200) {
                    logoRetryAfter = System.currentTimeMillis() + 10 * 60_000;
                    return null;
                }
                bytes = res.body();
                cachedLogo = bytes;
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            return img == null ? null : LosslessFactory.createFromImage(doc, img);
        } catch (Exception e) {
            log.warn("Payslip logo couldn't be loaded from {}: {}", url, e.getMessage());
            logoRetryAfter = System.currentTimeMillis() + 10 * 60_000;
            return null;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Data                                                               */
    /* ------------------------------------------------------------------ */

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

    /** {earnings, nett pay} summed over the tax year (March to February) up to this period. */
    private BigDecimal[] yearToDateTotals(Long employeeId, String currentPeriod) {
        try {
            YearMonth current = YearMonth.parse(currentPeriod);
            YearMonth taxYearStart = current.getMonthValue() >= 3
                    ? YearMonth.of(current.getYear(), 3)
                    : YearMonth.of(current.getYear() - 1, 3);

            BigDecimal gross = BigDecimal.ZERO, net = BigDecimal.ZERO;
            for (Payroll p : payrollRepository.findByEmployeeIdOrderByPayPeriodDesc(employeeId)) {
                YearMonth period = YearMonth.parse(p.getPayPeriod());
                if (!period.isBefore(taxYearStart) && !period.isAfter(current)) {
                    gross = gross.add(nz(p.getGrossPay()));
                    net = net.add(nz(p.getNetPay()));
                }
            }
            return new BigDecimal[]{gross, net};
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

    private static List<String> nonBlank(String... values) {
        List<String> out = new ArrayList<>();
        for (String v : values) if (notBlank(v)) out.add(v.trim());
        return out;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String orNotProvided(String s) {
        return notBlank(s) ? s.trim() : "Not provided";
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
