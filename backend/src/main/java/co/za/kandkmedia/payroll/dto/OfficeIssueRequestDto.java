package co.za.kandkmedia.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OfficeIssueRequestDto {
    @NotBlank
    private String employeeName;
    private String employeeCode;
    private String employeeEmail;
    private String role;
    private String department;

    @NotBlank(message = "Please select an office.")
    private String office;

    @NotBlank(message = "Please add a subject.")
    private String subject;

    private String category;
    private String priority;

    @NotBlank(message = "Please describe the problem.")
    private String description;
}
