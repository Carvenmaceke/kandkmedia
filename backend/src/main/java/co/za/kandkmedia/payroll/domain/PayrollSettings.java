package co.za.kandkmedia.payroll.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Singleton settings row (same pattern as Company) controlling the
 * automatic month-end payslip run. Genuinely editable at runtime — see
 * PayslipSchedulerService, which checks this row every minute rather than
 * relying on a fixed @Scheduled cron expression (those are baked in at
 * application startup and can't change without a restart).
 */
@Entity
@Table(name = "payroll_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    private boolean autoSendEnabled = true;

    /** "LAST_DAY_OF_MONTH" or "DAY_BEFORE_MONTH_END". */
    @Builder.Default
    private String sendOn = "LAST_DAY_OF_MONTH";

    @Builder.Default
    private int deliveryHour = 18;

    @Builder.Default
    private int deliveryMinute = 0;

    /** Guards against firing more than once on the trigger day — the
     *  scheduler checks every minute, so without this it would re-run the
     *  whole batch every minute for as long as the clock matches. */
    private java.time.LocalDate lastRunDate;
}
