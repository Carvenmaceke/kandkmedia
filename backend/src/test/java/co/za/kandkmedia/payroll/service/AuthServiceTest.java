package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.AppUser;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.Role;
import co.za.kandkmedia.payroll.dto.AuthResponse;
import co.za.kandkmedia.payroll.dto.LoginRequest;
import co.za.kandkmedia.payroll.dto.ResendVerificationRequest;
import co.za.kandkmedia.payroll.dto.SignupRequest;
import co.za.kandkmedia.payroll.dto.VerifyEmailRequest;
import co.za.kandkmedia.payroll.repository.AppUserRepository;
import co.za.kandkmedia.payroll.repository.DepartmentRepository;
import co.za.kandkmedia.payroll.repository.EmployeeRepository;
import co.za.kandkmedia.payroll.repository.LeaveBalanceRepository;
import co.za.kandkmedia.payroll.repository.LeaveTypeRepository;
import co.za.kandkmedia.payroll.security.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the email-verification-code flow added around signup/login: a new
 * account can't log in until it confirms a code sent to its own inbox via
 * the same EmailService every other notification in this app already uses.
 */
class AuthServiceTest {

    private final AppUserRepository userRepository = Mockito.mock(AppUserRepository.class);
    private final EmployeeRepository employeeRepository = Mockito.mock(EmployeeRepository.class);
    private final DepartmentRepository departmentRepository = Mockito.mock(DepartmentRepository.class);
    private final LeaveBalanceRepository leaveBalanceRepository = Mockito.mock(LeaveBalanceRepository.class);
    private final LeaveTypeRepository leaveTypeRepository = Mockito.mock(LeaveTypeRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AuthenticationManager authenticationManager = Mockito.mock(AuthenticationManager.class);
    private final JwtService jwtService = new JwtService();
    private final EmailService emailService = Mockito.mock(EmailService.class);

    private final AuthService authService = new AuthService(
            userRepository, employeeRepository, departmentRepository, leaveBalanceRepository,
            leaveTypeRepository, passwordEncoder, authenticationManager, jwtService, emailService);

    AuthServiceTest() {
        ReflectionTestUtils.setField(authService, "allowedEmailDomain", "kandkmedia.co.za, insideeducation.co.za");
        ReflectionTestUtils.setField(jwtService, "secret", "test-secret-at-least-32-bytes-long-for-hs256");
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3600000L);
        when(leaveTypeRepository.findAll()).thenReturn(List.of());
        when(employeeRepository.nextEmployeeCode()).thenReturn("EMP-00001");
        when(employeeRepository.save(any())).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            if (e.getId() == null) e.setId(1L);
            return e;
        });
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(emailService.verificationCodeSendError(anyString(), anyString(), anyString())).thenReturn(null);
    }

    private SignupRequest signupRequest() {
        SignupRequest req = new SignupRequest();
        req.setFirstName("Jane");
        req.setLastName("Doe");
        req.setEmail("jane@kandkmedia.co.za");
        req.setPassword("password123");
        req.setAgreedToTerms(true);
        req.setSignature("data:image/png;base64,abc");
        return req;
    }

    private AppUser unverifiedUser(String code, LocalDateTime expiresAt) {
        Employee employee = Employee.builder().id(1L).employeeCode("EMP-00001").firstName("Jane").lastName("Doe").build();
        return AppUser.builder()
                .id(1L)
                .email("jane@kandkmedia.co.za")
                .passwordHash(passwordEncoder.encode("password123"))
                .role(Role.EMPLOYEE)
                .employee(employee)
                .emailVerified(false)
                .verificationCode(code)
                .verificationCodeExpiresAt(expiresAt)
                .verificationCodeSentAt(LocalDateTime.now().minusMinutes(5))
                .build();
    }

    @Test
    void signupCreatesAnUnverifiedAccountAndSendsACodeInsteadOfAToken() {
        AuthResponse response = authService.signup(signupRequest());

        assertThat(response.getToken()).isNull();
        assertThat(response.isEmailVerificationRequired()).isTrue();
        assertThat(response.getEmail()).isEqualTo("jane@kandkmedia.co.za");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isEmailVerified()).isFalse();
        assertThat(captor.getValue().getVerificationCode()).matches("\\d{6}");

        verify(emailService).verificationCodeSendError(org.mockito.ArgumentMatchers.eq("jane@kandkmedia.co.za"), anyString(), anyString());
    }

    @Test
    void signupRollsBackWhenTheVerificationEmailFailsToSend() {
        when(emailService.verificationCodeSendError(anyString(), anyString(), anyString())).thenReturn("Resend API error (403): testing emails only");

        assertThatThrownBy(() -> authService.signup(signupRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Couldn't send");
    }

    @Test
    void loginIsBlockedUntilTheAccountIsVerified() {
        AppUser user = unverifiedUser("123456", LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByEmail("jane@kandkmedia.co.za")).thenReturn(Optional.of(user));

        LoginRequest req = new LoginRequest();
        req.setEmail("jane@kandkmedia.co.za");
        req.setPassword("password123");

        AuthResponse response = authService.login(req);

        assertThat(response.getToken()).isNull();
        assertThat(response.isEmailVerificationRequired()).isTrue();
    }

    @Test
    void loginSucceedsNormallyOnceVerified() {
        Employee employee = Employee.builder().id(1L).employeeCode("EMP-00001").firstName("Jane").lastName("Doe").build();
        AppUser user = AppUser.builder()
                .id(1L).email("jane@kandkmedia.co.za")
                .passwordHash(passwordEncoder.encode("password123"))
                .role(Role.EMPLOYEE).employee(employee).emailVerified(true)
                .build();
        when(userRepository.findByEmail("jane@kandkmedia.co.za")).thenReturn(Optional.of(user));

        LoginRequest req = new LoginRequest();
        req.setEmail("jane@kandkmedia.co.za");
        req.setPassword("password123");

        AuthResponse response = authService.login(req);

        assertThat(response.getToken()).isNotBlank();
        assertThat(response.isEmailVerificationRequired()).isFalse();
    }

    @Test
    void loginStillRejectsWrongPasswordBeforeCheckingVerification() {
        doThrow(new BadCredentialsException("bad")).when(authenticationManager).authenticate(any());

        LoginRequest req = new LoginRequest();
        req.setEmail("jane@kandkmedia.co.za");
        req.setPassword("wrong");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Incorrect email or password");
    }

    @Test
    void verifyEmailWithTheCorrectCodeIssuesAToken() {
        AppUser user = unverifiedUser("123456", LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByEmail("jane@kandkmedia.co.za")).thenReturn(Optional.of(user));

        VerifyEmailRequest req = new VerifyEmailRequest();
        req.setEmail("jane@kandkmedia.co.za");
        req.setCode("123456");

        AuthResponse response = authService.verifyEmail(req);

        assertThat(response.getToken()).isNotBlank();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getVerificationCode()).isNull();
    }

    @Test
    void verifyEmailRejectsTheWrongCode() {
        AppUser user = unverifiedUser("123456", LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByEmail("jane@kandkmedia.co.za")).thenReturn(Optional.of(user));

        VerifyEmailRequest req = new VerifyEmailRequest();
        req.setEmail("jane@kandkmedia.co.za");
        req.setCode("000000");

        assertThatThrownBy(() -> authService.verifyEmail(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Incorrect");
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void verifyEmailRejectsAnExpiredCode() {
        AppUser user = unverifiedUser("123456", LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByEmail("jane@kandkmedia.co.za")).thenReturn(Optional.of(user));

        VerifyEmailRequest req = new VerifyEmailRequest();
        req.setEmail("jane@kandkmedia.co.za");
        req.setCode("123456");

        assertThatThrownBy(() -> authService.verifyEmail(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void resendVerificationIssuesANewCodeWhenCooldownHasPassed() {
        AppUser user = unverifiedUser("111111", LocalDateTime.now().plusMinutes(10));
        user.setVerificationCodeSentAt(LocalDateTime.now().minusMinutes(2));
        when(userRepository.findByEmail("jane@kandkmedia.co.za")).thenReturn(Optional.of(user));

        ResendVerificationRequest req = new ResendVerificationRequest();
        req.setEmail("jane@kandkmedia.co.za");

        authService.resendVerification(req);

        assertThat(user.getVerificationCode()).matches("\\d{6}");
        verify(emailService).verificationCodeSendError(org.mockito.ArgumentMatchers.eq("jane@kandkmedia.co.za"), anyString(), anyString());
    }

    @Test
    void resendVerificationIsThrottledWithinTheCooldownWindow() {
        AppUser user = unverifiedUser("111111", LocalDateTime.now().plusMinutes(10));
        user.setVerificationCodeSentAt(LocalDateTime.now().minusSeconds(5));
        when(userRepository.findByEmail("jane@kandkmedia.co.za")).thenReturn(Optional.of(user));

        ResendVerificationRequest req = new ResendVerificationRequest();
        req.setEmail("jane@kandkmedia.co.za");

        assertThatThrownBy(() -> authService.resendVerification(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("wait");
    }

    @Test
    void resendVerificationRejectsAnAlreadyVerifiedAccount() {
        Employee employee = Employee.builder().id(1L).employeeCode("EMP-00001").firstName("Jane").lastName("Doe").build();
        AppUser user = AppUser.builder()
                .id(1L).email("jane@kandkmedia.co.za").passwordHash("hash")
                .role(Role.EMPLOYEE).employee(employee).emailVerified(true)
                .build();
        when(userRepository.findByEmail("jane@kandkmedia.co.za")).thenReturn(Optional.of(user));

        ResendVerificationRequest req = new ResendVerificationRequest();
        req.setEmail("jane@kandkmedia.co.za");

        assertThatThrownBy(() -> authService.resendVerification(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already verified");
    }

    @Test
    void signupAcceptsBothCompanyDomainsAndRejectsOthers() {
        SignupRequest inside = signupRequest();
        inside.setEmail("thabo@insideeducation.co.za");
        assertThat(authService.signup(inside).getEmail()).isEqualTo("thabo@insideeducation.co.za");

        SignupRequest gmail = signupRequest();
        gmail.setEmail("someone@gmail.com");
        assertThatThrownBy(() -> authService.signup(gmail))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("@kandkmedia.co.za or @insideeducation.co.za");
    }

    @Test
    void signupFailureShowsWhyTheEmailDidNotSend() {
        when(emailService.verificationCodeSendError(anyString(), anyString(), anyString())).thenReturn("Resend API error (403): verify a domain");
        assertThatThrownBy(() -> authService.signup(signupRequest())).hasMessageContaining("verify a domain");
    }

    @Test
    void checkEmailReportsDomainAndExistingAccounts() {
        assertThat(authService.checkEmail("new.person@insideeducation.co.za")).containsEntry("ok", true);
        assertThat(authService.checkEmail("x@gmail.com")).containsEntry("ok", false);
        assertThat(authService.checkEmail("not-an-email")).containsEntry("ok", false);
        when(userRepository.existsByEmail("taken@kandkmedia.co.za")).thenReturn(true);
        assertThat(authService.checkEmail("Taken@KandKMedia.co.za ")).containsEntry("ok", false).containsEntry("exists", true);
    }
}
