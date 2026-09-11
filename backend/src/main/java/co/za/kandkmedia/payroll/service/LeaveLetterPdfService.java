package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Company;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.LeaveRequest;
import co.za.kandkmedia.payroll.domain.LeaveStatus;
import co.za.kandkmedia.payroll.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveLetterPdfService {

    private final CompanyRepository companyRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    public byte[] generate(LeaveRequest request) {
        Employee employee = request.getEmployee();
        Company company = companyRepository.findAll().stream().findFirst().orElse(null);
        boolean approved = request.getStatus() == LeaveStatus.APPROVED;

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDFont bold = PDType1Font.HELVETICA_BOLD;
            PDFont regular = PDType1Font.HELVETICA;
            float pageWidth = page.getMediaBox().getWidth();
            float marginX = 42;
            float y;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(23, 17, 15);
                cs.addRect(0, page.getMediaBox().getHeight() - 92, pageWidth, 92);
                cs.fill();
                cs.setNonStrokingColor(216, 31, 44);
                cs.addRect(marginX, page.getMediaBox().getHeight() - 26, 40, 3);
                cs.fill();

                cs.setNonStrokingColor(255, 255, 255);
                text(cs, bold, 15, marginX, page.getMediaBox().getHeight() - 50,
                        company != null ? company.getName() : "K and K Media (Pty) Ltd");
                text(cs, regular, 8.5f, marginX, page.getMediaBox().getHeight() - 64,
                        company != null && company.getAddress() != null ? company.getAddress() : "");

                String title = "LEAVE REQUEST " + (approved ? "APPROVAL" : "DECLINE") + " LETTER";
                text(cs, bold, 11, pageWidth - marginX - textWidth(bold, 11, title), page.getMediaBox().getHeight() - 50, title);

                y = page.getMediaBox().getHeight() - 140;
                cs.setNonStrokingColor(20, 20, 20);
                text(cs, regular, 10.5f, marginX, y, "Date: " + java.time.LocalDate.now().format(DATE_FMT));
                y -= 26;

                text(cs, bold, 10.5f, marginX, y, "Dear " + employee.getFirstName() + ",");
                y -= 22;

                String bodyText = approved
                        ? "This letter confirms that your " + request.getLeaveType().getName() + " request has been APPROVED."
                        : "This letter confirms that your " + request.getLeaveType().getName() + " request has been DECLINED.";
                List<String> wrapped = wrap(regular, 10.5f, bodyText, pageWidth - marginX * 2);
                for (String line : wrapped) { text(cs, regular, 10.5f, marginX, y, line); y -= 14; }
                y -= 6;

                y = detailRow(cs, bold, regular, marginX, y, "Employee:", employee.getFullName() + " (" + employee.getEmployeeCode() + ")");
                y = detailRow(cs, bold, regular, marginX, y, "Leave Type:", request.getLeaveType().getName());
                y = detailRow(cs, bold, regular, marginX, y, "Dates:", request.getStartDate() + " to " + request.getEndDate());
                y = detailRow(cs, bold, regular, marginX, y, "Days Requested:", String.valueOf(request.getDaysRequested()));
                y = detailRow(cs, bold, regular, marginX, y, "Applicant's Reason:", request.getReason() != null ? request.getReason() : "-");

                y -= 6;
                if (approved) {
                    cs.setNonStrokingColor(233, 245, 238);
                } else {
                    cs.setNonStrokingColor(251, 235, 233);
                }
                cs.addRect(marginX, y - 14, pageWidth - marginX * 2, 26);
                cs.fill();
                cs.setNonStrokingColor(approved ? 47 : 140, approved ? 122 : 42, approved ? 85 : 46);
                text(cs, bold, 11, marginX + 12, y + 4, "Status: " + (approved ? "Approved" : "Declined"));
                y -= 40;

                if (!approved && request.getDecisionReason() != null && !request.getDecisionReason().isBlank()) {
                    text(cs, bold, 9.5f, marginX, y, "Reason for decline:");
                    y -= 15;
                    List<String> reasonLines = wrap(regular, 10, request.getDecisionReason(), pageWidth - marginX * 2);
                    cs.setNonStrokingColor(20, 20, 20);
                    for (String line : reasonLines) { text(cs, regular, 10, marginX, y, line); y -= 14; }
                    y -= 10;
                }

                y -= 30;
                float sigBoxWidth = (pageWidth - marginX * 2 - 30) / 2;
                float sigImgHeight = 50;

                drawSignatureBlock(doc, cs, regular, bold, marginX, y, sigBoxWidth, sigImgHeight,
                        "Applicant", request.getEmployeeSignature(), employee.getFullName(), request.getEmployeeSignedAt());
                drawSignatureBlock(doc, cs, regular, bold, marginX + sigBoxWidth + 30, y, sigBoxWidth, sigImgHeight,
                        (approved ? "Approved" : "Declined") + " by",
                        request.getDeciderSignature(),
                        request.getDecidedBy() != null ? request.getDecidedBy().getFullName() : "-",
                        request.getDeciderSignedAt());

                y -= sigImgHeight + 70;
                cs.setNonStrokingColor(140, 130, 128);
                text(cs, regular, 7.5f, marginX, y, "This document was electronically generated and signed within the K and K Media Payroll System.");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate leave letter PDF", e);
        }
    }

    private void drawSignatureBlock(PDDocument doc, PDPageContentStream cs, PDFont regular, PDFont bold,
                                     float x, float y, float width, float imgHeight,
                                     String label, String signatureDataUrl, String name,
                                     java.time.LocalDateTime signedAt) throws IOException {
        if (signatureDataUrl != null && !signatureDataUrl.isBlank()) {
            try {
                BufferedImage img = decodeDataUrl(signatureDataUrl);
                if (img != null) {
                    PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, toPngBytes(img), "signature");
                    cs.drawImage(pdImage, x, y, width, imgHeight);
                }
            } catch (Exception e) {
                // Corrupt/unsupported signature image — letter still valid, just without that image.
            }
        }
        cs.setStrokingColor(140, 130, 128);
        cs.setLineWidth(0.75f);
        cs.moveTo(x, y - 6);
        cs.lineTo(x + width, y - 6);
        cs.stroke();
        cs.setNonStrokingColor(20, 20, 20);
        text(cs, bold, 9, x, y - 20, name != null ? name : "-");
        cs.setNonStrokingColor(120, 110, 108);
        text(cs, regular, 8, x, y - 32, label);
        if (signedAt != null) {
            text(cs, regular, 8, x, y - 44, "Signed: " + signedAt.toLocalDate());
        }
    }

    private BufferedImage decodeDataUrl(String dataUrl) throws IOException {
        String base64 = dataUrl.contains(",") ? dataUrl.substring(dataUrl.indexOf(',') + 1) : dataUrl;
        byte[] bytes = Base64.getDecoder().decode(base64);
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private byte[] toPngBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private float detailRow(PDPageContentStream cs, PDFont bold, PDFont regular, float x, float y, String label, String value) throws IOException {
        cs.setNonStrokingColor(90, 82, 80);
        text(cs, bold, 9.5f, x, y, label);
        cs.setNonStrokingColor(20, 20, 20);
        text(cs, regular, 9.5f, x + 140, y, value);
        return y - 17;
    }

    private List<String> wrap(PDFont font, float size, String text, float maxWidth) throws IOException {
        text = sanitize(text);
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.getStringWidth(candidate) / 1000 * size > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private void text(PDPageContentStream cs, PDFont font, float size, float x, float y, String value) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(value == null ? "" : sanitize(value));
        cs.endText();
    }

    /** WinAnsi/Standard14 fonts only support Latin-1 — strip anything outside it so PDFBox doesn't throw. */
    private String sanitize(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c <= 0xFF ? c : '?');
        }
        return sb.toString();
    }

    private float textWidth(PDFont font, float size, String text) {
        try {
            return font.getStringWidth(sanitize(text)) / 1000 * size;
        } catch (IOException e) {
            return 0;
        }
    }
}
