package co.za.kandkmedia.payroll.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * sendTestEmail is the diagnostic path an admin uses to check whether mail
 * delivery is actually configured in a given environment. The "no API key"
 * case is the one worth covering directly — it's deterministic and doesn't
 * need a real network call — since it's also the single most common reason
 * mail silently doesn't work: MAIL_PASSWORD (the Resend API key) missing
 * from that environment's configuration.
 */
class EmailServiceTest {

    private final EmailService emailService = new EmailService(
            Mockito.mock(PayslipPdfService.class), Mockito.mock(LeaveLetterPdfService.class));

    @Test
    void sendTestEmailFailsClearlyWhenNoApiKeyIsConfigured() {
        ReflectionTestUtils.setField(emailService, "resendApiKey", "");

        EmailService.EmailSendResult result = emailService.sendTestEmail("someone@kandkmedia.co.za");

        assertThat(result.ok()).isFalse();
        assertThat(result.errorMessage()).contains("MAIL_PASSWORD");
    }

    /** Company-mailbox (SMTP) sending, against a real local SMTP server. */
    @org.junit.jupiter.api.Nested
    class ViaSmtp {
        @org.junit.jupiter.api.extension.RegisterExtension
        static com.icegreen.greenmail.junit5.GreenMailExtension mailServer =
                new com.icegreen.greenmail.junit5.GreenMailExtension(com.icegreen.greenmail.util.ServerSetupTest.SMTP)
                        .withConfiguration(com.icegreen.greenmail.configuration.GreenMailConfiguration.aConfig()
                                .withUser("payroll@kandkmedia.co.za", "payroll@kandkmedia.co.za", "s3cret"));

        private EmailService smtpService(String password) {
            EmailService svc = new EmailService(Mockito.mock(PayslipPdfService.class), Mockito.mock(LeaveLetterPdfService.class));
            ReflectionTestUtils.setField(svc, "provider", "smtp");
            ReflectionTestUtils.setField(svc, "smtpHost", "127.0.0.1");
            ReflectionTestUtils.setField(svc, "smtpPort", mailServer.getSmtp().getPort());
            ReflectionTestUtils.setField(svc, "smtpSecurity", "none");
            ReflectionTestUtils.setField(svc, "smtpUsername", "payroll@kandkmedia.co.za");
            ReflectionTestUtils.setField(svc, "smtpPassword", password);
            ReflectionTestUtils.setField(svc, "fromAddress", "payroll@updates.kandkmedia.co.za");
            return svc;
        }

        @Test
        void sendsVerificationCodeFromTheCompanyMailbox() throws Exception {
            String error = smtpService("s3cret").verificationCodeSendError("thabo@insideeducation.co.za", "Thabo", "123456");

            assertThat(error).isNull();
            jakarta.mail.internet.MimeMessage[] received = mailServer.getReceivedMessages();
            assertThat(received).hasSize(1);
            assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("thabo@insideeducation.co.za");
            assertThat(received[0].getFrom()[0].toString()).contains("payroll@kandkmedia.co.za");
            assertThat(com.icegreen.greenmail.util.GreenMailUtil.getBody(received[0])).contains("123456");
        }

        @Test
        void wrongPasswordGivesAClearMessage() {
            EmailService.EmailSendResult result = smtpService("wrong").sendTestEmail("someone@kandkmedia.co.za");
            assertThat(result.ok()).isFalse();
            assertThat(result.errorMessage()).contains("rejected the login");
        }

        @Test
        void unreachableServerExplainsPossiblePortBlocking() {
            EmailService svc = smtpService("s3cret");
            ReflectionTestUtils.setField(svc, "smtpPort", 1); // nothing listens here
            EmailService.EmailSendResult result = svc.sendTestEmail("someone@kandkmedia.co.za");
            assertThat(result.ok()).isFalse();
            assertThat(result.errorMessage()).contains("Couldn't connect").contains("blocking outgoing mail ports");
        }

        @Test
        void missingCredentialsSaySoInsteadOfTryingToSend() {
            EmailService svc = smtpService("");
            assertThat(svc.sendTestEmail("someone@kandkmedia.co.za").errorMessage()).contains("SMTP_USERNAME and SMTP_PASSWORD");
        }
    }
}
