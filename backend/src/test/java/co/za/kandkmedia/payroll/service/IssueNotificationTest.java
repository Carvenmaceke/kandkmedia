package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.OfficeIssue;
import co.za.kandkmedia.payroll.domain.SupportTicket;
import co.za.kandkmedia.payroll.domain.TicketStatus;
import co.za.kandkmedia.payroll.dto.OfficeIssueRequestDto;
import co.za.kandkmedia.payroll.dto.SupportRequestDto;
import co.za.kandkmedia.payroll.repository.OfficeIssueRepository;
import co.za.kandkmedia.payroll.repository.SupportTicketRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Employees get an email when their issue is logged and whenever IT changes its status or replies. */
class IssueNotificationTest {

    private final SupportTicketRepository tickets = mock(SupportTicketRepository.class);
    private final OfficeIssueRepository issues = mock(OfficeIssueRepository.class);
    private final EmailService email = mock(EmailService.class);
    private final SupportService support = new SupportService(tickets, email);
    private final OfficeIssueService office = new OfficeIssueService(issues, email);

    {
        when(tickets.save(any())).thenAnswer(inv -> { SupportTicket t = inv.getArgument(0); if (t.getId() == null) t.setId(7L); return t; });
        when(issues.save(any())).thenAnswer(inv -> { OfficeIssue i = inv.getArgument(0); if (i.getId() == null) i.setId(9L); return i; });
    }

    @Test
    void loggingASystemIssueConfirmsToTheEmployee() {
        SupportRequestDto dto = new SupportRequestDto();
        dto.setEmployeeName("Thabo Mokoena"); dto.setEmployeeEmail("thabo@kandkmedia.co.za");
        dto.setSubject("Can't see payslip"); dto.setCategory("Payslip Issue"); dto.setPriority("High"); dto.setDescription("x");
        support.submit(dto);
        verify(email).sendSupportRequest(any());
        verify(email).sendIssueReceived("System Issue", 7L, "thabo@kandkmedia.co.za", "Thabo Mokoena", "Can't see payslip", "Payslip Issue", "High", null);
    }

    @Test
    void loggingAnOfficeIssueConfirmsWithTheOffice() {
        OfficeIssueRequestDto dto = new OfficeIssueRequestDto();
        dto.setEmployeeName("Thabo Mokoena"); dto.setEmployeeEmail("thabo@kandkmedia.co.za"); dto.setOffice("Rosebank");
        dto.setSubject("Printer jam"); dto.setCategory("Printer/Scanner"); dto.setPriority("Low"); dto.setDescription("x");
        office.submit(dto);
        verify(email).sendIssueReceived("Office Issue", 9L, "thabo@kandkmedia.co.za", "Thabo Mokoena", "Printer jam", "Printer/Scanner", "Low", "Rosebank");
    }

    @Test
    void statusChangeOrNewReplySendsAnUpdateButRepeatsDoNot() {
        SupportTicket t = SupportTicket.builder().id(7L).employeeEmail("thabo@kandkmedia.co.za").employeeName("Thabo").subject("S")
                .description("d").status(TicketStatus.OPEN).build();
        when(tickets.findById(7L)).thenReturn(Optional.of(t));

        support.updateStatus(7L, "In Progress", null);
        verify(email).sendIssueUpdate("System Issue", 7L, "thabo@kandkmedia.co.za", "Thabo", "S", TicketStatus.IN_PROGRESS, null);

        support.updateStatus(7L, "In Progress", "Replacing the cable today");
        verify(email).sendIssueUpdate(eq("System Issue"), eq(7L), any(), any(), any(), eq(TicketStatus.IN_PROGRESS), eq("Replacing the cable today"));

        clearInvocations(email);
        support.updateStatus(7L, "In Progress", "Replacing the cable today"); // nothing new
        verify(email, never()).sendIssueUpdate(any(), any(), any(), any(), any(), any(), any());

        support.resolve(7L);
        verify(email).sendIssueUpdate(eq("System Issue"), eq(7L), any(), any(), any(), eq(TicketStatus.RESOLVED), eq("Replacing the cable today"));
    }

    @Test
    void officeIssueResolvedSendsAnUpdate() {
        OfficeIssue i = OfficeIssue.builder().id(9L).employeeEmail("a@kandkmedia.co.za").employeeName("A").subject("WiFi").office("Midrand")
                .description("d").status(TicketStatus.IN_PROGRESS).build();
        when(issues.findById(9L)).thenReturn(Optional.of(i));
        office.updateStatus(9L, "Resolved", "Router restarted");
        verify(email).sendIssueUpdate("Office Issue", 9L, "a@kandkmedia.co.za", "A", "WiFi", TicketStatus.RESOLVED, "Router restarted");
    }
}
