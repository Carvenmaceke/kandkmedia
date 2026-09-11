package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.SupportTicket;
import co.za.kandkmedia.payroll.domain.TicketStatus;
import co.za.kandkmedia.payroll.dto.SupportRequestDto;
import co.za.kandkmedia.payroll.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportTicketRepository supportTicketRepository;
    private final EmailService emailService;

    /**
     * Saves the ticket first, then attempts to send it — so a failed send
     * (bad SMTP config, etc.) never loses the submission itself; it just
     * gets recorded with emailSent=false for HR/Admin to notice and retry.
     */
    public SupportTicket submit(SupportRequestDto dto) {
        SupportTicket ticket = SupportTicket.builder()
                .employeeName(dto.getEmployeeName())
                .employeeCode(dto.getEmployeeCode())
                .employeeEmail(dto.getEmployeeEmail())
                .role(dto.getRole())
                .department(dto.getDepartment())
                .subject(dto.getSubject())
                .category(dto.getCategory())
                .priority(dto.getPriority())
                .description(dto.getDescription())
                .status(TicketStatus.OPEN)
                .build();

        ticket = supportTicketRepository.save(ticket);
        emailService.sendSupportRequest(ticket);
        return supportTicketRepository.save(ticket);
    }

    public List<SupportTicket> all() {
        return supportTicketRepository.findAllByOrderByCreatedAtDesc();
    }

    public SupportTicket resolve(Long id) {
        SupportTicket ticket = supportTicketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support ticket not found."));
        ticket.setStatus(TicketStatus.RESOLVED);
        return supportTicketRepository.save(ticket);
    }
}
