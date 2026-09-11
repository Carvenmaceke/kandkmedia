package co.za.kandkmedia.payroll.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "leave_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private Integer daysRequested;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LeaveStatus status = LeaveStatus.PENDING;

    @ManyToOne
    @JoinColumn(name = "decided_by_id")
    private Employee decidedBy;

    // --- Signatures — both stored as PNG data URLs (base64), same format
    // the frontend's canvas signature pad produces. ---

    @Lob
    private String employeeSignature;
    private java.time.LocalDateTime employeeSignedAt;

    @Lob
    private String deciderSignature;
    private java.time.LocalDateTime deciderSignedAt;

    /** Required when declined; null when approved. */
    @Lob
    private String decisionReason;

    @Builder.Default
    private boolean letterEmailSent = false;
    private String letterEmailFailureReason;
}
