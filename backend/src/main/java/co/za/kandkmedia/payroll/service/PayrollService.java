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
import java.time.LocalDateTime;
import java.util.List;

/**
 * K and K Media pays a fixed monthly salary: no hours, overtime, allowances,
 * tax (PAYE), UIF or other deductions — so every payslip's gross and nett pay
 * equal the employee's monthly salary.
 */
@Service
@RequiredArgsConstructor
public class PayrollService {

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

    /** Salary only. overtime/bonus are accepted for API compatibility but not paid. */
    private Payroll calculate(Employee employee, String payPeriod, BigDecimal overtime, BigDecimal bonus) {
        BigDecimal salary = employee.getSalary() == null ? BigDecimal.ZERO : employee.getSalary();
        return Payroll.builder()
                .employee(employee)
                .payPeriod(payPeriod)
                .basicSalary(salary)
                .overtime(BigDecimal.ZERO)
                .bonus(BigDecimal.ZERO)
                .housingAllowance(BigDecimal.ZERO)
                .transportAllowance(BigDecimal.ZERO)
                .grossPay(salary)
                .paye(BigDecimal.ZERO)
                .uif(BigDecimal.ZERO)
                .totalDeductions(BigDecimal.ZERO)
                .netPay(salary)
                .status(PayrollStatus.DRAFT)
                .build();
    }

    /**
     * Brings payroll records that haven't been sent yet in line with the salary-only rule:
     * any old tax/UIF/allowance/overtime figures are cleared and nett pay set to the salary.
     * Sent payslips are history and are left as they were. Runs once at startup; idempotent.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @org.springframework.transaction.annotation.Transactional
    public void normaliseUnsentRecords() {
        int fixed = 0;
        for (Payroll p : payrollRepository.findAll()) {
            if (p.getStatus() == PayrollStatus.SENT) continue;
            BigDecimal salary = p.getBasicSalary() == null ? BigDecimal.ZERO : p.getBasicSalary();
            boolean alreadySalaryOnly = nz(p.getTotalDeductions()).signum() == 0 && nz(p.getOvertime()).signum() == 0
                    && nz(p.getBonus()).signum() == 0 && nz(p.getHousingAllowance()).signum() == 0
                    && nz(p.getTransportAllowance()).signum() == 0 && nz(p.getGrossPay()).compareTo(salary) == 0
                    && nz(p.getNetPay()).compareTo(salary) == 0;
            if (alreadySalaryOnly) continue;
            p.setOvertime(BigDecimal.ZERO);
            p.setBonus(BigDecimal.ZERO);
            p.setHousingAllowance(BigDecimal.ZERO);
            p.setTransportAllowance(BigDecimal.ZERO);
            p.setPaye(BigDecimal.ZERO);
            p.setUif(BigDecimal.ZERO);
            p.setOtherDeductions(BigDecimal.ZERO);
            p.setTotalDeductions(BigDecimal.ZERO);
            p.setGrossPay(salary);
            p.setNetPay(salary);
            if (p.getPayslipId() != null) sealPayslip(p); // finalized but unsent: re-hash the corrected document
            payrollRepository.save(p);
            fixed++;
        }
        if (fixed > 0) org.slf4j.LoggerFactory.getLogger(PayrollService.class).info("Updated {} unsent payroll record(s) to salary-only", fixed);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a DRAFT payroll record can be deleted — this one has already moved past that stage. Use the Master-only force-delete for a record that's wrong but already advanced.");
        }
        payrollRepository.delete(payroll);
    }

    /**
     * Deletes a payroll record regardless of status — deliberately kept
     * separate from deleteDraft and restricted to MASTER only (see the
     * controller's @PreAuthorize). This exists for exactly one situation:
     * a record that was wrong from the start (e.g. the zero-salary
     * auto-draft bug) got advanced past DRAFT — possibly all the way to
     * SENT, meaning an email may have already gone out with the wrong
     * figures — before anyone noticed. A routine, correctly-calculated
     * Sent/Finalized payslip should never go through this path; this is
     * for correcting bad data, not undoing legitimate payroll history.
     */
    public void forceDelete(Long payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll record not found."));
        payrollRepository.delete(payroll);
    }

    /**
     * Corrects a payroll record that's stuck at a zero basic salary, no
     * matter what stage it's already reached — including SENT. This is
     * deliberately narrower than a general "edit anything" escape hatch:
     * it only acts when basicSalary is exactly zero, which can only have
     * happened from the auto-draft-for-a-not-yet-salaried-employee bug,
     * never from a real salary someone was actually paid. A record with
     * any real basic salary, at any stage, is refused here — that's not
     * this method's job, and it should never become one.
     *
     * If the record had already reached SENT (meaning a — necessarily
     * wrong — payslip email may already have gone out), this also
     * resends it with the corrected figures once fixed, so the employee
     * ends up with the right document rather than the original R0 one.
     */
    public Payroll correctZeroSalaryRecord(Long payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll record not found."));
        if (payroll.getBasicSalary() != null && payroll.getBasicSalary().compareTo(BigDecimal.ZERO) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This record already has a real basic salary — it isn't one of the zero-salary records this action is for.");
        }
        Employee employee = payroll.getEmployee();
        if (employee.getSalary() == null || employee.getSalary().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This employee's salary still hasn't been set — set it under Employees first.");
        }

        Payroll recalculated = calculate(employee, payroll.getPayPeriod(), payroll.getOvertime(), payroll.getBonus());
        payroll.setBasicSalary(recalculated.getBasicSalary());
        payroll.setHousingAllowance(recalculated.getHousingAllowance());
        payroll.setTransportAllowance(recalculated.getTransportAllowance());
        payroll.setGrossPay(recalculated.getGrossPay());
        payroll.setPaye(recalculated.getPaye());
        payroll.setUif(recalculated.getUif());
        payroll.setTotalDeductions(recalculated.getTotalDeductions());
        payroll.setNetPay(recalculated.getNetPay());

        if (payroll.getStatus() == PayrollStatus.FINALIZED || payroll.getStatus() == PayrollStatus.SENT) {
            // The security seal (payslip ID, hash) was generated over the
            // wrong figures — reseal so the hash actually matches the
            // corrected document, then resend with the right numbers.
            sealPayslip(payroll);
        }
        Payroll saved = payrollRepository.save(payroll);
        if (saved.getStatus() == PayrollStatus.SENT) {
            emailService.sendPayslip(saved);
            saved = payrollRepository.save(saved);
        }
        return saved;
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


}
