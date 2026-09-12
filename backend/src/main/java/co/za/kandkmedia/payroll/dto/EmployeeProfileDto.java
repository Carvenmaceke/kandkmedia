package co.za.kandkmedia.payroll.dto;

import lombok.Data;

import java.time.LocalDate;

/** All fields optional — only non-null ones are applied (partial update). */
@Data
public class EmployeeProfileDto {
    private String title;
    private String firstName;
    private String secondName;
    private String lastName;
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

    private String postalService;
    private String postalNumber;
    private String postStreetNumber;
    private String postStreetName;
    private String postSuburb;
    private String postCity;
    private String postPostalCode;

    private String phone;
    private String position;
    private String office;
    private String employmentType;
    private String rateType;
    private java.math.BigDecimal salary;

    /** The employeeCode (e.g. EMP-00005) of the employee's manager — not a
     *  raw numeric id, since that's what HR/Master actually has on hand in
     *  the UI. Empty string clears the manager relationship. */
    private String managerEmployeeCode;
}
