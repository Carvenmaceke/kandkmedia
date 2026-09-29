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
        void relayLoginSendsAsTheVerifiedSenderAddress() throws Exception {
            EmailService svc = smtpService("s3cret");
            ReflectionTestUtils.setField(svc, "smtpFrom", "support@kandkmedia.co.za");

            assertThat(svc.sendTestEmail("someone@insideeducation.co.za").ok()).isTrue();
            assertThat(mailServer.getReceivedMessages()[0].getFrom()[0].toString())
                    .isEqualTo("K and K Media <support@kandkmedia.co.za>");
        }

        @Test
        void wrongPasswordGivesAClearMessage() {
            EmailService.EmailSendResult result = smtpService("wrong").sendTestEmail("someone@kandkmedia.co.za");
            assertThat(result.ok()).isFalse();
            assertThat(result.errorMessage()).contains("rejected the login");
        }

        @Test
        void straySpacesAndQuotesInPastedCredentialsAreIgnored() {
            EmailService svc = smtpService("  \"s3cret\"\n");
            ReflectionTestUtils.setField(svc, "smtpUsername", " payroll@kandkmedia.co.za \n");
            assertThat(svc.sendTestEmail("someone@kandkmedia.co.za").ok()).isTrue();
        }

        @Test
        void brevoLoginFailureNamesTheLikelyCause() {
            EmailService svc = smtpService("xkeysib-abc");
            ReflectionTestUtils.setField(svc, "smtpHost", "smtp-relay.brevo.com");
            assertThat(ReflectionTestUtils.<String>invokeMethod(svc, "loginFailureMessage")).contains("API key").contains("xsmtpsib-");
            ReflectionTestUtils.setField(svc, "smtpPassword", "xsmtpsib-old");
            assertThat(ReflectionTestUtils.<String>invokeMethod(svc, "loginFailureMessage")).contains("Brevo rejected the login").contains("activated");
        }

        @Test
        void unreachableServerExplainsPossiblePortBlocking() {
            EmailService svc = smtpService("s3cret");
            ReflectionTestUtils.setField(svc, "smtpPort", 1); // nothing listens here
            EmailService.EmailSendResult result = svc.sendTestEmail("someone@kandkmedia.co.za");
            assertThat(result.ok()).isFalse();
            assertThat(result.errorMessage()).contains("Couldn't connect").contains("blocking outgoing mail ports").contains("2525");
        }

        @Test
        void missingCredentialsSaySoInsteadOfTryingToSend() {
            EmailService svc = smtpService("");
            assertThat(svc.sendTestEmail("someone@kandkmedia.co.za").errorMessage()).contains("SMTP_USERNAME and SMTP_PASSWORD");
        }
    }

    /** Brevo HTTPS API sending, against a local stand-in for api.brevo.com. */
    @org.junit.jupiter.api.Nested
    class ViaBrevoApi {
        private com.sun.net.httpserver.HttpServer server;
        private final java.util.List<String> bodies = new java.util.ArrayList<>();
        private final java.util.List<String> keys = new java.util.ArrayList<>();
        private int status = 201;
        private String reply = "{\"messageId\":\"<1@brevo>\"}";

        @org.junit.jupiter.api.BeforeEach
        void start() throws Exception {
            server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v3/smtp/email", ex -> {
                bodies.add(new String(ex.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                keys.add(ex.getRequestHeaders().getFirst("api-key"));
                byte[] out = reply.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ex.sendResponseHeaders(status, out.length);
                ex.getResponseBody().write(out);
                ex.close();
            });
            server.start();
        }

        @org.junit.jupiter.api.AfterEach
        void stop() { server.stop(0); }

        private EmailService brevo(String key) {
            EmailService svc = new EmailService(Mockito.mock(PayslipPdfService.class), Mockito.mock(LeaveLetterPdfService.class));
            ReflectionTestUtils.setField(svc, "provider", "brevo");
            ReflectionTestUtils.setField(svc, "brevoApiKey", key);
            ReflectionTestUtils.setField(svc, "brevoUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email");
            ReflectionTestUtils.setField(svc, "smtpFrom", "support@kandkmedia.co.za");
            return svc;
        }

        @Test
        void sendsTheVerificationCodeFromTheVerifiedSender() {
            assertThat(brevo(" xkeysib-abc \n").verificationCodeSendError("thabo@insideeducation.co.za", "Thabo", "654321")).isNull();
            assertThat(keys).containsExactly("xkeysib-abc");
            assertThat(bodies.get(0)).contains("\"email\":\"support@kandkmedia.co.za\"").contains("\"name\":\"K and K Media\"")
                    .contains("thabo@insideeducation.co.za").contains("654321");
        }

        @Test
        void explainsARejectedKey() {
            status = 401; reply = "{\"code\":\"unauthorized\",\"message\":\"Key not found\"}";
            EmailService.EmailSendResult r = brevo("xkeysib-bad").sendTestEmail("someone@kandkmedia.co.za");
            assertThat(r.ok()).isFalse();
            assertThat(r.errorMessage()).contains("Key not found").contains("API Keys");
        }

        @Test
        void passesBrevosOwnReasonThrough() {
            status = 400; reply = "{\"code\":\"invalid_parameter\",\"message\":\"Sender support@kandkmedia.co.za is not valid\"}";
            assertThat(brevo("xkeysib-abc").sendTestEmail("someone@kandkmedia.co.za").errorMessage()).contains("is not valid");
        }

        @Test
        void catchesAnSmtpKeyInTheApiKeySlot() {
            assertThat(brevo("xsmtpsib-abc").sendTestEmail("someone@kandkmedia.co.za").errorMessage()).contains("needs an API key");
            assertThat(bodies).isEmpty();
        }
    }

    /** EmailJS REST API sending, against a local stand-in for api.emailjs.com. */
    @org.junit.jupiter.api.Nested
    class ViaEmailJs {
        private com.sun.net.httpserver.HttpServer server;
        private final java.util.List<String> bodies = new java.util.ArrayList<>();
        private final java.util.List<String> brevoBodies = new java.util.ArrayList<>();
        private int status = 200;
        private String reply = "OK";

        @org.junit.jupiter.api.BeforeEach
        void start() throws Exception {
            server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1.0/email/send", ex -> respond(ex, bodies, status, reply));
            server.createContext("/v3/smtp/email", ex -> respond(ex, brevoBodies, 201, "{}"));
            server.start();
        }

        private void respond(com.sun.net.httpserver.HttpExchange ex, java.util.List<String> sink, int code, String text) throws java.io.IOException {
            sink.add(new String(ex.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] out = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.sendResponseHeaders(code, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        }

        @org.junit.jupiter.api.AfterEach
        void stop() { server.stop(0); }

        private EmailService emailJs() {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            EmailService svc = new EmailService(Mockito.mock(PayslipPdfService.class), Mockito.mock(LeaveLetterPdfService.class));
            ReflectionTestUtils.setField(svc, "provider", "emailjs");
            ReflectionTestUtils.setField(svc, "emailJsServiceId", "service_nkapwdh");
            ReflectionTestUtils.setField(svc, "emailJsTemplateId", "template_x");
            ReflectionTestUtils.setField(svc, "emailJsPublicKey", " pub ");
            ReflectionTestUtils.setField(svc, "emailJsPrivateKey", "priv\n");
            ReflectionTestUtils.setField(svc, "emailJsUrl", base + "/api/v1.0/email/send");
            ReflectionTestUtils.setField(svc, "brevoUrl", base + "/v3/smtp/email");
            ReflectionTestUtils.setField(svc, "supportEmail", "itsupport@kandkmedia.co.za");
            ReflectionTestUtils.setField(svc, "fromAddress", "support@kandkmedia.co.za");
            return svc;
        }

        @Test
        void sendsTheVerificationCodeThroughTheTemplate() {
            assertThat(emailJs().verificationCodeSendError("thabo@insideeducation.co.za", "Thabo", "112233")).isNull();
            assertThat(bodies.get(0)).contains("\"service_id\":\"service_nkapwdh\"").contains("\"template_id\":\"template_x\"")
                    .contains("\"user_id\":\"pub\"").contains("\"accessToken\":\"priv\"")
                    .contains("\"to_email\":\"thabo@insideeducation.co.za\"").contains("112233");
        }

        @Test
        void payslipEmailIsANoticeWithoutThePdf() {
            EmailService svc = emailJs();
            ReflectionTestUtils.setField(svc, "portalUrl", "https://carvenmaceke.github.io/kandkmedia/");
            co.za.kandkmedia.payroll.domain.Employee e = co.za.kandkmedia.payroll.domain.Employee.builder()
                    .firstName("Thabo").lastName("M").email("thabo@kandkmedia.co.za").employeeCode("EMP-00001").build();
            co.za.kandkmedia.payroll.domain.Payroll p = co.za.kandkmedia.payroll.domain.Payroll.builder().employee(e).payPeriod("2026-09").build();

            assertThat(svc.sendPayslip(p)).isTrue();
            assertThat(p.isEmailSent()).isTrue();
            assertThat(bodies.get(0)).contains("Your September 2026 payslip is available")
                    .contains("available to download").contains("https://carvenmaceke.github.io/kandkmedia/")
                    .contains("My Payslips").doesNotContain("couldn't be attached");
            assertThat(brevoBodies).isEmpty();
        }

        @Test
        void explainsTheNonBrowserSetting() {
            status = 403; reply = "API calls are disabled for non-browser applications";
            assertThat(emailJs().sendTestEmail("someone@kandkmedia.co.za").errorMessage()).contains("Allow EmailJS API for non-browser applications");
        }

        @Test
        void missingSettingsAreNamed() {
            EmailService svc = emailJs();
            ReflectionTestUtils.setField(svc, "emailJsTemplateId", "");
            assertThat(svc.sendTestEmail("someone@kandkmedia.co.za").errorMessage()).contains("EMAILJS_TEMPLATE_ID");
        }

        @Test
        void attachmentsGoThroughBrevoWhenItIsConfigured() {
            EmailService svc = emailJs();
            ReflectionTestUtils.setField(svc, "brevoApiKey", "xkeysib-abc");
            Object r = ReflectionTestUtils.invokeMethod(svc, "send", "a@kandkmedia.co.za", null, "Payslip", "Attached", "p.pdf", new byte[]{1, 2});
            assertThat(r.toString()).contains("ok=true");
            assertThat(bodies).isEmpty();
            assertThat(brevoBodies).hasSize(1);
        }

        @Test
        void attachmentsWithoutBrevoAreSentWithoutThePdfAndPointToTheApp() {
            Object r = ReflectionTestUtils.invokeMethod(emailJs(), "send", "a@kandkmedia.co.za", null, "Payslip", "Your payslip.", "p.pdf", new byte[]{1, 2});
            assertThat(r.toString()).contains("ok=true");
            assertThat(bodies).hasSize(1);
            assertThat(bodies.get(0)).contains("Your payslip.").contains("p.pdf").contains("Payroll & Leave");
        }
    }
}
