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
}
