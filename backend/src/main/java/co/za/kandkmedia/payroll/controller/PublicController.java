package co.za.kandkmedia.payroll.controller;

import co.za.kandkmedia.payroll.domain.Company;
import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.dto.VerificationResponse;
import co.za.kandkmedia.payroll.repository.CompanyRepository;
import co.za.kandkmedia.payroll.repository.PayrollRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;

/**
 * Deliberately returns only the minimum needed to confirm a payslip is
 * genuine — masked employee name, employer, period, ID, status — never the
 * salary figures or any other sensitive detail from the payslip itself.
 *
 * IMPORTANT — not yet rate limited (see backend/README.md). A public,
 * unauthenticated, enumerable-by-brute-force endpoint like this needs rate
 * limiting before it's exposed on the open internet; that's flagged as
 * still open rather than silently skipped.
 */
@RestController
@RequestMapping("/api/public/verify")
@RequiredArgsConstructor
public class PublicController {

    private final PayrollRepository payrollRepository;
    private final CompanyRepository companyRepository;

    private static final DateTimeFormatter GENERATED_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm");

    @GetMapping("/{verificationCode}")
    public VerificationResponse verify(@PathVariable String verificationCode) {
        return payrollRepository.findByVerificationCode(verificationCode.toUpperCase())
                .map(this::toValidResponse)
                .orElseGet(() -> VerificationResponse.builder()
                        .valid(false)
                        .status("Not Found")
                        .build());
    }

    private VerificationResponse toValidResponse(Payroll payroll) {
        Company company = companyRepository.findAll().stream().findFirst().orElse(null);
        return VerificationResponse.builder()
                .valid(true)
                .status("Valid")
                .payslipId(payroll.getPayslipId())
                .employeeName(maskName(payroll.getEmployee().getFullName()))
                .employer(company != null ? company.getName() : "K and K Media (Pty) Ltd")
                .payPeriod(payroll.getPayPeriod())
                .generatedAt(payroll.getDocumentGeneratedAt() != null ? payroll.getDocumentGeneratedAt().format(GENERATED_FMT) : null)
                .build();
    }

    /** "John Doe" -> "J*** D***" — enough to visually match a name, not enough to leak it. */
    private String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (String part : fullName.trim().split("\\s+")) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append("***");
        }
        return sb.toString();
    }
}
