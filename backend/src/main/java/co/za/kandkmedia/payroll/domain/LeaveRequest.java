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

    @Column(columnDefinition = "LONGTEXT")
    private String employeeSignature;
    private java.time.LocalDateTime employeeSignedAt;

    @Column(columnDefinition = "LONGTEXT")
    private String deciderSignature;
    private java.time.LocalDateTime deciderSignedAt;

    /** Required when declined; null when approved. */
    @Lob
    private String decisionReason;

    // --- Proof of leave (sick note, etc.) — stored as a base64 data URL,
    // same pattern already used for signatures. No separate file storage
    // service (S3 etc.) is configured, and Render's filesystem is
    // ephemeral anyway, so the database is the only place this can
    // actually persist. Frontend already enforces a 4MB size limit before
    // ever building this data URL. ---
    private String proofFileName;
    private String proofFileType;

    @Column(columnDefinition = "LONGTEXT")
    private String proofFileDataUrl;

    @Builder.Default
    private boolean letterEmailSent = false;
    private String letterEmailFailureReason;
}
