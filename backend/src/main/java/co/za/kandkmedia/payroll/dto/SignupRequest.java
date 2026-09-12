package co.za.kandkmedia.payroll.dto;

import co.za.kandkmedia.payroll.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

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
    private Role role; // EMPLOYEE, HR, ADMIN, or IT_SUPPORT — signup never grants MANAGER directly

    private String phone;
    private String position;
    private String department;
    // Level is no longer chosen at signup — HR assigns salary directly per
    // employee afterwards (see EmployeeProfileService / HrController).

    @NotNull(message = "Please agree to the Terms & Conditions to continue.")
    @jakarta.validation.constraints.AssertTrue(message = "Please agree to the Terms & Conditions to continue.")
    private Boolean agreedToTerms;

    @NotBlank(message = "Please sign before creating your account.")
    private String signature; // PNG data URL from the signup signature pad

    // --- Onboarding info, required at signup (matches the frontend form) ---
    // idNumber vs passportNumber+passportCountry: exactly one identity
    // document path is enforced in AuthService, not here, since a
    // NotBlank on both fields would wrongly force every field.
    @NotBlank
    private String title;
    private String secondName;
    @NotBlank
    private String initials;
    @NotNull
    private LocalDate dateOfBirth;
    private String idNumber;
    private String passportNumber;
    private String passportCountry;
    @NotBlank
    private String race;
    @NotBlank
    private String relationshipStatus;

    @NotBlank
    private String contactTelephone;
    @NotBlank
    private String contactCellphone;
    @NotBlank
    private String emergencyContactName;
    @NotBlank
    private String emergencyContactTelephone;
    private String emergencyContactCellphone;

    @NotBlank
    private String taxOffice;
    @NotBlank
    private String incomeTaxNumber;

    @NotBlank
    private String bankAccountType;
    @NotBlank
    private String bankBranchCode;
    @NotBlank
    private String bankName;
    private String bankBranchName;
    @NotBlank
    private String bankAccountNumber;
    @NotBlank
    private String bankAccountHolder;
    private String bankAccountRelationship;

    private String resUnitNumber;
    private String resComplexName;
    @NotBlank
    private String resStreetNumber;
    @NotBlank
    private String resStreetName;
    @NotBlank
    private String resSuburb;
    @NotBlank
    private String resCity;
    @NotBlank
    private String resPostalCode;

    // Postal address: optional at the DTO level — AuthService copies the
    // residential address across when these are left blank, mirroring the
    // frontend's "same as residential" checkbox.
    private String postalService;
    private String postalNumber;
    private String postStreetNumber;
    private String postStreetName;
    private String postSuburb;
    private String postCity;
    private String postPostalCode;
}
