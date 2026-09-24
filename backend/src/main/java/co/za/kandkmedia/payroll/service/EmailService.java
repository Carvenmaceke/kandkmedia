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
 * Actually sends mail — this is not a stub. Two ways to send, picked by
 * MAIL_PROVIDER:
 *
 * <ul>
 *   <li><b>resend</b> (default) — Resend's HTTPS API, described below.</li>
 *   <li><b>smtp</b> — logs in to an ordinary company mailbox (e.g.
 *   payroll@kandkmedia.co.za on mail.kandkmedia.co.za) with SMTP_USERNAME /
 *   SMTP_PASSWORD and sends from it, like Outlook would. Needs no DNS
 *   changes, but the host must allow outbound SMTP (see below).</li>
 * </ul>
 *
 * Resend: sends via Resend's HTTPS API
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

    /** "resend" (default) or "smtp". */
    @Value("${app.mail.provider:resend}")
    private String provider;

    @Value("${app.mail.smtp.host:mail.kandkmedia.co.za}")
    private String smtpHost;
    @Value("${app.mail.smtp.port:465}")
    private int smtpPort;
    /** "ssl" (port 465), "starttls" (port 587) or "none"; blank = decide from the port. */
    @Value("${app.mail.smtp.security:}")
    private String smtpSecurity;
    @Value("${app.mail.smtp.username:}")
    private String smtpUsername;
    @Value("${app.mail.smtp.password:}")
    private String smtpPassword;
    /** Sender address when it isn't the login itself — needed for relay services like Brevo,
     *  whose login (e.g. 8a1b2c001@smtp-brevo.com) isn't a mailbox; must be a sender verified there. */
    @Value("${app.mail.smtp.from:}")
    private String smtpFrom;

    private record SendResult(boolean ok, String errorMessage) {
        static SendResult success() { return new SendResult(true, null); }
        static SendResult failure(String msg) { return new SendResult(false, msg); }
    }

    /** Every email in this class goes through here, whichever provider is configured. */
    private SendResult send(String to, String replyTo, String subject, String textBody, String attachmentFilename, byte[] attachmentBytes) {
        return usingSmtp()
                ? sendViaSmtp(to, replyTo, subject, textBody, attachmentFilename, attachmentBytes)
                : sendViaResend(to, replyTo, subject, textBody, attachmentFilename, attachmentBytes);
    }

    private boolean usingSmtp() {
        return "smtp".equalsIgnoreCase(provider == null ? "" : provider.trim());
    }

    /** The sender address. With SMTP: SMTP_FROM if set (relay services like Brevo), otherwise the
     *  logged-in mailbox (mail servers reject any other From), keeping MAIL_FROM's display name if it
     *  has one. With Resend: MAIL_FROM. */
    private String effectiveFrom() {
        if (usingSmtp()) {
            if (smtpFrom != null && !smtpFrom.isBlank()) {
                return smtpFrom.contains("<") ? smtpFrom.trim() : "K and K Media <" + smtpFrom.trim() + ">";
            }
            if (smtpUsername != null && !smtpUsername.isBlank()) {
                if (fromAddress != null && fromAddress.toLowerCase().contains(smtpUsername.trim().toLowerCase())) return fromAddress;
                return "K and K Media <" + smtpUsername.trim() + ">";
            }
        }
        return fromAddress;
    }

    private SendResult sendViaSmtp(String to, String replyTo, String subject, String textBody, String attachmentFilename, byte[] attachmentBytes) {
        if (smtpUsername == null || smtpUsername.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
            return SendResult.failure("Email is not configured — set SMTP_USERNAME and SMTP_PASSWORD for the company mailbox.");
        }
        try {
            org.springframework.mail.javamail.JavaMailSenderImpl sender = new org.springframework.mail.javamail.JavaMailSenderImpl();
            sender.setHost(smtpHost);
            sender.setPort(smtpPort);
            sender.setUsername(smtpUsername);
            sender.setPassword(smtpPassword);
            sender.setDefaultEncoding("UTF-8");
            String security = smtpSecurity == null || smtpSecurity.isBlank()
                    ? (smtpPort == 465 ? "ssl" : "starttls") // 587 and 2525 (e.g. Brevo) use STARTTLS
                    : smtpSecurity.trim().toLowerCase();
            java.util.Properties props = sender.getJavaMailProperties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.connectiontimeout", "15000");
            props.put("mail.smtp.timeout", "20000");
            props.put("mail.smtp.writetimeout", "20000");
            if (security.equals("ssl")) {
                props.put("mail.smtp.ssl.enable", "true");
            } else if (security.equals("starttls")) {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }

            jakarta.mail.internet.MimeMessage message = sender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(message, attachmentBytes != null, "UTF-8");
            helper.setFrom(effectiveFrom());
            helper.setTo(to);
            if (replyTo != null && !replyTo.isBlank()) helper.setReplyTo(replyTo);
            helper.setSubject(subject);
            helper.setText(textBody, false);
            if (attachmentBytes != null && attachmentFilename != null) {
                helper.addAttachment(attachmentFilename, new org.springframework.core.io.ByteArrayResource(attachmentBytes), "application/pdf");
            }
            sender.send(message);
            return SendResult.success();
        } catch (org.springframework.mail.MailAuthenticationException e) {
            log.error("SMTP login failed for {}", smtpUsername, e);
            return SendResult.failure("The mail server rejected the login for " + smtpUsername + " — check SMTP_USERNAME / SMTP_PASSWORD.");
        } catch (Exception e) {
            log.error("Failed to send email via SMTP ({}:{})", smtpHost, smtpPort, e);
            if (isConnectionProblem(e)) {
                return SendResult.failure("Couldn't connect to " + smtpHost + " on port " + smtpPort
                        + ". The server's host may be blocking outgoing mail ports (Render does on some plans) — "
                        + "use a relay that listens on port 2525 (e.g. Brevo: SMTP_HOST=smtp-relay.brevo.com, SMTP_PORT=2525), "
                        + "or switch MAIL_PROVIDER back to resend. (" + rootMessage(e) + ")");
            }
            return SendResult.failure("SMTP error: " + rootMessage(e));
        }
    }

    private static boolean isConnectionProblem(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.net.ConnectException || t instanceof java.net.SocketTimeoutException
                    || t instanceof java.net.UnknownHostException || t instanceof jakarta.mail.MessagingException && String.valueOf(t.getMessage()).startsWith("Couldn't connect")) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private SendResult sendViaResend(String to, String replyTo, String subject, String textBody, String attachmentFilename, byte[] attachmentBytes) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            return SendResult.failure("Email is not configured — no Resend API key set (MAIL_PASSWORD).");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("from", effectiveFrom());
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

        SendResult result = send(employee.getEmail(), null, subject, text, filename, pdf);
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

        SendResult result = send(employee.getEmail(), null, subject, text, filename, pdf);
        request.setLetterEmailSent(result.ok());
        request.setLetterEmailFailureReason(result.ok() ? null : result.errorMessage());
        return result.ok();
    }

    /**
     * Sends the 6-digit code a new signup needs to confirm they actually
     * own the company email address they signed up with, before their
     * account can log in. Same sendViaResend path as every other email
     * this service sends — no separate mail configuration to maintain.
     */
    public boolean sendVerificationCode(String toEmail, String firstName, String code) {
        return verificationCodeSendError(toEmail, firstName, code) == null;
    }

    /** Same as sendVerificationCode, but returns why sending failed (null on success). */
    public String verificationCodeSendError(String toEmail, String firstName, String code) {
        String subject = "Verify your K and K Media account";
        String text = "Hi " + firstName + ",\n\n" +
                "Your verification code is: " + code + "\n\n" +
                "Enter this code to finish creating your account. It expires in 15 minutes.\n\n" +
                "If you didn't try to sign up, you can ignore this email.\n\n" +
                "Regards,\nK and K Media";
        SendResult result = send(toEmail, null, subject, text, null, null);
        return result.ok() ? null : (result.errorMessage() == null ? "unknown error" : result.errorMessage());
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

        SendResult result = send(supportEmail, replyTo, subject, text, null, null);
        ticket.setEmailSent(result.ok());
        ticket.setEmailFailureReason(result.ok() ? null : result.errorMessage());
        return result.ok();
    }

    /**
     * Sends a physical/on-site office issue (hardware, network, printer,
     * equipment) straight to the support inbox, same direct-send contract
     * as sendSupportRequest — the office location is included prominently
     * since that's the detail IT support actually needs to act on it.
     */
    public boolean sendOfficeIssue(co.za.kandkmedia.payroll.domain.OfficeIssue issue) {
        String subject = "[Office: " + nullToDash(issue.getOffice()) + "] [" + nullToDash(issue.getPriority()) + "] " + nullToDash(issue.getCategory()) + ": " + issue.getSubject();
        String text =
                "Office: " + nullToDash(issue.getOffice()) + "\n" +
                "Employee: " + issue.getEmployeeName() + " (" + nullToDash(issue.getEmployeeCode()) + ")\n" +
                "Role: " + nullToDash(issue.getRole()) + "\n" +
                "Department: " + nullToDash(issue.getDepartment()) + "\n" +
                "Issue Type: " + nullToDash(issue.getCategory()) + "\n" +
                "Priority: " + nullToDash(issue.getPriority()) + "\n\n" +
                issue.getDescription() + "\n\n" +
                "Issue ID: " + issue.getId();
        String replyTo = (issue.getEmployeeEmail() != null && !issue.getEmployeeEmail().isBlank()) ? issue.getEmployeeEmail() : null;

        SendResult result = send(supportEmail, replyTo, subject, text, null, null);
        issue.setEmailSent(result.ok());
        issue.setEmailFailureReason(result.ok() ? null : result.errorMessage());
        return result.ok();
    }

    private String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    /** The outcome of a diagnostic send — errorMessage carries Resend's own response body
     *  verbatim (e.g. "domain not verified", "invalid API key") when ok is false, so a
     *  misconfiguration is diagnosable from the API response itself, not just server logs. */
    public record EmailSendResult(boolean ok, String errorMessage) {}

    /**
     * Sends a real email through the exact same Resend path every other
     * email in this app uses, to the caller's own address — lets whoever's
     * testing confirm mail is actually configured (a real API key, a
     * verified sending domain) without needing Render log access, and see
     * Resend's exact error if it isn't. Never sends to an address other
     * than the authenticated caller's own, so it can't be used to spam
     * anyone else.
     */
    public EmailSendResult sendTestEmail(String toEmail) {
        String subject = "K and K Media — test email";
        String text = "This confirms the payroll system's email delivery (via " + (usingSmtp() ? "the company mailbox " + smtpUsername : "Resend") + ") is working.\n\n" +
                "Sent at " + java.time.LocalDateTime.now() + ".";
        SendResult result = send(toEmail, null, subject, text, null, null);
        return new EmailSendResult(result.ok(), result.errorMessage());
    }
}
