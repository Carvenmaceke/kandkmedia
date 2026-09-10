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

    @Value("${spring.mail.username:payroll@kandkmedia.co.za}")
    private String fromAddress;

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
}
