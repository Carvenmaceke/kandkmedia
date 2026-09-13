package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.domain.PayrollStatus;
import co.za.kandkmedia.payroll.repository.PayrollRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * NOTE: PAYE and UIF below are simplified placeholders (flat 15% / 1% capped),
 * matching the frontend prototype, purely to make the pipeline demonstrable
 * end-to-end. Replace with a real SARS-compliant tax table before this
 * touches an actual payslip.
 */
@Service
@RequiredArgsConstructor
public class PayrollService {

    private static final BigDecimal PAYE_RATE = new BigDecimal("0.15");
    private static final BigDecimal UIF_RATE = new BigDecimal("0.01");
    private static final BigDecimal UIF_CAP = new BigDecimal("17712"); // monthly UIF-contributable ceiling

    private final PayrollRepository payrollRepository;
    private final EmailService emailService;
    private final PayslipPdfService payslipPdfService;
    private final PayslipSecurityService payslipSecurityService;

    /** Returns empty (creates nothing) if the employee's salary hasn't
     *  been set by HR yet — a payslip shouldn't exist at all for someone
     *  who was only just signed up and defaults to a salary of zero. */
    public java.util.Optional<Payroll> generateDraft(Employee employee, String payPeriod, BigDecimal overtime, BigDecimal bonus) {
        java.util.Optional<Payroll> existing = payrollRepository.findByEmployeeIdAndPayPeriod(employee.getId(), payPeriod);
        if (existing.isPresent()) {
            return existing;
        }
        if (employee.getSalary() == null || employee.getSalary().compareTo(BigDecimal.ZERO) <= 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(payrollRepository.save(calculate(employee, payPeriod, overtime, bonus)));
    }

    /**
     * If this employee already has a payroll record for the current pay
     * period AND it's still sitting in DRAFT, recalculates it from the
     * employee's current salary/allowances. This is what makes an HR
     * salary edit actually show up on that period's payslip — without
     * this, an already-created draft keeps whatever figures it was
     * calculated with at draft-creation time, even after the underlying
     * salary changes, since generateDraft only calculates once and
     * returns the existing row untouched after that.
     *
     * Deliberately scoped to DRAFT only: once HR has moved a record to
     * REVIEWED or further, a salary edit should never silently rewrite
     * numbers someone has already reviewed/approved/finalized.
     */
    public void refreshDraftIfPresent(Employee employee) {
        payrollRepository.findByEmployeeIdAndPayPeriod(employee.getId(), Payroll.currentPeriod())
                .filter(p -> p.getStatus() == PayrollStatus.DRAFT)
                .ifPresent(existing -> {
                    Payroll recalculated = calculate(employee, existing.getPayPeriod(), existing.getOvertime(), existing.getBonus());
                    existing.setBasicSalary(recalculated.getBasicSalary());
                    existing.setHousingAllowance(recalculated.getHousingAllowance());
                    existing.setTransportAllowance(recalculated.getTransportAllowance());
                    existing.setGrossPay(recalculated.getGrossPay());
                    existing.setPaye(recalculated.getPaye());
                    existing.setUif(recalculated.getUif());
                    existing.setTotalDeductions(recalculated.getTotalDeductions());
                    existing.setNetPay(recalculated.getNetPay());
                    payrollRepository.save(existing);
                });
    }

    private Payroll calculate(Employee employee, String payPeriod, BigDecimal overtime, BigDecimal bonus) {
        BigDecimal basic = employee.getSalary();
        BigDecimal housing = isSeniorOrManager(employee) ? new BigDecimal("2000") : BigDecimal.ZERO;
        BigDecimal transport = isIntern(employee) ? BigDecimal.ZERO : new BigDecimal("1000");
        BigDecimal gross = basic.add(overtime).add(bonus).add(housing).add(transport);

        BigDecimal paye = gross.multiply(PAYE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal uif = gross.min(UIF_CAP).multiply(UIF_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDeductions = paye.add(uif);
        BigDecimal net = gross.subtract(totalDeductions);

        return Payroll.builder()
                .employee(employee)
                .payPeriod(payPeriod)
                .basicSalary(basic)
                .overtime(overtime)
                .bonus(bonus)
                .housingAllowance(housing)
                .transportAllowance(transport)
                .grossPay(gross)
                .paye(paye)
                .uif(uif)
                .totalDeductions(totalDeductions)
                .netPay(net)
                .status(PayrollStatus.DRAFT)
                .build();
    }

    /** Advances every record for a pay period exactly one stage — mirrors the HR "Advance to X" button. */
    public List<Payroll> advanceStage(String payPeriod) {
        List<Payroll> records = payrollRepository.findByPayPeriod(payPeriod);
        if (records.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No payroll records found for " + payPeriod);
        }
        PayrollStatus[] stages = PayrollStatus.values();
        boolean movingToSent = false;
        for (Payroll payroll : records) {
            int idx = payroll.getStatus().ordinal();
            if (idx < stages.length - 1) {
                PayrollStatus next = stages[idx + 1];
                payroll.setStatus(next);
                if (next == PayrollStatus.FINALIZED) {
                    payroll.setFinalizedAt(LocalDateTime.now());
                    sealPayslip(payroll);
                }
                if (next == PayrollStatus.SENT) {
                    movingToSent = true;
                }
            }
        }
        List<Payroll> saved = payrollRepository.saveAll(records);

        // Actually send the payslip emails once the batch reaches SENT.
        // sendPayslip() catches its own mail errors and records them on the
        // row (emailFailureReason) rather than throwing, so one bad address
        // doesn't stop the rest of the batch from sending.
        if (movingToSent) {
            for (Payroll payroll : saved) {
                if (payroll.getEmployee().isNotifyPayslip()) {
                    emailService.sendPayslip(payroll);
                }
            }
            saved = payrollRepository.saveAll(saved);
        }

        return saved;
    }

    /** Resend a single payslip email — used by HR to retry a failed send. */
    public Payroll resendEmail(Long payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll record not found."));
        emailService.sendPayslip(payroll);
        return payrollRepository.save(payroll);
    }

    /** Removes a payroll record entirely — restricted to DRAFT only, since
     *  anything past that point (Reviewed, Approved, Finalized, Sent) is a
     *  real record that must be kept for audit purposes, not deleted. This
     *  exists specifically to clean up drafts that were auto-created for
     *  employees before their salary had been set (the signup default of
     *  zero) — a bug fixed in generateDraft, but one that could have
     *  already created erroneous rows before this fix shipped. */
    public void deleteDraft(Long payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll record not found."));
        if (payroll.getStatus() != PayrollStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a DRAFT payroll record can be deleted — this one has already moved past that stage.");
        }
        payrollRepository.delete(payroll);
    }

    /**
     * Assigns the payslip's permanent security identifiers and hashes the
     * resulting PDF. Called exactly once, at the DRAFT→...→FINALIZED
     * transition — these values are never regenerated afterwards, since a
     * document ID / hash that could change wouldn't be trustworthy as a
     * "this hasn't been altered" check.
     */
    private void sealPayslip(Payroll payroll) {
        payroll.setPayslipId(payslipSecurityService.generatePayslipId(payroll));
        payroll.setVerificationCode(payslipSecurityService.generateUniqueVerificationCode());
        payroll.setDocumentGeneratedAt(LocalDateTime.now());

        byte[] pdf = payslipPdfService.generate(payroll);
        payroll.setDocumentHash(payslipSecurityService.sha256Hex(pdf));
    }

    private boolean isSeniorOrManager(Employee employee) {
        String level = employee.getLevel() != null ? employee.getLevel().getName() : "";
        return "Senior".equals(level) || "Manager".equals(level);
    }

    private boolean isIntern(Employee employee) {
        String level = employee.getLevel() != null ? employee.getLevel().getName() : "";
        return "Intern".equals(level);
    }
}
