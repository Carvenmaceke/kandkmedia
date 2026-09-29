package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.domain.PayrollStatus;
import co.za.kandkmedia.payroll.repository.PayrollRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Staff are paid a fixed monthly salary — no tax, UIF, allowances, overtime or other deductions. */
class PayrollSalaryOnlyTest {

    private final PayrollRepository repo = mock(PayrollRepository.class);
    private final PayrollService service = new PayrollService(repo, mock(EmailService.class), mock(PayslipPdfService.class), mock(PayslipSecurityService.class));

    {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static BigDecimal r(long v) { return BigDecimal.valueOf(v); }

    @Test
    void draftPaysTheMonthlySalaryWithNoDeductions() {
        Employee e = Employee.builder().id(1L).salary(r(22000)).build();
        when(repo.findByEmployeeIdAndPayPeriod(anyLong(), anyString())).thenReturn(Optional.empty());

        Payroll p = service.generateDraft(e, "2026-09", r(1500), r(2000)).orElseThrow();

        assertThat(p.getBasicSalary()).isEqualByComparingTo("22000");
        assertThat(p.getGrossPay()).isEqualByComparingTo("22000");
        assertThat(p.getNetPay()).isEqualByComparingTo("22000");
        assertThat(p.getPaye()).isZero();
        assertThat(p.getUif()).isZero();
        assertThat(p.getTotalDeductions()).isZero();
        assertThat(p.getOvertime()).isZero();
        assertThat(p.getBonus()).isZero();
        assertThat(p.getHousingAllowance()).isZero();
        assertThat(p.getTransportAllowance()).isZero();
    }

    @Test
    void startupClearsOldDeductionsOnUnsentRecordsButLeavesSentOnesAlone() {
        Payroll draft = Payroll.builder().payPeriod("2026-09").status(PayrollStatus.REVIEWED).basicSalary(r(22000))
                .transportAllowance(r(1000)).grossPay(r(23000)).paye(r(3450)).uif(r(177)).totalDeductions(r(3627)).netPay(r(19373)).build();
        Payroll sent = Payroll.builder().payPeriod("2026-08").status(PayrollStatus.SENT).basicSalary(r(22000))
                .grossPay(r(23000)).paye(r(3450)).totalDeductions(r(3627)).netPay(r(19373)).build();
        when(repo.findAll()).thenReturn(List.of(draft, sent));

        service.normaliseUnsentRecords();

        assertThat(draft.getGrossPay()).isEqualByComparingTo("22000");
        assertThat(draft.getNetPay()).isEqualByComparingTo("22000");
        assertThat(draft.getTotalDeductions()).isZero();
        assertThat(draft.getPaye()).isZero();
        assertThat(draft.getTransportAllowance()).isZero();
        assertThat(sent.getNetPay()).isEqualByComparingTo("19373");
        verify(repo, times(1)).save(any());

        clearInvocations(repo);
        service.normaliseUnsentRecords(); // already salary-only: nothing to do
        verify(repo, never()).save(any());
    }
}
