package co.za.kandkmedia.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyEmailRequest {
    @NotBlank
    private String email;

    @NotBlank
    private String code;
}
