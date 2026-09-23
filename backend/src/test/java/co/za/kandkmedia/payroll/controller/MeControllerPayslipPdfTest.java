package co.za.kandkmedia.payroll.controller;

import co.za.kandkmedia.payroll.domain.AppUser;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.domain.PayrollStatus;
import co.za.kandkmedia.payroll.repository.AppUserRepository;
import co.za.kandkmedia.payroll.repository.CompanyRepository;
import co.za.kandkmedia.payroll.repository.LeaveBalanceRepository;
import co.za.kandkmedia.payroll.repository.PayrollRepository;
import co.za.kandkmedia.payroll.service.EmployeeProfileService;
import co.za.kandkmedia.payroll.service.LeaveService;
import co.za.kandkmedia.payroll.service.PayslipPdfService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * The self-service payslip PDF endpoint (GET /api/me/payslips/{id}/pdf)
 * must never hand back another employee's document, and must never hand
 * back an unfinalized one — this is the one place in the new real-PDF
 * download flow with actual security logic to get wrong.
 */
class MeControllerPayslipPdfTest {

    private final PayrollRepository payrollRepository = Mockito.mock(PayrollRepository.class);
    private final PayslipPdfService payslipPdfService = Mockito.mock(PayslipPdfService.class);
    private final MeController controller = new MeController(
            Mockito.mock(LeaveService.class),
            Mockito.mock(LeaveBalanceRepository.class),
            payrollRepository,
            Mockito.mock(EmployeeProfileService.class),
            Mockito.mock(AppUserRepository.class),
            Mockito.mock(PasswordEncoder.class),
            Mockito.mock(CompanyRepository.class),
            payslipPdfService,
            Mockito.mock(co.za.kandkmedia.payroll.service.ItAssistantService.class),
            Mockito.mock(co.za.kandkmedia.payroll.service.WorkScheduleService.class)
    );

    private Employee employee(long id, String code) {
        return Employee.builder().id(id).employeeCode(code).firstName("A").lastName("B").email(code + "@kandkmedia.co.za").build();
    }

    private AppUser userFor(Employee employee) {
        AppUser user = new AppUser();
        user.setEmployee(employee);
        return user;
    }

    @Test
    void returnsThePdfForOwnFinalizedPayslip() {
        Employee me = employee(1L, "EMP-00001");
        Payroll payroll = Payroll.builder().id(10L).employee(me).payPeriod("2024-11").status(PayrollStatus.FINALIZED).build();
        when(payrollRepository.findById(10L)).thenReturn(Optional.of(payroll));
        when(payslipPdfService.generate(payroll)).thenReturn(new byte[]{1, 2, 3});

        ResponseEntity<byte[]> response = controller.myPayslipPdf(10L, userFor(me));

        assertThat(response.getBody()).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    void rejectsSomeoneElsesPayslip() {
        Employee me = employee(1L, "EMP-00001");
        Employee someoneElse = employee(2L, "EMP-00002");
        Payroll payroll = Payroll.builder().id(10L).employee(someoneElse).payPeriod("2024-11").status(PayrollStatus.FINALIZED).build();
        when(payrollRepository.findById(10L)).thenReturn(Optional.of(payroll));

        assertThatThrownBy(() -> controller.myPayslipPdf(10L, userFor(me)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("isn't your payslip");
    }

    @Test
    void rejectsAPayslipThatIsNotFinalizedYet() {
        Employee me = employee(1L, "EMP-00001");
        Payroll payroll = Payroll.builder().id(10L).employee(me).payPeriod("2024-11").status(PayrollStatus.DRAFT).build();
        when(payrollRepository.findById(10L)).thenReturn(Optional.of(payroll));

        assertThatThrownBy(() -> controller.myPayslipPdf(10L, userFor(me)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("finalized");
    }

    @Test
    void sentStatusIsAlsoAllowed() {
        Employee me = employee(1L, "EMP-00001");
        Payroll payroll = Payroll.builder().id(10L).employee(me).payPeriod("2024-11").status(PayrollStatus.SENT).build();
        when(payrollRepository.findById(10L)).thenReturn(Optional.of(payroll));
        when(payslipPdfService.generate(payroll)).thenReturn(new byte[]{9});

        ResponseEntity<byte[]> response = controller.myPayslipPdf(10L, userFor(me));

        assertThat(response.getBody()).isEqualTo(new byte[]{9});
    }
}
