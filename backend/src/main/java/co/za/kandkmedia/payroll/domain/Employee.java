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

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String idNumber;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;
    private String position;
    private String employmentType; // e.g. Full-time, Part-time, Contract

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne
    @JoinColumn(name = "level_id")
    private EmployeeLevel level;

    @Column(nullable = false)
    private BigDecimal salary;

    private LocalDate startDate;

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
