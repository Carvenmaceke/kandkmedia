package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.domain.PayrollStatus;
import co.za.kandkmedia.payroll.repository.CompanyRepository;
import co.za.kandkmedia.payroll.repository.PayrollRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Verifies the payslip pipeline built on the real company .docx template
 * (PayslipDocxTemplateService) rather than the old hand-drawn PDFBox
 * layout. The docx-level assertions run everywhere; the full PDF
 * conversion only runs where headless LibreOffice ("soffice") is
 * actually installed, same as this project's other environment-dependent
 * checks skip cleanly where their dependency is unavailable.
 */
class PayslipPdfServiceTest {

    private final PayrollRepository payrollRepository = Mockito.mock(PayrollRepository.class);
    private final CompanyRepository companyRepository = Mockito.mock(CompanyRepository.class);
    private final PayslipDocxTemplateService docxTemplateService = new PayslipDocxTemplateService(companyRepository);
    private final DocxToPdfConverter docxToPdfConverter = new DocxToPdfConverter();
    private final PayslipPdfService payslipPdfService = new PayslipPdfService(docxTemplateService, docxToPdfConverter, payrollRepository);

    PayslipPdfServiceTest() {
        // A Company with a logoUrl that refuses the connection immediately (nothing listens on
        // port 1) rather than an empty Company list — with no company row at all,
        // PayslipDocxTemplateService now falls back to the real kandkmedia.co.za default logo
        // URL, and hitting that from every test run would make the suite depend on the network
        // and a third-party site staying up. The fetch failing fast still exercises (and proves
        // non-fatal) the "logo unreachable" path without either problem.
        when(companyRepository.findAll()).thenReturn(List.of(
                co.za.kandkmedia.payroll.domain.Company.builder().logoUrl("http://127.0.0.1:1/unreachable.png").build()));
        ReflectionTestUtils.setField(docxTemplateService, "verificationBaseUrl", "https://kandkmedia.example/api/public/verify");
    }

    private Employee employee() {
        return Employee.builder()
                .employeeCode("EMP-00042")
                .firstName("John")
                .lastName("Test-Employee")
                .email("john.test@kandkmedia.co.za")
                .office("Midrand")
                .salary(BigDecimal.valueOf(22000))
                .startDate(LocalDate.of(2024, 8, 1))
                .bankAccountNumber("1234567890")
                .bankBranchCode("250655")
                .resStreetNumber("42")
                .resStreetName("Example Street")
                .resSuburb("Sandton")
                .resCity("Johannesburg")
                .resPostalCode("2196")
                .active(true)
                .build();
    }

    private Payroll payroll(Employee employee) {
        return Payroll.builder()
                .employee(employee)
                .payPeriod("2024-11")
                .basicSalary(BigDecimal.valueOf(22000))
                .housingAllowance(BigDecimal.valueOf(3000))
                .transportAllowance(BigDecimal.valueOf(1500))
                .overtime(BigDecimal.ZERO)
                .bonus(BigDecimal.ZERO)
                .grossPay(BigDecimal.valueOf(26500))
                .paye(BigDecimal.valueOf(850))
                .uif(BigDecimal.valueOf(220))
                .otherDeductions(BigDecimal.ZERO)
                .totalDeductions(BigDecimal.valueOf(1070))
                .netPay(BigDecimal.valueOf(25430))
                .status(PayrollStatus.SENT)
                .payslipId("PAY-2024-11-000042")
                .verificationCode("ABC123XYZ")
                .documentGeneratedAt(LocalDateTime.of(2024, 12, 2, 9, 15))
                .documentHash("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
                .build();
    }

    @Test
    void buildsWellFormedDocxWithNoLeftoverPlaceholderTokens() throws Exception {
        when(payrollRepository.findByEmployeeIdOrderByPayPeriodDesc(anyLong())).thenReturn(List.of());
        Employee employee = employee();
        Payroll payroll = payroll(employee);

        byte[] docx = docxTemplateService.build(payroll, new BigDecimal[]{BigDecimal.valueOf(245000), BigDecimal.valueOf(10700), BigDecimal.valueOf(234300)});

        String documentXml = null;
        boolean hasQrMedia = false;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("word/document.xml")) {
                    documentXml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
                if (entry.getName().equals("word/media/verification-qr.png")) {
                    hasQrMedia = true;
                }
            }
        }

        assertThat(documentXml).isNotNull();
        assertThat(documentXml).doesNotContainPattern("___[A-Z][A-Z_]*___"); // no unsubstituted placeholder tokens (the template's own "_____________" signature line is not one)
        assertThat(documentXml).contains("John Test-Employee"); // employee name present, as stored (we don't uppercase it)
        assertThat(documentXml).contains("EMP-00042");
        assertThat(documentXml).contains("Housing Allowance");
        assertThat(documentXml).contains("Transport Allowance");
        assertThat(documentXml).contains("25430.00"); // nett pay
        assertThat(documentXml).contains("Gross Earnings"); // YTD row label
        assertThat(documentXml).contains("PAY-2024-11-000042"); // verification block
        assertThat(hasQrMedia).isTrue();
        // "Sage VIP" is removed from mc:Choice (the branch every renderer we care about uses —
        // see PayslipDocxTemplateService's class doc); it deliberately stays in mc:Fallback,
        // dead markup nothing renders, so only the Choice branch is checked here.
        int choiceStart = documentXml.indexOf("<mc:Choice");
        int choiceEnd = documentXml.indexOf("</mc:Choice>") + "</mc:Choice>".length();
        assertThat(documentXml.substring(choiceStart, choiceEnd)).doesNotContain("Sage VIP");

        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.newDocumentBuilder().parse(new ByteArrayInputStream(documentXml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void skipsVerificationBlockWhenPayslipNotYetSealed() throws Exception {
        when(payrollRepository.findByEmployeeIdOrderByPayPeriodDesc(anyLong())).thenReturn(List.of());
        Employee employee = employee();
        Payroll payroll = payroll(employee);
        payroll.setPayslipId(null);
        payroll.setVerificationCode(null);
        payroll.setDocumentHash(null);

        byte[] docx = docxTemplateService.build(payroll, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});

        String documentXml = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("word/document.xml")) {
                    documentXml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        assertThat(documentXml).doesNotContain("Payslip ID:");
        assertThat(documentXml).doesNotContainPattern("___[A-Z][A-Z_]*___");
    }

    @Test
    void unreachableLogoUrlDoesNotFailGeneration() {
        // The constructor's companyRepository stub already points at an unreachable logoUrl —
        // this just asserts explicitly that the docx still builds (the QR/verification-style
        // "non-fatal, just skip it" handling applies to the logo fetch too).
        when(payrollRepository.findByEmployeeIdOrderByPayPeriodDesc(anyLong())).thenReturn(List.of());
        byte[] docx = docxTemplateService.build(payroll(employee()), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        assertThat(docx).isNotEmpty();
    }

    @Test
    void convertsToRealPdfViaLibreOfficeWhenAvailable(@TempDir Path tempDir) throws IOException, InterruptedException {
        assumeTrue(isSofficeAvailable(), "soffice not installed in this environment — skipping PDF conversion check");

        when(payrollRepository.findByEmployeeIdOrderByPayPeriodDesc(anyLong())).thenReturn(List.of());
        Employee employee = employee();
        Payroll payroll = payroll(employee);

        byte[] pdf = payslipPdfService.generate(payroll);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        Path out = tempDir.resolve("payslip.pdf");
        Files.write(out, pdf);
        assertThat(Files.size(out)).isGreaterThan(1000);
    }

    private boolean isSofficeAvailable() {
        try {
            Process p = new ProcessBuilder("soffice", "--version").start();
            return p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
