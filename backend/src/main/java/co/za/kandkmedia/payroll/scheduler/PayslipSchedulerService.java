package co.za.kandkmedia.payroll.scheduler;

import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.domain.PayrollStatus;
import co.za.kandkmedia.payroll.repository.EmployeeRepository;
import co.za.kandkmedia.payroll.service.PayrollService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Runs once a day (see app.payslip-delivery.cron-hour/cron-minute) and, on
 * the configured trigger day only, generates any missing draft payroll rows
 * for the current pay period and pushes every one of that period's records
 * through to SENT — which is what actually fires the real payslip emails
 * (PayrollService.advanceStage sends on the transition into SENT).
 *
 * Deliberately does NOT stop to wait for HR review: this is the automatic
 * safety-net send described in the spec ("automatically generate and send
 * payslips at the end of every month"). If HR wants to review variable
 * earnings (overtime, bonus) before anyone is paid, that has to happen
 * earlier in the month via the normal HR payroll screens — anything still
 * sitting in DRAFT on the trigger day goes out with whatever figures it
 * already has (overtime/bonus default to zero if HR never generated a
 * draft for that employee at all).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayslipSchedulerService {

    private final EmployeeRepository employeeRepository;
    private final PayrollService payrollService;

    @Value("${app.payslip-delivery.auto-send:true}")
    private boolean autoSendEnabled;

    @Value("${app.payslip-delivery.send-on:LAST_DAY_OF_MONTH}")
    private String sendOn;

    @Scheduled(cron = "0 ${app.payslip-delivery.cron-minute:0} ${app.payslip-delivery.cron-hour:18} * * ?")
    public void runMonthEndPayslipRun() {
        if (!autoSendEnabled) {
            return;
        }
        if (!isTriggerDayToday()) {
            return;
        }

        String period = Payroll.currentPeriod();
        log.info("Automatic month-end payslip run starting for {}", period);

        try {
            for (Employee employee : employeeRepository.findAll()) {
                payrollService.generateDraft(employee, period, BigDecimal.ZERO, BigDecimal.ZERO);
            }

            // One call per remaining stage to walk everything through to SENT.
            // Records already further along (e.g. HR got them to APPROVED
            // earlier in the month) just continue from where they are; records
            // already at SENT are untouched (advanceStage no-ops past the end
            // of the pipeline), so re-running this on the same day is safe and
            // won't double-send anyone's email.
            for (int i = 0; i < PayrollStatus.values().length; i++) {
                payrollService.advanceStage(period);
            }

            log.info("Automatic month-end payslip run complete for {}", period);
        } catch (Exception e) {
            // A scheduled method that throws stops future executions from
            // being logged clearly by default — catch and log explicitly so
            // a bad run tonight doesn't go unnoticed.
            log.error("Automatic month-end payslip run failed for {}", period, e);
        }
    }

    private boolean isTriggerDayToday() {
        LocalDate today = LocalDate.now();
        LocalDate lastDayOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        if ("DAY_BEFORE_MONTH_END".equalsIgnoreCase(sendOn)) {
            return today.equals(lastDayOfMonth.minusDays(1));
        }
        return today.equals(lastDayOfMonth); // default: LAST_DAY_OF_MONTH
    }
}
