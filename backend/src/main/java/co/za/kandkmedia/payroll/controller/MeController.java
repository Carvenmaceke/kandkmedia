package co.za.kandkmedia.payroll.controller;

import co.za.kandkmedia.payroll.domain.AppUser;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.LeaveRequest;
import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.dto.LeaveRequestDto;
import co.za.kandkmedia.payroll.dto.EmployeeProfileDto;
import co.za.kandkmedia.payroll.repository.LeaveBalanceRepository;
import co.za.kandkmedia.payroll.repository.PayrollRepository;
import co.za.kandkmedia.payroll.service.LeaveService;
import co.za.kandkmedia.payroll.service.EmployeeProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Backs the frontend's "Switch to my profile" self-service mode: HR, Admin
 * and Manager accounts are employees too, and apply for their own leave
 * through the exact same endpoints an Employee-role account uses.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final LeaveService leaveService;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final PayrollRepository payrollRepository;
    private final EmployeeProfileService employeeProfileService;

    @GetMapping
    public Employee myProfile(@AuthenticationPrincipal AppUser user) {
        return employeeOf(user);
    }

    @PutMapping("/profile")
    public Employee updateMyProfile(@AuthenticationPrincipal AppUser user, @RequestBody EmployeeProfileDto dto) {
        return employeeProfileService.updateProfile(employeeOf(user).getId(), dto);
    }

    @GetMapping("/leave")
    public List<LeaveRequest> myLeaveRequests(@AuthenticationPrincipal AppUser user) {
        return leaveService.myRequests(employeeOf(user).getId());
    }

    @PostMapping("/leave")
    public LeaveRequest applyForLeave(@AuthenticationPrincipal AppUser user, @Valid @RequestBody LeaveRequestDto dto) {
        return leaveService.apply(employeeOf(user), dto);
    }

    @GetMapping("/leave-balance")
    public Object myLeaveBalance(@AuthenticationPrincipal AppUser user) {
        return leaveBalanceRepository.findByEmployeeId(employeeOf(user).getId());
    }

    @GetMapping("/payslips")
    public List<Payroll> myPayslips(@AuthenticationPrincipal AppUser user) {
        return payrollRepository.findByEmployeeIdOrderByPayPeriodDesc(employeeOf(user).getId());
    }

    private Employee employeeOf(AppUser user) {
        Employee employee = user.getEmployee();
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This account has no linked employee profile.");
        }
        return employee;
    }
}
