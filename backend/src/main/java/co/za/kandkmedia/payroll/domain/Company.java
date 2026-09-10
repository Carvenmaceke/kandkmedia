package co.za.kandkmedia.payroll.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String registrationNumber;
    private String address;
    private String email;
    private String phone;
    private String website;
    private String logoUrl;
}
