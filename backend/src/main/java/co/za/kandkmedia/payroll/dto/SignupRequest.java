package co.za.kandkmedia.payroll.dto;

import co.za.kandkmedia.payroll.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignupRequest {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    /**
     * Domain restriction (must end in @kandkmedia.co.za) is enforced in
     * AuthService against the configured app.allowed-email-domain, not here,
     * so it stays configurable without a redeploy.
     */
    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotNull
    private Role role; // EMPLOYEE, HR, or ADMIN — signup never grants MANAGER directly

    private String phone;
    private String position;
    private String department;
    private String level; // only meaningful when role == EMPLOYEE
}
