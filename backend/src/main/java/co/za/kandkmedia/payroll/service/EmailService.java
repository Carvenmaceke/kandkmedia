package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.Payroll;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Actually sends mail — this is not a stub. It needs real SMTP credentials
 * (MAIL_HOST / MAIL_USERNAME / MAIL_PASSWORD env vars, see backend/README.md)
 * to do anything; without them Spring will fail to connect and the
 * exception is caught and recorded on the Payroll row rather than thrown,
 * so a bad mail config doesn't take down the whole request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final PayslipPdfService payslipPdfService;
    private final LeaveLetterPdfService leaveLetterPdfService;

    @Value("${app.mail-from:payroll@kandkmedia.co.za}")
    private String fromAddress;

    @Value("${app.support-email:itsupport@kandkmedia.co.za}")
    private String supportEmail;

    /**
     * Generates the payslip PDF and emails it to the employee. Returns true
     * and stamps emailSent/emailSentAt on success; on failure, returns false
     * and stamps emailFailureReason instead — callers should persist the
     * Payroll row either way so HR can see (and retry) failed sends.
     */
    public boolean sendPayslip(Payroll payroll) {
        Employee employee = payroll.getEmployee();
        try {
            byte[] pdf = payslipPdfService.generate(payroll);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(employee.getEmail());
            helper.setSubject(payroll.getPayPeriod() + " Payslip - " + employee.getFullName());
            helper.setText(
                    "Hi " + employee.getFirstName() + ",\n\n" +
                    "Your payslip for " + payroll.getPayPeriod() + " is attached.\n\n" +
                    "Regards,\nK and K Media Payroll"
            );
            String filename = payroll.getPayPeriod() + "-" + employee.getEmployeeCode() + ".pdf";
            helper.addAttachment(filename, new org.springframework.core.io.ByteArrayResource(pdf));

            mailSender.send(message);

            payroll.setEmailSent(true);
            payroll.setEmailSentAt(LocalDateTime.now());
            payroll.setEmailFailureReason(null);
            return true;

        } catch (MessagingException | MailException e) {
            log.error("Failed to send payslip email for {} ({})", employee.getEmployeeCode(), payroll.getPayPeriod(), e);
            payroll.setEmailSent(false);
            payroll.setEmailFailureReason(e.getMessage());
            return false;
        }
    }

    /**
     * Generates the signed leave decision letter and emails it to the
     * applicant. Same success/failure contract as sendPayslip — stamps
     * letterEmailSent/letterEmailFailureReason on the LeaveRequest rather
     * than throwing, so a bad mail config doesn't block the decision itself
     * from being recorded.
     */
    public boolean sendLeaveLetter(co.za.kandkmedia.payroll.domain.LeaveRequest request) {
        Employee employee = request.getEmployee();
        boolean approved = request.getStatus() == co.za.kandkmedia.payroll.domain.LeaveStatus.APPROVED;
        try {
            byte[] pdf = leaveLetterPdfService.generate(request);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(employee.getEmail());
            helper.setSubject("Your " + request.getLeaveType().getName() + " request has been " + (approved ? "approved" : "declined"));
            String body = "Hi " + employee.getFirstName() + ",\n\n" +
                    "Your " + request.getLeaveType().getName() + " request (" + request.getStartDate() + " to " + request.getEndDate() + ") has been " +
                    (approved ? "approved." : "declined.") +
                    (!approved && request.getDecisionReason() != null ? "\n\nReason: " + request.getDecisionReason() : "") +
                    "\n\nThe signed letter is attached.\n\nRegards,\nK and K Media";
            helper.setText(body);
            String filename = "Leave-" + request.getStatus() + "-" + request.getId() + "-" + employee.getEmployeeCode() + ".pdf";
            helper.addAttachment(filename, new org.springframework.core.io.ByteArrayResource(pdf));

            mailSender.send(message);

            request.setLetterEmailSent(true);
            request.setLetterEmailFailureReason(null);
            return true;

        } catch (MessagingException | MailException e) {
            log.error("Failed to send leave decision letter for request {} ({})", request.getId(), employee.getEmployeeCode(), e);
            request.setLetterEmailSent(false);
            request.setLetterEmailFailureReason(e.getMessage());
            return false;
        }
    }

    /**
     * Sends a support ticket straight to the support inbox — this is the
     * "press Send, nothing opens" path: the browser calls the backend, the
     * backend sends the mail server-side. No mailto:, no user email client
     * involved. Same catch-and-record-the-failure pattern as the other
     * send methods.
     */
    public boolean sendSupportRequest(co.za.kandkmedia.payroll.domain.SupportTicket ticket) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(supportEmail);
            if (ticket.getEmployeeEmail() != null && !ticket.getEmployeeEmail().isBlank()) {
                helper.setReplyTo(ticket.getEmployeeEmail());
            }
            helper.setSubject("[" + nullToDash(ticket.getPriority()) + "] " + nullToDash(ticket.getCategory()) + ": " + ticket.getSubject());
            String body =
                    "Employee: " + ticket.getEmployeeName() + " (" + nullToDash(ticket.getEmployeeCode()) + ")\n" +
                    "Role: " + nullToDash(ticket.getRole()) + "\n" +
                    "Department: " + nullToDash(ticket.getDepartment()) + "\n" +
                    "Category: " + nullToDash(ticket.getCategory()) + "\n" +
                    "Priority: " + nullToDash(ticket.getPriority()) + "\n\n" +
                    ticket.getDescription() + "\n\n" +
                    "Ticket ID: " + ticket.getId();
            helper.setText(body);

            mailSender.send(message);

            ticket.setEmailSent(true);
            ticket.setEmailFailureReason(null);
            return true;

        } catch (MessagingException | MailException e) {
            log.error("Failed to send support request {} ({})", ticket.getId(), ticket.getEmployeeCode(), e);
            ticket.setEmailSent(false);
            ticket.setEmailFailureReason(e.getMessage());
            return false;
        }
    }

    private String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
