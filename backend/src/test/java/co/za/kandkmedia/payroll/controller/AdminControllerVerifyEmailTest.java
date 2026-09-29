package co.za.kandkmedia.payroll.controller;

import co.za.kandkmedia.payroll.domain.AppUser;
import co.za.kandkmedia.payroll.domain.Role;
import co.za.kandkmedia.payroll.repository.*;
import co.za.kandkmedia.payroll.service.EmailService;
import co.za.kandkmedia.payroll.service.OfficeIssueService;
import co.za.kandkmedia.payroll.service.SupportService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The manual-verify escape hatch for an account whose signup verification
 * email never arrived (mail delivery down) — otherwise permanently stuck:
 * can't log in (unverified) and can't sign up again (email taken).
 */
class AdminControllerVerifyEmailTest {

    private final AppUserRepository userRepository = Mockito.mock(AppUserRepository.class);
    private final AdminController controller = new AdminController(
            Mockito.mock(CompanyRepository.class), Mockito.mock(DepartmentRepository.class),
            Mockito.mock(EmployeeLevelRepository.class), userRepository,
            Mockito.mock(SupportService.class), Mockito.mock(OfficeIssueService.class),
            Mockito.mock(PayrollSettingsRepository.class), Mockito.mock(EmailService.class),
            Mockito.mock(co.za.kandkmedia.payroll.service.AccountCleanupService.class));

    @Test
    void verifiesAnUnverifiedAccountAndClearsItsCode() {
        AppUser user = AppUser.builder()
                .id(5L).email("stuck@kandkmedia.co.za").passwordHash("hash").role(Role.EMPLOYEE)
                .emailVerified(false).verificationCode("123456")
                .verificationCodeExpiresAt(LocalDateTime.now().plusMinutes(10))
                .verificationCodeSentAt(LocalDateTime.now())
                .build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppUser result = controller.verifyUserEmail(5L);

        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getVerificationCode()).isNull();
    }

    @Test
    void verifyingAnAlreadyVerifiedAccountIsANoOp() {
        AppUser user = AppUser.builder()
                .id(6L).email("fine@kandkmedia.co.za").passwordHash("hash").role(Role.EMPLOYEE)
                .emailVerified(true)
                .build();
        when(userRepository.findById(6L)).thenReturn(Optional.of(user));

        AppUser result = controller.verifyUserEmail(6L);

        assertThat(result.isEmailVerified()).isTrue();
        Mockito.verify(userRepository, Mockito.never()).save(any());
    }

    @Test
    void unknownUserIsRejected() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.verifyUserEmail(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }
}
