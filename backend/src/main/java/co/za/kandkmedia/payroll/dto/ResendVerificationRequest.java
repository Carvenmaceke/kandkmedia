package co.za.kandkmedia.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResendVerificationRequest {
    @NotBlank
    private String email;
}
