package co.za.kandkmedia.payroll.scheduler;

import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.domain.PayrollSettings;
import co.za.kandkmedia.payroll.domain.PayrollStatus;
import co.za.kandkmedia.payroll.repository.EmployeeRepository;
import co.za.kandkmedia.payroll.repository.PayrollSettingsRepository;
import co.za.kandkmedia.payroll.service.PayrollService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Checks the database-backed PayrollSettings every minute rather than using
 * a fixed @Scheduled cron expression for the delivery time — cron
 * expressions are resolved once at application startup and can't change
 * without a restart, which would defeat the point of making delivery time
 * editable from Company Settings. This runs constantly but does almost
 * nothing on 1,439 out of 1,440 checks a day; the actual work only fires
 * when the current time matches the configured delivery hour/minute AND
 * today is the configured trigger day AND it hasn't already run today
 * (lastRunDate guards against re-firing every minute during that match).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayslipSchedulerService {

    private final EmployeeRepository employeeRepository;
    private final PayrollService payrollService;
    private final PayrollSettingsRepository payrollSettingsRepository;

    @Scheduled(cron = "0 * * * * ?") // every minute, on the minute
    public void checkAndRunIfDue() {
        PayrollSettings settings = payrollSettingsRepository.findAll().stream().findFirst()
                .orElseGet(() -> payrollSettingsRepository.save(PayrollSettings.builder().build()));
        if (!settings.isAutoSendEnabled()) {
            return;
        }
        LocalDate today = LocalDate.now();
        if (today.equals(settings.getLastRunDate())) {
            return; // already ran today
        }
        LocalTime now = LocalTime.now();
        if (now.getHour() != settings.getDeliveryHour() || now.getMinute() != settings.getDeliveryMinute()) {
            return;
        }
        if (!isTriggerDayToday(today, settings.getSendOn())) {
            return;
        }

        runMonthEndPayslipRun();
        settings.setLastRunDate(today);
        payrollSettingsRepository.save(settings);
    }

    private void runMonthEndPayslipRun() {
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
            // of the pipeline), so this is safe to re-run.
            for (int i = 0; i < PayrollStatus.values().length; i++) {
                payrollService.advanceStage(period);
            }
            log.info("Automatic month-end payslip run complete for {}", period);
        } catch (Exception e) {
            log.error("Automatic month-end payslip run failed for {}", period, e);
        }
    }

    private boolean isTriggerDayToday(LocalDate today, String sendOn) {
        LocalDate lastDayOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        if ("DAY_BEFORE_MONTH_END".equalsIgnoreCase(sendOn)) {
            return today.equals(lastDayOfMonth.minusDays(1));
        }
        return today.equals(lastDayOfMonth); // default: LAST_DAY_OF_MONTH
    }
}
