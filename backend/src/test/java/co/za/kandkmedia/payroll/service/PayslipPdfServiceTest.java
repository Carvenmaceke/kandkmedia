package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Company;
import co.za.kandkmedia.payroll.domain.Department;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.domain.PayrollStatus;
import co.za.kandkmedia.payroll.repository.CompanyRepository;
import co.za.kandkmedia.payroll.repository.PayrollRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Renders a real payslip PDF and reads its text back, checking it follows
 * the approved K &amp; K Media payslip design and carries the employee's own
 * details and figures.
 */
class PayslipPdfServiceTest {

    private final PayrollRepository payrollRepository = Mockito.mock(PayrollRepository.class);
    private final CompanyRepository companyRepository = Mockito.mock(CompanyRepository.class);
    private final PayslipPdfService service = new PayslipPdfService(payrollRepository, companyRepository);

    @BeforeEach
    void setUp() {
        when(companyRepository.findAll()).thenReturn(List.of(Company.builder().name("K & K Media (Pty) Ltd").build()));
        ReflectionTestUtils.setField(service, "verificationBaseUrl", "https://kandkmedia.example/api/public/verify");
        ReflectionTestUtils.setField(service, "defaultLogoUrl", ""); // no network in tests: text wordmark
    }

    private Employee employee() {
        return Employee.builder()
                .id(42L)
                .employeeCode("EMP-00042")
                .firstName("Belle")
                .lastName("Petersen")
                .email("belle.petersen@kandkmedia.co.za")
                .position("Graphic Designer")
                .department(Department.builder().name("Creative Services").build())
                .salary(BigDecimal.valueOf(22000))
                .startDate(LocalDate.of(2026, 8, 1))
                .bankAccountNumber("1706477870")
                .bankBranchCode("470010")
                .bankName("Capitec")
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
                .payPeriod("2026-09")
                .basicSalary(BigDecimal.valueOf(22000))
                .grossPay(BigDecimal.valueOf(22000))
                .totalDeductions(BigDecimal.ZERO)
                .netPay(BigDecimal.valueOf(22000))
                .status(PayrollStatus.SENT)
                .payslipId("PAY-2026-09-000042")
                .verificationCode("ABC123XYZ")
                .documentGeneratedAt(LocalDateTime.of(2026, 9, 30, 9, 15))
                .build();
    }

    private String render(Payroll payroll) throws Exception {
        byte[] pdf = service.generate(payroll);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        try (PDDocument doc = PDDocument.load(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            assertThat(doc.getPage(0).getMediaBox().getWidth()).isEqualTo(PDRectangle.LETTER.getWidth());
            assertThat(doc.getDocumentInformation().getTitle()).isEqualTo("Payslip - K & K Media (Pty) Ltd");
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void salaryOnlyPayslipWithTheEmployeesOwnDetails() throws Exception {
        when(payrollRepository.findByEmployeeIdOrderByPayPeriodDesc(anyLong())).thenReturn(List.of());

        String text = render(payroll(employee()));

        // Sections and labels
        assertThat(text).contains("PAYSLIP", "EARNINGS", "NETT PAY", "YEAR TO DATE TOTALS", "CURRENT PERIOD",
                "ADDITIONAL INFO", "Emp Code", "Emp Name", "Co. Address", "Payment Date", "Date Engaged",
                "Account No", "Branch Code", "CONSTANTIA SQUARE OFFICE", "RANDJESFONTEIN, MIDRAND", "Page 1 of 1",
                "K&K", "MEDIA");
        // Paid a fixed monthly salary: no hours, tax, UIF or any deductions
        assertThat(text).contains("Monthly Salary", "Total Earnings", "Deductions:", "None", "Pay Basis", "Monthly salary");
        assertThat(text).doesNotContain("DEDUCTIONS", "Tax", "U.I.F", "Normal Time", "Days", "Overtime", "Opening Bal.", "Co. Contributions");
        // Per-employee values
        assertThat(text).contains("K & K MEDIA (PTY) LTD", "EMP-00042", "BELLE PETERSEN", "42 EXAMPLE STREET",
                "SANDTON, JOHANNESBURG", "30/09/2026", "01/08/2026", "Capitec", "1706477870", "470010");
        // Salary = total earnings = nett pay
        assertThat(text).contains("22,000.00", "R 22,000.00");
        assertThat(text).contains("September 2026", "Graphic Designer", "Creative Services", "PAY-2026-09-000042", "ABC123XYZ");
    }

    @Test
    void omitsVerificationDetailsUntilThePayslipIsSealed() throws Exception {
        when(payrollRepository.findByEmployeeIdOrderByPayPeriodDesc(anyLong())).thenReturn(List.of());
        Payroll payroll = payroll(employee());
        payroll.setPayslipId(null);
        payroll.setVerificationCode(null);

        String text = render(payroll);

        assertThat(text).doesNotContain("Payslip ID", "Scan to verify");
        assertThat(text).contains("BELLE PETERSEN", "R 22,000.00");
    }

    @Test
    void sumsYearToDateFromMarchOnly() throws Exception {
        Employee employee = employee();
        Payroll current = payroll(employee);
        Payroll august = Payroll.builder().payPeriod("2026-08").grossPay(BigDecimal.valueOf(21000)).netPay(BigDecimal.valueOf(21000)).build();
        Payroll lastTaxYear = Payroll.builder().payPeriod("2026-02").grossPay(BigDecimal.valueOf(99999)).netPay(BigDecimal.valueOf(99999)).build();
        when(payrollRepository.findByEmployeeIdOrderByPayPeriodDesc(anyLong())).thenReturn(List.of(current, august, lastTaxYear));

        String text = render(current);

        assertThat(text).contains("43,000.00"); // 22,000 + 21,000 earnings and nett pay
        assertThat(text).doesNotContain("99,999.00");
    }

    @Test
    void handlesMissingOptionalDetailsAndNonLatinCharacters() throws Exception {
        when(payrollRepository.findByEmployeeIdOrderByPayPeriodDesc(anyLong())).thenReturn(List.of());
        Employee employee = Employee.builder().id(7L).employeeCode("EMP-00007").firstName("Łukasz").lastName("Nkosi").build();
        Payroll payroll = Payroll.builder().employee(employee).payPeriod("2026-09").build();

        String text = render(payroll);

        assertThat(text).contains("EMP-00007", "NKOSI", "R 0.00", "Not provided"); // no banking details on file yet
    }
}
