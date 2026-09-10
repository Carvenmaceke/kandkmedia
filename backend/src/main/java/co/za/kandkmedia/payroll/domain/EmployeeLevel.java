package co.za.kandkmedia.payroll.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "employee_levels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // e.g. Intern, Junior, Mid-Level, Senior, Manager

    private BigDecimal defaultSalary;
    private BigDecimal minSalary;
    private BigDecimal maxSalary;
}
