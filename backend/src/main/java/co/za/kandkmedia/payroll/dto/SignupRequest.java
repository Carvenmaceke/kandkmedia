package co.za.kandkmedia.payroll.dto;

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

    // No role field — every public signup becomes Role.EMPLOYEE, enforced
    // server-side in AuthService, never trusting a client-supplied value.
    // HR/Admin/IT Support/Manager access is granted afterward, exclusively
    // by the Master account, via PUT /api/admin/users/{id}/role.

    private String phone;
    private String position;
    private String department;
    private String office;
    // Level is no longer chosen at signup — HR assigns salary directly per
    // employee afterwards (see EmployeeProfileService / HrController).

    @NotNull(message = "Please agree to the Terms & Conditions to continue.")
    @jakarta.validation.constraints.AssertTrue(message = "Please agree to the Terms & Conditions to continue.")
    private Boolean agreedToTerms;

    @NotBlank(message = "Please sign before creating your account.")
    private String signature; // PNG data URL from the signup signature pad

    // --- Onboarding info — optional at signup now; HR fills this in for
    // the employee afterward via PUT /api/hr/employees/{id}/profile once
    // their account exists. idNumber vs passportNumber+passportCountry is
    // no longer enforced here either, for the same reason. ---
    private String title;
    private String secondName;
    private String initials;
    private LocalDate dateOfBirth;
    private String idNumber;
    private String passportNumber;
    private String passportCountry;
    private String race;
    private String relationshipStatus;

    private String contactTelephone;
    private String contactCellphone;
    private String emergencyContactName;
    private String emergencyContactTelephone;
    private String emergencyContactCellphone;

    private String taxOffice;
    private String incomeTaxNumber;

    private String bankAccountType;
    private String bankBranchCode;
    private String bankName;
    private String bankBranchName;
    private String bankAccountNumber;
    private String bankAccountHolder;
    private String bankAccountRelationship;

    private String resUnitNumber;
    private String resComplexName;
    private String resStreetNumber;
    private String resStreetName;
    private String resSuburb;
    private String resCity;
    private String resPostalCode;

    // Postal address: AuthService copies the residential address across
    // when these are left blank, mirroring the frontend's "same as
    // residential" checkbox — unchanged, just no longer required either.
    private String postalService;
    private String postalNumber;
    private String postStreetNumber;
    private String postStreetName;
    private String postSuburb;
    private String postCity;
    private String postPostalCode;
}
