package co.za.kandkmedia.payroll.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * One row per employee per pay period. While DRAFT/REVIEWED/APPROVED it's a
 * working payroll record; once FINALIZED it becomes an immutable historical
 * snapshot — the same data a generated payslip PDF would be built from.
 * Editing an employee's current salary must never change past rows here.
 */
@Entity
@Table(name = "payroll", uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "pay_period"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** e.g. "2026-09" — one record per employee per calendar month. */
    @Column(name = "pay_period", nullable = false)
    private String payPeriod;

    @Builder.Default
    private BigDecimal basicSalary = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal overtime = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal bonus = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal housingAllowance = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal transportAllowance = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal grossPay = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal paye = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal uif = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal otherDeductions = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal netPay = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PayrollStatus status = PayrollStatus.DRAFT;

    private LocalDateTime finalizedAt;

    @Builder.Default
    private boolean emailSent = false;

    private LocalDateTime emailSentAt;
    private String emailFailureReason;

    public static String currentPeriod() {
        return YearMonth.now().toString(); // "2026-09"
    }
}
