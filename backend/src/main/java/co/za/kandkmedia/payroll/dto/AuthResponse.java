package co.za.kandkmedia.payroll.dto;

import co.za.kandkmedia.payroll.domain.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private Role role;
    private String employeeCode;
    private String fullName;
    private String email;
}
