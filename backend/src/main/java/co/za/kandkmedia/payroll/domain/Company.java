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

    /** IT Support's "I'll be at X office until Y" note, shown to every
     *  employee before they report an Office Issue. Company-wide (not
     *  per-session local state), so it's actually the same for everyone. */
    @Lob
    private String officeAvailabilityNote;
}
