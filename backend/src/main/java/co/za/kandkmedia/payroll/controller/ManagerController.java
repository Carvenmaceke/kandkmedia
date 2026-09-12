package co.za.kandkmedia.payroll.controller;

import co.za.kandkmedia.payroll.domain.AppUser;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.LeaveRequest;
import co.za.kandkmedia.payroll.dto.LeaveDecisionDto;
import co.za.kandkmedia.payroll.repository.EmployeeRepository;
import co.za.kandkmedia.payroll.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/manager")
@RequiredArgsConstructor
public class ManagerController {

    private final LeaveService leaveService;
    private final EmployeeRepository employeeRepository;

    /** A manager's own direct reports only — deliberately narrower than
     *  /api/hr/employees (the whole company), since a manager shouldn't see
     *  everyone's records, just their team's. */
    @GetMapping("/team")
    public List<Employee> team(@AuthenticationPrincipal AppUser user) {
        return employeeRepository.findByManagerId(user.getEmployee().getId());
    }

    @GetMapping("/team-leave")
    public List<LeaveRequest> teamLeaveRequests(@AuthenticationPrincipal AppUser user) {
        return leaveService.teamRequests(user.getEmployee().getId());
    }

    @PutMapping("/team-leave/{id}/approve")
    public LeaveRequest approve(@PathVariable Long id, @Valid @RequestBody LeaveDecisionDto decision, @AuthenticationPrincipal AppUser user) {
        return leaveService.decide(id, true, user.getEmployee(), decision.getSignature(), null);
    }

    @PutMapping("/team-leave/{id}/reject")
    public LeaveRequest reject(@PathVariable Long id, @Valid @RequestBody LeaveDecisionDto decision, @AuthenticationPrincipal AppUser user) {
        return leaveService.decide(id, false, user.getEmployee(), decision.getSignature(), decision.getReason());
    }
}
