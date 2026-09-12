package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Company;
import co.za.kandkmedia.payroll.domain.Employee;
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
public class OnboardingDocumentPdfService {

    private final CompanyRepository companyRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    private record Field(String label, String value) {}

    public byte[] generate(Employee e) {
        Company company = companyRepository.findAll().stream().findFirst().orElse(null);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDFont bold = PDType1Font.HELVETICA_BOLD;
            PDFont regular = PDType1Font.HELVETICA;
            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            float marginX = 42;

            PDPageContentStream[] csHolder = new PDPageContentStream[]{new PDPageContentStream(doc, page)};
            float[] yHolder = new float[]{0};

            drawHeader(csHolder[0], company, marginX, pageWidth, pageHeight);
            yHolder[0] = pageHeight - 128;

            csHolder[0].setNonStrokingColor(20, 20, 20);
            text(csHolder[0], bold, 13, marginX, yHolder[0], e.getFullName());
            yHolder[0] -= 16;
            text(csHolder[0], regular, 9.5f, marginX, yHolder[0],
                    e.getEmployeeCode() + " - " + nvl(e.getPosition()) + " - " + nvl(e.getPhone()));
            yHolder[0] -= 26;

            List<Field> personal = List.of(
                    new Field("Title", e.getTitle()), new Field("First Name", e.getFirstName()),
                    new Field("Second Name", e.getSecondName()), new Field("Last Name", e.getLastName()),
                    new Field("Initials", e.getInitials()),
                    new Field("Date of Birth", e.getDateOfBirth() != null ? e.getDateOfBirth().format(DATE_FMT) : null),
                    new Field("Identity Number", e.getIdNumber()), new Field("Passport Number", e.getPassportNumber()),
                    new Field("Passport Country", e.getPassportCountry()), new Field("Race", e.getRace()),
                    new Field("Relationship Status", e.getRelationshipStatus()),
                    new Field("Contact Telephone", e.getContactTelephone()), new Field("Contact Cellphone", e.getContactCellphone()),
                    new Field("Emergency Contact Name", e.getEmergencyContactName()),
                    new Field("Emergency Contact Telephone", e.getEmergencyContactTelephone()),
                    new Field("Emergency Contact Cellphone", e.getEmergencyContactCellphone())
            );
            List<Field> tax = List.of(new Field("Tax Office", e.getTaxOffice()), new Field("Income Tax Number", e.getIncomeTaxNumber()));
            List<Field> banking = List.of(
                    new Field("Type of Account", e.getBankAccountType()), new Field("Branch Code", e.getBankBranchCode()),
                    new Field("Bank Name", e.getBankName()), new Field("Branch Name", e.getBankBranchName()),
                    new Field("Bank Account Number", e.getBankAccountNumber()), new Field("Account Holder", e.getBankAccountHolder()),
                    new Field("Account Relationship", e.getBankAccountRelationship())
            );
            List<Field> residential = List.of(
                    new Field("Unit Number", e.getResUnitNumber()), new Field("Complex Name", e.getResComplexName()),
                    new Field("Street Number", e.getResStreetNumber()), new Field("Street Name", e.getResStreetName()),
                    new Field("Suburb", e.getResSuburb()), new Field("City", e.getResCity()), new Field("Postal Code", e.getResPostalCode())
            );
            List<Field> postal = List.of(
                    new Field("Postal Service", e.getPostalService()), new Field("Postal Number", e.getPostalNumber()),
                    new Field("Street Number", e.getPostStreetNumber()), new Field("Street Name", e.getPostStreetName()),
                    new Field("Suburb", e.getPostSuburb()), new Field("City", e.getPostCity()), new Field("Postal Code", e.getPostPostalCode())
            );

            PDFont finalBold = bold;
            PDFont finalRegular = regular;
            java.util.function.BiConsumer<String, List<Field>> section = (title, fields) -> {
                try {
                    if (yHolder[0] < 90) {
                        csHolder[0].close();
                        PDPage newPage = new PDPage(PDRectangle.A4);
                        doc.addPage(newPage);
                        csHolder[0] = new PDPageContentStream(doc, newPage);
                        yHolder[0] = pageHeight - 60;
                    }
                    yHolder[0] += 6;
                    csHolder[0].setNonStrokingColor(251, 234, 234);
                    csHolder[0].addRect(marginX, yHolder[0] - 12, pageWidth - marginX * 2, 18);
                    csHolder[0].fill();
                    csHolder[0].setNonStrokingColor(216, 31, 44);
                    text(csHolder[0], finalBold, 9.5f, marginX + 6, yHolder[0], title.toUpperCase());
                    yHolder[0] -= 20;
                    for (Field f : fields) {
                        if (yHolder[0] < 60) {
                            csHolder[0].close();
                            PDPage newPage = new PDPage(PDRectangle.A4);
                            doc.addPage(newPage);
                            csHolder[0] = new PDPageContentStream(doc, newPage);
                            yHolder[0] = pageHeight - 60;
                        }
                        csHolder[0].setNonStrokingColor(90, 82, 80);
                        text(csHolder[0], finalBold, 9, marginX, yHolder[0], f.label());
                        csHolder[0].setNonStrokingColor(20, 20, 20);
                        text(csHolder[0], finalRegular, 9, marginX + 160, yHolder[0], nvl(f.value()));
                        yHolder[0] -= 15;
                    }
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            };

            section.accept("Personal Information", personal);
            section.accept("Tax", tax);
            section.accept("Banking Details", banking);
            section.accept("Residential Address", residential);
            section.accept("Postal Address", postal);

            if (yHolder[0] < 160) {
                csHolder[0].close();
                PDPage newPage = new PDPage(PDRectangle.A4);
                doc.addPage(newPage);
                csHolder[0] = new PDPageContentStream(doc, newPage);
                yHolder[0] = pageHeight - 60;
            }
            yHolder[0] -= 20;
            csHolder[0].setNonStrokingColor(90, 82, 80);
            String declaration = "I declare that the information provided in this document is true and correct, and I consent to " +
                    (company != null ? company.getName() : "the company") + " processing this personal information for payroll and HR administration purposes.";
            List<String> declLines = wrap(regular, 9, declaration, pageWidth - marginX * 2);
            for (String line : declLines) {
                text(csHolder[0], regular, 9, marginX, yHolder[0], line);
                yHolder[0] -= 13;
            }
            yHolder[0] -= 24;

            float sigWidth = 200, sigHeight = 50;
            if (e.getOnboardingSignature() != null && !e.getOnboardingSignature().isBlank()) {
                try {
                    BufferedImage img = decodeDataUrl(e.getOnboardingSignature());
                    if (img != null) {
                        PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, toPngBytes(img), "signature");
                        csHolder[0].drawImage(pdImage, marginX, yHolder[0], sigWidth, sigHeight);
                    }
                } catch (Exception ex) {
                    // corrupt/unsupported signature — leave just the line below
                }
            }
            csHolder[0].setStrokingColor(140, 130, 128);
            csHolder[0].setLineWidth(0.75f);
            csHolder[0].moveTo(marginX, yHolder[0] - 6);
            csHolder[0].lineTo(marginX + sigWidth, yHolder[0] - 6);
            csHolder[0].stroke();
            csHolder[0].setNonStrokingColor(20, 20, 20);
            text(csHolder[0], bold, 9, marginX, yHolder[0] - 20, e.getFullName());
            csHolder[0].setNonStrokingColor(120, 110, 108);
            text(csHolder[0], regular, 8, marginX, yHolder[0] - 32,
                    e.getTermsAgreedAt() != null ? "Signed: " + e.getTermsAgreedAt().format(DateTimeFormatter.ofPattern("d MMM yyyy")) : "Not yet signed");

            csHolder[0].close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to generate onboarding document", ex);
        }
    }

    private void drawHeader(PDPageContentStream cs, Company company, float marginX, float pageWidth, float pageHeight) throws IOException {
        cs.setNonStrokingColor(23, 17, 15);
        cs.addRect(0, pageHeight - 90, pageWidth, 90);
        cs.fill();
        cs.setNonStrokingColor(216, 31, 44);
        cs.addRect(marginX, pageHeight - 26, 40, 3);
        cs.fill();
        cs.setNonStrokingColor(255, 255, 255);
        text(cs, PDType1Font.HELVETICA_BOLD, 15, marginX, pageHeight - 50, company != null ? company.getName() : "Company");
        cs.setNonStrokingColor(201, 191, 188);
        text(cs, PDType1Font.HELVETICA, 8.5f, marginX, pageHeight - 64, company != null && company.getAddress() != null ? company.getAddress() : "");
        String title = "EMPLOYEE ONBOARDING RECORD";
        cs.setNonStrokingColor(255, 255, 255);
        text(cs, PDType1Font.HELVETICA_BOLD, 11, pageWidth - marginX - textWidth(PDType1Font.HELVETICA_BOLD, 11, title), pageHeight - 50, title);
    }

    private String nvl(String s) { return s == null || s.isBlank() ? "-" : s; }

    private void text(PDPageContentStream cs, PDFont font, float size, float x, float y, String value) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(value));
        cs.endText();
    }

    private String sanitize(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) sb.append(c <= 0xFF ? c : '?');
        return sb.toString();
    }

    private float textWidth(PDFont font, float size, String text) {
        try { return font.getStringWidth(sanitize(text)) / 1000 * size; } catch (IOException e) { return 0; }
    }

    private List<String> wrap(PDFont font, float size, String text, float maxWidth) {
        text = sanitize(text);
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            float w;
            try { w = font.getStringWidth(candidate) / 1000 * size; } catch (IOException e) { w = 0; }
            if (w > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
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
}
