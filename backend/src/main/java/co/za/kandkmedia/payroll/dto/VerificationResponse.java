package co.za.kandkmedia.payroll.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VerificationResponse {
    private boolean valid;
    private String status; // "Valid" or "Not Found"
    private String payslipId;
    private String employeeName; // masked, e.g. "C*** M***"
    private String employer;
    private String payPeriod;
    private String generatedAt;
}
