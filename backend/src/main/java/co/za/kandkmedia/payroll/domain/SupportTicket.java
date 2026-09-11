package co.za.kandkmedia.payroll.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "support_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Submitter details are stored as plain fields, not a FK to Employee —
    // this endpoint is intentionally public (see SupportController), so it
    // shouldn't hard-fail just because the submitted details don't exactly
    // match a database row.
    private String employeeName;
    private String employeeCode;
    private String employeeEmail;
    private String role;
    private String department;

    @Column(nullable = false)
    private String subject;

    private String category;
    private String priority;

    @Lob
    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TicketStatus status = TicketStatus.OPEN;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private boolean emailSent = false;
    private String emailFailureReason;
}
