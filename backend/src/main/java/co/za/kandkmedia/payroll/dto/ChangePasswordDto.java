package co.za.kandkmedia.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordDto {
    @NotBlank(message = "Please enter your current password.")
    private String currentPassword;

    @NotBlank(message = "Please enter a new password.")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;
}
