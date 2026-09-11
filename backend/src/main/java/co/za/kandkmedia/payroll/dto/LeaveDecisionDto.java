package co.za.kandkmedia.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LeaveDecisionDto {
    /** PNG data URL from the decider's signature pad — required either way. */
    @NotBlank(message = "Please sign before confirming this decision.")
    private String signature;

    /** Required when declining; ignored when approving. */
    private String reason;
}
