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
import co.za.kandkmedia.payroll.service.PayslipPdfService;
import co.za.kandkmedia.payroll.service.ItAssistantService;
import co.za.kandkmedia.payroll.dto.AssistantRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final PayslipPdfService payslipPdfService;
    private final ItAssistantService itAssistantService;

    /** IT Assistant chat — answered by the Groq-hosted model when GROQ_API_KEY is set (503 otherwise, and the frontend falls back to its keyword answers). */
    /** Lets the chat show the "AI" badge only when the server actually has a Groq key. */
    @GetMapping("/assistant/status")
    public java.util.Map<String, Object> assistantStatus() {
        return java.util.Map.of("configured", itAssistantService.isConfigured(), "model", itAssistantService.model());
    }

    @PostMapping("/assistant")
    public java.util.Map<String, String> askAssistant(@AuthenticationPrincipal AppUser user, @RequestBody AssistantRequest request) {
        return java.util.Map.of("reply", itAssistantService.reply(employeeOf(user), request));
    }

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

    /** Only this employee's own days — never anyone else's schedule. */
    @GetMapping("/schedule")
    public java.util.Map<String, Object> mySchedule(@AuthenticationPrincipal AppUser user) {
        Employee e = employeeOf(user);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("office", e.getOffice());
        result.put("daysPerWeek", e.getDaysPerWeek());
        result.put("assignedWorkDays", e.getAssignedWorkDays() == null ? java.util.List.of() : java.util.Arrays.asList(e.getAssignedWorkDays().split(",")));
        return result;
    }

    /** Self-service — salary/rateType are never editable here, not even by HR editing their own profile. */
    @PutMapping("/profile")
    public Employee updateMyProfile(@AuthenticationPrincipal AppUser user, @RequestBody EmployeeProfileDto dto) {
        return employeeProfileService.updateProfile(employeeOf(user).getId(), dto, false);
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

    /** Only Finalized or Sent records — a Draft/Reviewed/Approved row is
     *  still being worked on and isn't a real payslip an employee should
     *  see or download yet. */
    @GetMapping("/payslips")
    public List<Payroll> myPayslips(@AuthenticationPrincipal AppUser user) {
        return payrollRepository.findByEmployeeIdOrderByPayPeriodDesc(employeeOf(user).getId()).stream()
                .filter(p -> p.getStatus() == co.za.kandkmedia.payroll.domain.PayrollStatus.FINALIZED
                        || p.getStatus() == co.za.kandkmedia.payroll.domain.PayrollStatus.SENT)
                .toList();
    }

    /** The real generated payslip document — only ever this employee's own, and only once finalized. */
    @GetMapping("/payslips/{id}/pdf")
    public ResponseEntity<byte[]> myPayslipPdf(@PathVariable Long id, @AuthenticationPrincipal AppUser user) {
        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll record not found."));
        Long ownEmployeeId = employeeOf(user).getId();
        if (!payroll.getEmployee().getId().equals(ownEmployeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This isn't your payslip.");
        }
        if (payroll.getStatus() != co.za.kandkmedia.payroll.domain.PayrollStatus.FINALIZED
                && payroll.getStatus() != co.za.kandkmedia.payroll.domain.PayrollStatus.SENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This payslip isn't finalized yet.");
        }
        byte[] pdf = payslipPdfService.generate(payroll);
        String filename = payroll.getPayPeriod() + "-" + payroll.getEmployee().getEmployeeCode() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf);
    }

    private Employee employeeOf(AppUser user) {
        Employee employee = user.getEmployee();
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This account has no linked employee profile.");
        }
        return employee;
    }
}
