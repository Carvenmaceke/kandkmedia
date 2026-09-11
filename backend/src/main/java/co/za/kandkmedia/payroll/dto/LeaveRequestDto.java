package co.za.kandkmedia.payroll.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class LeaveRequestDto {
    @NotBlank
    private String leaveType;

    @NotNull
    @FutureOrPresent
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    private String reason;

    /** PNG data URL from the frontend's signature pad — required, same as the UI enforces. */
    @NotBlank(message = "Please sign the application before submitting.")
    private String signature;
}
