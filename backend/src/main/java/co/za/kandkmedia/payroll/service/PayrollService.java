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

    public Payroll generateDraft(Employee employee, String payPeriod, BigDecimal overtime, BigDecimal bonus) {
        return payrollRepository.findByEmployeeIdAndPayPeriod(employee.getId(), payPeriod)
                .orElseGet(() -> {
                    BigDecimal basic = employee.getSalary();
                    BigDecimal housing = isSeniorOrManager(employee) ? new BigDecimal("2000") : BigDecimal.ZERO;
                    BigDecimal transport = isIntern(employee) ? BigDecimal.ZERO : new BigDecimal("1000");
                    BigDecimal gross = basic.add(overtime).add(bonus).add(housing).add(transport);

                    BigDecimal paye = gross.multiply(PAYE_RATE).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal uif = gross.min(UIF_CAP).multiply(UIF_RATE).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal totalDeductions = paye.add(uif);
                    BigDecimal net = gross.subtract(totalDeductions);

                    Payroll payroll = Payroll.builder()
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
                    return payrollRepository.save(payroll);
                });
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
                emailService.sendPayslip(payroll);
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
