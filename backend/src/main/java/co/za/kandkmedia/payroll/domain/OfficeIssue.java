package co.za.kandkmedia.payroll.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Physical/on-site issues (hardware, network, printers, equipment) at a
 * specific office — deliberately separate from SupportTicket, which is for
 * issues within the system itself (payslips, leave, bugs). Same shape and
 * lifecycle otherwise: public submission, saved first then emailed, staff
 * update status/response afterward.
 */
@Entity
@Table(name = "office_issues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfficeIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String employeeName;
    private String employeeCode;
    private String employeeEmail;
    private String role;
    private String department;

    @Column(nullable = false)
    private String office; // "Midrand" or "Sandton"

    @Column(nullable = false)
    private String subject;

    private String category; // Hardware/Equipment, Network/WiFi, Printer/Scanner, etc.
    private String priority;

    @Lob
    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TicketStatus status = TicketStatus.OPEN;

    @Lob
    private String response;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private boolean emailSent = false;
    private String emailFailureReason;
}
