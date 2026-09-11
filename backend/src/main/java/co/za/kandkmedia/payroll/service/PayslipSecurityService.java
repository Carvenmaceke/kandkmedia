package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.repository.PayrollRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Values here are meant to be generated exactly once per payslip, when a
 * Payroll row is finalized (see PayrollService.advanceStage) — never
 * regenerated afterwards, since that's what makes them trustworthy as a
 * "this document hasn't been altered" check.
 */
@Service
@RequiredArgsConstructor
public class PayslipSecurityService {

    // Excludes 0/O and 1/I so a human reading the code aloud/by hand doesn't
    // second-guess which character was meant.
    private static final String CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PayrollRepository payrollRepository;

    public String generatePayslipId(Payroll payroll) {
        return String.format("PAY-%s-%06d", payroll.getPayPeriod(), payroll.getId());
    }

    /** Retries on the astronomically unlikely event of a collision. */
    public String generateUniqueVerificationCode() {
        String code;
        do {
            code = randomCode();
        } while (payrollRepository.findByVerificationCode(code).isPresent());
        return code;
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder("XXXX-XXXX".length());
        for (int i = 0; i < 8; i++) {
            if (i == 4) sb.append('-');
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    public String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM — this can't actually happen.
            throw new IllegalStateException(e);
        }
    }
}
