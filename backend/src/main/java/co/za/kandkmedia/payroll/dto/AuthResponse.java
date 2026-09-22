package co.za.kandkmedia.payroll.dto;

import co.za.kandkmedia.payroll.domain.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AuthResponse {
    /** Null when email verification is still pending — see emailVerificationRequired. */
    private String token;
    private Role role;
    private String employeeCode;
    private String fullName;
    private String email;
    /** True right after signup, and on a login attempt against an unverified account. */
    @Builder.Default
    private boolean emailVerificationRequired = false;
}
