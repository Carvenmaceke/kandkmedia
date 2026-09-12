package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.Payroll;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Actually sends mail — this is not a stub. Sends via Resend's HTTPS API
 * (https://api.resend.com/emails) rather than raw SMTP: Render (and many
 * other hosts) block outbound SMTP ports 25/465/587 as an anti-spam
 * measure, which surfaces as a MailConnectException / connection timeout
 * that has nothing to do with credentials being wrong. HTTPS (443) is
 * never blocked this way, so the API is the reliable path.
 *
 * Reuses the MAIL_PASSWORD env var as the Resend API key (it's already a
 * "re_..." value) rather than introducing a separate one — no deploy
 * config changes needed beyond what was already set up for SMTP.
 *
 * Without a real key, every send fails gracefully: the exception is
 * caught and recorded on the relevant row (emailFailureReason etc.)
 * rather than thrown, so a bad mail config doesn't take down the whole
 * request.
 */
@Service
@Slf4j
public class EmailService {

    private final PayslipPdfService payslipPdfService;
    private final LeaveLetterPdfService leaveLetterPdfService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static final String RESEND_URL = "https://api.resend.com/emails";

    public EmailService(PayslipPdfService payslipPdfService, LeaveLetterPdfService leaveLetterPdfService) {
        this.payslipPdfService = payslipPdfService;
        this.leaveLetterPdfService = leaveLetterPdfService;
    }

    @Value("${app.mail-from:payroll@kandkmedia.co.za}")
    private String fromAddress;

    @Value("${app.support-email:itsupport@kandkmedia.co.za}")
    private String supportEmail;

    @Value("${spring.mail.password:}")
    private String resendApiKey;

    private record SendResult(boolean ok, String errorMessage) {
        static SendResult success() { return new SendResult(true, null); }
        static SendResult failure(String msg) { return new SendResult(false, msg); }
    }

    private SendResult sendViaResend(String to, String replyTo, String subject, String textBody, String attachmentFilename, byte[] attachmentBytes) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            return SendResult.failure("Email is not configured — no Resend API key set (MAIL_PASSWORD).");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("from", fromAddress);
            body.put("to", List.of(to));
            body.put("subject", subject);
            body.put("text", textBody);
            if (replyTo != null && !replyTo.isBlank()) {
                body.put("reply_to", replyTo);
            }
            if (attachmentBytes != null && attachmentFilename != null) {
                body.put("attachments", List.of(Map.of(
                        "filename", attachmentFilename,
                        "content", Base64.getEncoder().encodeToString(attachmentBytes)
                )));
            }
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_URL))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return SendResult.success();
            }
            log.error("Resend API returned {}: {}", response.statusCode(), response.body());
            return SendResult.failure("Resend API error (" + response.statusCode() + "): " + response.body());
        } catch (Exception e) {
            log.error("Failed to send email via Resend", e);
            return SendResult.failure(e.getMessage());
        }
    }

    /**
     * Generates the payslip PDF and emails it to the employee. Returns true
     * and stamps emailSent/emailSentAt on success; on failure, returns false
     * and stamps emailFailureReason instead — callers should persist the
     * Payroll row either way so HR can see (and retry) failed sends.
     */
    public boolean sendPayslip(Payroll payroll) {
        Employee employee = payroll.getEmployee();
        byte[] pdf = payslipPdfService.generate(payroll);
        String subject = payroll.getPayPeriod() + " Payslip - " + employee.getFullName();
        String text = "Hi " + employee.getFirstName() + ",\n\n" +
                "Your payslip for " + payroll.getPayPeriod() + " is attached.\n\n" +
                "Regards,\nK and K Media Payroll";
        String filename = payroll.getPayPeriod() + "-" + employee.getEmployeeCode() + ".pdf";

        SendResult result = sendViaResend(employee.getEmail(), null, subject, text, filename, pdf);
        if (result.ok()) {
            payroll.setEmailSent(true);
            payroll.setEmailSentAt(LocalDateTime.now());
            payroll.setEmailFailureReason(null);
            return true;
        }
        payroll.setEmailSent(false);
        payroll.setEmailFailureReason(result.errorMessage());
        return false;
    }

    /**
     * Generates the signed leave decision letter and emails it to the
     * applicant. Same success/failure contract as sendPayslip.
     */
    public boolean sendLeaveLetter(co.za.kandkmedia.payroll.domain.LeaveRequest request) {
        Employee employee = request.getEmployee();
        boolean approved = request.getStatus() == co.za.kandkmedia.payroll.domain.LeaveStatus.APPROVED;
        byte[] pdf = leaveLetterPdfService.generate(request);

        String subject = "Your " + request.getLeaveType().getName() + " request has been " + (approved ? "approved" : "declined");
        String text = "Hi " + employee.getFirstName() + ",\n\n" +
                "Your " + request.getLeaveType().getName() + " request (" + request.getStartDate() + " to " + request.getEndDate() + ") has been " +
                (approved ? "approved." : "declined.") +
                (!approved && request.getDecisionReason() != null ? "\n\nReason: " + request.getDecisionReason() : "") +
                "\n\nThe signed letter is attached.\n\nRegards,\nK and K Media";
        String filename = "Leave-" + request.getStatus() + "-" + request.getId() + "-" + employee.getEmployeeCode() + ".pdf";

        SendResult result = sendViaResend(employee.getEmail(), null, subject, text, filename, pdf);
        request.setLetterEmailSent(result.ok());
        request.setLetterEmailFailureReason(result.ok() ? null : result.errorMessage());
        return result.ok();
    }

    /**
     * Sends a support ticket straight to the support inbox — this is the
     * "press Send, nothing opens" path: the browser calls the backend, the
     * backend sends the mail server-side. No mailto:, no user email client
     * involved.
     */
    public boolean sendSupportRequest(co.za.kandkmedia.payroll.domain.SupportTicket ticket) {
        String subject = "[" + nullToDash(ticket.getPriority()) + "] " + nullToDash(ticket.getCategory()) + ": " + ticket.getSubject();
        String text =
                "Employee: " + ticket.getEmployeeName() + " (" + nullToDash(ticket.getEmployeeCode()) + ")\n" +
                "Role: " + nullToDash(ticket.getRole()) + "\n" +
                "Department: " + nullToDash(ticket.getDepartment()) + "\n" +
                "Category: " + nullToDash(ticket.getCategory()) + "\n" +
                "Priority: " + nullToDash(ticket.getPriority()) + "\n\n" +
                ticket.getDescription() + "\n\n" +
                "Ticket ID: " + ticket.getId();
        String replyTo = (ticket.getEmployeeEmail() != null && !ticket.getEmployeeEmail().isBlank()) ? ticket.getEmployeeEmail() : null;

        SendResult result = sendViaResend(supportEmail, replyTo, subject, text, null, null);
        ticket.setEmailSent(result.ok());
        ticket.setEmailFailureReason(result.ok() ? null : result.errorMessage());
        return result.ok();
    }

    private String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
