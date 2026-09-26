package co.za.kandkmedia.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank
    private String email;

    @NotBlank
    private String code;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;
}
