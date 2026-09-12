package co.za.kandkmedia.payroll.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** System-generated, human-facing code, e.g. EMP-00001. Unique, never reused. */
    @Column(nullable = false, unique = true)
    private String employeeCode;

    // --- Personal Information (from the employee onboarding form) ---
    private String title; // Mr / Mrs / Ms / Dr / etc.

    @Column(nullable = false)
    private String firstName;

    /** The form's "Second Name" (a middle name), distinct from lastName. */
    private String secondName;

    @Column(nullable = false)
    private String lastName;

    private String initials;
    private LocalDate dateOfBirth;
    private String idNumber;
    private String passportNumber;
    private String passportCountry;

    /** Stated by the employee themselves, for EE/BEE reporting purposes only. */
    private String race;
    private String relationshipStatus;

    private String contactTelephone;
    private String contactCellphone;
    private String emergencyContactName;
    private String emergencyContactTelephone;
    private String emergencyContactCellphone;

    // --- Tax ---
    private String taxOffice;
    private String incomeTaxNumber;

    // --- Banking details ---
    private String bankAccountType;
    private String bankBranchCode;
    private String bankName;
    private String bankBranchName;
    private String bankAccountNumber;
    private String bankAccountHolder;
    private String bankAccountRelationship; // e.g. "Self", "Joint"

    // --- Residential address ---
    private String resUnitNumber;
    private String resComplexName;
    private String resStreetNumber;
    private String resStreetName;
    private String resSuburb;
    private String resCity;
    private String resPostalCode;

    // --- Postal address (can differ from residential, e.g. a PO Box) ---
    private String postalService;
    private String postalNumber;
    private String postStreetNumber;
    private String postStreetName;
    private String postSuburb;
    private String postCity;
    private String postPostalCode;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;
    private String position; // "Job Title" on the form
    private String employmentType; // e.g. Full-time, Part-time, Contract

    /** "Hourly" or "Monthly" — how `salary` below should be read. */
    @Builder.Default
    private String rateType = "Monthly";

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne
    @JoinColumn(name = "level_id")
    private EmployeeLevel level;

    @Column(nullable = false)
    private BigDecimal salary;

    private LocalDate startDate; // "Commencement Date" on the form

    // --- Signup consent + signature, used to generate the HR-downloadable
    // onboarding document (see OnboardingDocumentPdfService) ---
    @Builder.Default
    private boolean agreedToTerms = false;
    private java.time.LocalDateTime termsAgreedAt;

    @Lob
    private String onboardingSignature; // PNG data URL from the signup signature pad

    @ManyToOne
    @JoinColumn(name = "manager_id")
    private Employee manager;

    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
