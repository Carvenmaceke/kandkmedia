package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Company;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PayslipPdfService {

    private final CompanyRepository companyRepository;

    private static final NumberFormat RAND = NumberFormat.getNumberInstance(new Locale("en", "ZA"));

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
            float y = page.getMediaBox().getHeight() - 60;

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
                text(cs, regular, 8.5f, marginX, page.getMediaBox().getHeight() - 70,
                        company != null && company.getAddress() != null ? company.getAddress() : "");

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

                y -= 50;
                cs.setNonStrokingColor(120, 110, 108);
                text(cs, regular, 7.5f, marginX, y, "Figures are illustrative dummy data, not real tax calculations.");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate payslip PDF", e);
        }
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
