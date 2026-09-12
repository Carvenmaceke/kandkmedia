package co.za.kandkmedia.payroll.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Authentication record. Deliberately separate from {@link Employee}: the
 * spec's suggested schema keeps "users" and "employees" as distinct tables,
 * which also means an employee's HR profile can exist even before — or
 * independently of — their login being provisioned.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /**
     * BCrypt hash — never store plain text, and never let this reach a JSON
     * response. @JsonIgnore on both the field and the UserDetails-mandated
     * getPassword() override, since Jackson would otherwise serialize both
     * "passwordHash" (from Lombok's generated getter) and "password" (from
     * getPassword()) as separate JSON properties.
     */
    @JsonIgnore
    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @OneToOne
    @JoinColumn(name = "employee_id", unique = true)
    private Employee employee;

    @Builder.Default
    private boolean enabled = true;

    @JsonIgnore
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @JsonIgnore
    @Override
    public String getPassword() {
        return passwordHash;
    }

    @JsonIgnore
    @Override
    public String getUsername() {
        return email;
    }

    @JsonIgnore
    @Override
    public boolean isAccountNonExpired() { return true; }

    @JsonIgnore
    @Override
    public boolean isAccountNonLocked() { return true; }

    @JsonIgnore
    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @JsonIgnore
    @Override
    public boolean isEnabled() { return enabled; }
}
