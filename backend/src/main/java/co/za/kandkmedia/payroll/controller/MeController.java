package co.za.kandkmedia.payroll.controller;

import co.za.kandkmedia.payroll.domain.AppUser;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.LeaveRequest;
import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.dto.LeaveRequestDto;
import co.za.kandkmedia.payroll.dto.EmployeeProfileDto;
import co.za.kandkmedia.payroll.dto.ChangePasswordDto;
import co.za.kandkmedia.payroll.repository.AppUserRepository;
import co.za.kandkmedia.payroll.repository.CompanyRepository;
import co.za.kandkmedia.payroll.repository.LeaveBalanceRepository;
import co.za.kandkmedia.payroll.repository.PayrollRepository;
import co.za.kandkmedia.payroll.service.LeaveService;
import co.za.kandkmedia.payroll.service.EmployeeProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final CompanyRepository companyRepository;

    @GetMapping
    public Employee myProfile(@AuthenticationPrincipal AppUser user) {
        return employeeOf(user);
    }

    /** Read-only, any authenticated role — the whole point of this note is
     *  that every employee sees it, not just Admin/IT Support/Master (who
     *  are the only ones with /api/admin/** access to set it). */
    @GetMapping("/office-availability")
    public java.util.Map<String, String> officeAvailability() {
        String note = companyRepository.findAll().stream().findFirst()
                .map(co.za.kandkmedia.payroll.domain.Company::getOfficeAvailabilityNote)
                .orElse(null);
        return java.util.Collections.singletonMap("note", note == null ? "" : note);
    }

    @PutMapping("/profile")
    public Employee updateMyProfile(@AuthenticationPrincipal AppUser user, @RequestBody EmployeeProfileDto dto) {
        return employeeProfileService.updateProfile(employeeOf(user).getId(), dto);
    }

    /**
     * Requires the correct current password before allowing a change —
     * this is the one place a wrong password on the request body is
     * expected and meaningful, not something to silently trust.
     */
    @PutMapping("/password")
    public void changePassword(@AuthenticationPrincipal AppUser user, @Valid @RequestBody ChangePasswordDto dto) {
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        appUserRepository.save(user);
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
