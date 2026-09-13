package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.OfficeIssue;
import co.za.kandkmedia.payroll.domain.TicketStatus;
import co.za.kandkmedia.payroll.dto.OfficeIssueRequestDto;
import co.za.kandkmedia.payroll.repository.OfficeIssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OfficeIssueService {

    private final OfficeIssueRepository officeIssueRepository;
    private final EmailService emailService;

    /**
     * Saves first, then attempts to send — a failed send never loses the
     * submission itself; it's recorded with emailSent=false for IT support
     * to notice and retry.
     */
    public OfficeIssue submit(OfficeIssueRequestDto dto) {
        OfficeIssue issue = OfficeIssue.builder()
                .employeeName(dto.getEmployeeName())
                .employeeCode(dto.getEmployeeCode())
                .employeeEmail(dto.getEmployeeEmail())
                .role(dto.getRole())
                .department(dto.getDepartment())
                .office(dto.getOffice())
                .subject(dto.getSubject())
                .category(dto.getCategory())
                .priority(dto.getPriority())
                .description(dto.getDescription())
                .status(TicketStatus.OPEN)
                .build();

        issue = officeIssueRepository.save(issue);
        emailService.sendOfficeIssue(issue);
        return officeIssueRepository.save(issue);
    }

    public List<OfficeIssue> all() {
        return officeIssueRepository.findAllByOrderByCreatedAtDesc();
    }

    /** Frontend statuses are "Open"/"In Progress"/"Resolved". */
    public OfficeIssue updateStatus(Long id, String status, String response) {
        OfficeIssue issue = officeIssueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Office issue not found."));
        TicketStatus mapped = switch (status == null ? "" : status.toLowerCase().replace(" ", "_")) {
            case "in_progress" -> TicketStatus.IN_PROGRESS;
            case "resolved" -> TicketStatus.RESOLVED;
            default -> TicketStatus.OPEN;
        };
        issue.setStatus(mapped);
        issue.setResponse(response);
        return officeIssueRepository.save(issue);
    }
}
