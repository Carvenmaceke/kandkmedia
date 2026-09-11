package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Company;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.repository.CompanyRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PayslipPdfService {

    private final CompanyRepository companyRepository;

    @Value("${app.verification-base-url}")
    private String verificationBaseUrl;

    private static final NumberFormat RAND = NumberFormat.getNumberInstance(new Locale("en", "ZA"));
    private static final DateTimeFormatter GENERATED_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm");

    public byte[] generate(Payroll payroll) {
        Employee employee = payroll.getEmployee();
        Company company = companyRepository.findAll().stream().findFirst().orElse(null);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDFont bold = PDType1Font.HELVETICA_BOLD;
            PDFont regular = PDType1Font.HELVETICA;

            float pageWidth = page.getMediaBox().getWidth();
            float marginX = 50;
            float y;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Header band
                cs.setNonStrokingColor(23, 17, 15);
                cs.addRect(0, page.getMediaBox().getHeight() - 100, pageWidth, 100);
                cs.fill();

                cs.setNonStrokingColor(216, 31, 44);
                cs.addRect(marginX, page.getMediaBox().getHeight() - 40, 40, 3);
                cs.fill();

                cs.setNonStrokingColor(255, 255, 255);
                text(cs, bold, 15, marginX, page.getMediaBox().getHeight() - 55,
                        company != null ? company.getName() : "K and K Media (Pty) Ltd");
                String employerLine1 = company != null && company.getAddress() != null ? company.getAddress() : "";
                text(cs, regular, 8, marginX, page.getMediaBox().getHeight() - 70, employerLine1);
                if (company != null && company.getRegistrationNumber() != null && !company.getRegistrationNumber().isBlank()) {
                    text(cs, regular, 8, marginX, page.getMediaBox().getHeight() - 82, "Reg No: " + company.getRegistrationNumber());
                }

                text(cs, bold, 11, pageWidth - marginX - textWidth(bold, 11, employee.getFullName()), page.getMediaBox().getHeight() - 55, employee.getFullName());
                String idLine = employee.getEmployeeCode() + "  |  " + nullToEmpty(employee.getPosition());
                text(cs, regular, 8.5f, pageWidth - marginX - textWidth(regular, 8.5f, idLine), page.getMediaBox().getHeight() - 70, idLine);
                String periodLine = "Pay Period: " + payroll.getPayPeriod();
                text(cs, regular, 8.5f, pageWidth - marginX - textWidth(regular, 8.5f, periodLine), page.getMediaBox().getHeight() - 84, periodLine);

                y = page.getMediaBox().getHeight() - 130;

                y = sectionHeader(cs, bold, marginX, y, "EARNINGS");
                y = row(cs, regular, bold, marginX, pageWidth - marginX, y, "Basic Salary", payroll.getBasicSalary(), false);
                if (positive(payroll.getHousingAllowance())) y = row(cs, regular, bold, marginX, pageWidth - marginX, y, "Housing Allowance", payroll.getHousingAllowance(), false);
                if (positive(payroll.getTransportAllowance())) y = row(cs, regular, bold, marginX, pageWidth - marginX, y, "Transport Allowance", payroll.getTransportAllowance(), false);
                if (positive(payroll.getOvertime())) y = row(cs, regular, bold, marginX, pageWidth - marginX, y, "Overtime", payroll.getOvertime(), false);
                if (positive(payroll.getBonus())) y = row(cs, regular, bold, marginX, pageWidth - marginX, y, "Bonus", payroll.getBonus(), false);
                y = row(cs, regular, bold, marginX, pageWidth - marginX, y, "Gross Earnings", payroll.getGrossPay(), true);

                y -= 12;
                y = sectionHeader(cs, bold, marginX, y, "DEDUCTIONS");
                y = row(cs, regular, bold, marginX, pageWidth - marginX, y, "PAYE", payroll.getPaye().negate(), false);
                y = row(cs, regular, bold, marginX, pageWidth - marginX, y, "UIF", payroll.getUif().negate(), false);
                y = row(cs, regular, bold, marginX, pageWidth - marginX, y, "Total Deductions", payroll.getTotalDeductions().negate(), true);

                y -= 16;
                cs.setNonStrokingColor(251, 234, 234);
                cs.addRect(marginX, y - 20, pageWidth - marginX * 2, 34);
                cs.fill();
                cs.setNonStrokingColor(23, 17, 15);
                text(cs, bold, 12, marginX + 12, y - 8, "Net Pay");
                String netStr = money(payroll.getNetPay());
                text(cs, bold, 12, pageWidth - marginX - 12 - textWidth(bold, 12, netStr), y - 8, netStr);

                y -= 40;
                cs.setNonStrokingColor(120, 110, 108);
                text(cs, regular, 7.5f, marginX, y, "Figures are illustrative dummy data, not real tax calculations.");

                // --- Security / verification block — only present once the
                // payroll row has been finalized and these values assigned
                // (see PayrollService.advanceStage). A still-draft payslip
                // preview simply won't have this section yet.
                if (payroll.getPayslipId() != null) {
                    y -= 30;
                    cs.setStrokingColor(231, 225, 224);
                    cs.setLineWidth(0.75f);
                    cs.moveTo(marginX, y);
                    cs.lineTo(pageWidth - marginX, y);
                    cs.stroke();
                    y -= 20;

                    float textBottom = y;
                    cs.setNonStrokingColor(90, 82, 80);
                    text(cs, bold, 8.5f, marginX, y, "Payslip ID: " + payroll.getPayslipId());
                    y -= 13;
                    text(cs, regular, 8.5f, marginX, y, "Verification Code: " + payroll.getVerificationCode());
                    y -= 13;
                    if (payroll.getDocumentGeneratedAt() != null) {
                        text(cs, regular, 8.5f, marginX, y, "Generated: " + payroll.getDocumentGeneratedAt().format(GENERATED_FMT));
                        y -= 13;
                    }
                    if (payroll.getDocumentHash() != null) {
                        String shortHash = payroll.getDocumentHash().substring(0, Math.min(16, payroll.getDocumentHash().length()));
                        text(cs, regular, 7.5f, marginX, y, "SHA-256: " + shortHash + "...");
                        y -= 16;
                    }

                    cs.setNonStrokingColor(140, 130, 128);
                    text(cs, regular, 7, marginX, y, "This document was electronically generated by the K and K Media Payroll System.");
                    y -= 10;
                    text(cs, regular, 7, marginX, y, "No physical signature is required. Verify authenticity by scanning the QR code or visiting the verification code above online.");

                    // QR code, bottom-right, encoding the verification URL
                    try {
                        String verifyUrl = verificationBaseUrl + "/" + payroll.getVerificationCode();
                        BufferedImage qr = buildQrCode(verifyUrl, 300);
                        PDImageXObject qrImage = LosslessFactory.createFromImage(doc, qr);
                        float qrSize = 70;
                        float qrX = pageWidth - marginX - qrSize;
                        float qrY = textBottom - 60;
                        cs.drawImage(qrImage, qrX, qrY, qrSize, qrSize);
                        cs.setNonStrokingColor(140, 130, 128);
                        String caption = "Scan to verify";
                        text(cs, regular, 6.5f, qrX + (qrSize - textWidth(regular, 6.5f, caption)) / 2, qrY - 10, caption);
                    } catch (WriterException e) {
                        // Non-fatal — the payslip is still valid and verifiable via the
                        // printed code even if the QR image itself couldn't be drawn.
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate payslip PDF", e);
        }
    }

    private BufferedImage buildQrCode(String content, int size) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    private float sectionHeader(PDPageContentStream cs, PDFont bold, float x, float y, String label) throws IOException {
        cs.setNonStrokingColor(216, 31, 44);
        text(cs, bold, 10, x, y, label);
        cs.setNonStrokingColor(26, 20, 20);
        return y - 16;
    }

    private float row(PDPageContentStream cs, PDFont regular, PDFont bold, float xLeft, float xRight, float y,
                       String label, BigDecimal value, boolean isBold) throws IOException {
        PDFont font = isBold ? bold : regular;
        cs.setNonStrokingColor(26, 20, 20);
        text(cs, font, 10.5f, xLeft, y, label);
        String valueStr = money(value);
        text(cs, font, 10.5f, xRight - textWidth(font, 10.5f, valueStr), y, valueStr);

        cs.setStrokingColor(231, 225, 224);
        cs.setLineWidth(0.5f);
        cs.moveTo(xLeft, y - 5);
        cs.lineTo(xRight, y - 5);
        cs.stroke();

        return y - 19;
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

    /** WinAnsi/Standard14 fonts only support Latin-1 — strip anything outside it so PDFBox doesn't throw. */
    private String sanitize(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c <= 0xFF ? c : '?');
        }
        return sb.toString();
    }

    private String money(BigDecimal value) {
        return "R" + RAND.format(value.setScale(0, java.math.RoundingMode.HALF_UP));
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
