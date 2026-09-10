package co.za.kandkmedia.payroll.controller;

import co.za.kandkmedia.payroll.domain.AppUser;
import co.za.kandkmedia.payroll.domain.LeaveRequest;
import co.za.kandkmedia.payroll.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/manager")
@RequiredArgsConstructor
public class ManagerController {

    private final LeaveService leaveService;

    @GetMapping("/team-leave")
    public List<LeaveRequest> teamLeaveRequests(@AuthenticationPrincipal AppUser user) {
        return leaveService.teamRequests(user.getEmployee().getId());
    }

    @PutMapping("/team-leave/{id}/approve")
    public LeaveRequest approve(@PathVariable Long id, @AuthenticationPrincipal AppUser user) {
        return leaveService.decide(id, true, user.getEmployee());
    }

    @PutMapping("/team-leave/{id}/reject")
    public LeaveRequest reject(@PathVariable Long id, @AuthenticationPrincipal AppUser user) {
        return leaveService.decide(id, false, user.getEmployee());
    }
}
