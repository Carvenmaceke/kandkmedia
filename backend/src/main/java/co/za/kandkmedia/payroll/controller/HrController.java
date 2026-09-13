package co.za.kandkmedia.payroll.controller;

import co.za.kandkmedia.payroll.domain.AppUser;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.LeaveRequest;
import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.domain.EmployeeLevel;
import co.za.kandkmedia.payroll.repository.EmployeeLevelRepository;
import co.za.kandkmedia.payroll.dto.LeaveDecisionDto;
import co.za.kandkmedia.payroll.repository.EmployeeRepository;
import co.za.kandkmedia.payroll.repository.AppUserRepository;
import co.za.kandkmedia.payroll.repository.PayrollRepository;
import co.za.kandkmedia.payroll.service.LeaveService;
import co.za.kandkmedia.payroll.service.PayrollService;
import co.za.kandkmedia.payroll.service.OnboardingDocumentPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/hr")
@RequiredArgsConstructor
public class HrController {

    private final EmployeeRepository employeeRepository;
    private final LeaveService leaveService;
    private final PayrollService payrollService;
    private final PayrollRepository payrollRepository;
    private final EmployeeLevelRepository levelRepository;
    private final co.za.kandkmedia.payroll.service.EmployeeProfileService employeeProfileService;
    private final AppUserRepository appUserRepository;
    private final OnboardingDocumentPdfService onboardingDocumentPdfService;

    @GetMapping("/levels")
    public List<EmployeeLevel> levels() {
        return levelRepository.findAll();
    }

    @PostMapping("/levels")
    public EmployeeLevel addLevel(@RequestBody EmployeeLevel level) {
        return levelRepository.save(level);
    }

    @PutMapping("/levels/{id}")
    public EmployeeLevel updateLevel(@PathVariable Long id, @RequestBody EmployeeLevel update) {
        EmployeeLevel level = levelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Level not found."));
        level.setDefaultSalary(update.getDefaultSalary());
        level.setMinSalary(update.getMinSalary());
        level.setMaxSalary(update.getMaxSalary());
        return levelRepository.save(level);
    }

    @GetMapping("/employees")
    public List<Employee> employees() {
        return employeeRepository.findAll();
    }

    /**
     * Role lives on AppUser, not Employee — GET /api/hr/employees alone
     * can't tell you anyone's role. This exists so HR (who has no access to
     * /api/admin/users, unlike Admin/IT_Support/Master) has *some* way to
     * see roles for the people they manage, e.g. picking a manager to
     * assign someone to.
     */
    @GetMapping("/employee-roles")
    public java.util.Map<String, String> employeeRoles() {
        return appUserRepository.findAll().stream()
                .filter(u -> u.getEmployee() != null)
                .collect(java.util.stream.Collectors.toMap(
                        u -> u.getEmployee().getEmployeeCode(),
                        u -> u.getRole().name(),
                        (a, b) -> a));
    }

    @GetMapping("/employees/{id}")
    public Employee employee(@PathVariable Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found."));
    }

    @PutMapping("/employees/{id}/profile")
    public Employee updateEmployeeProfile(@PathVariable Long id, @RequestBody co.za.kandkmedia.payroll.dto.EmployeeProfileDto dto) {
        return employeeProfileService.updateProfile(id, dto);
    }

    /**
     * Marks an employee as no longer active (departed) and disables their
     * login — but never deletes the row. Payroll history, leave history,
     * and past payslip/leave-letter documents all reference this Employee
     * and must survive for compliance purposes even after departure.
     */
    @PutMapping("/employees/{id}/deactivate")
    public Employee deactivateEmployee(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body) {
        Employee e = employee(id);
        e.setActive(false);
        String date = body != null ? body.get("terminationDate") : null;
        e.setTerminationDate(date != null && !date.isBlank() ? java.time.LocalDate.parse(date) : java.time.LocalDate.now());
        employeeRepository.save(e);
        appUserRepository.findByEmployeeId(id).ifPresent(u -> { u.setEnabled(false); appUserRepository.save(u); });
        return e;
    }

    /** Reverses a deactivation — e.g. a rehire, or an accidental deactivation. */
    @PutMapping("/employees/{id}/reactivate")
    public Employee reactivateEmployee(@PathVariable Long id) {
        Employee e = employee(id);
        e.setActive(true);
        e.setTerminationDate(null);
        employeeRepository.save(e);
        appUserRepository.findByEmployeeId(id).ifPresent(u -> { u.setEnabled(true); appUserRepository.save(u); });
        return e;
    }

    @GetMapping("/employees/{id}/onboarding-document")
    public ResponseEntity<byte[]> downloadOnboardingDocument(@PathVariable Long id) {
        Employee e = employee(id);
        byte[] pdf = onboardingDocumentPdfService.generate(e);
        String filename = "Onboarding-" + e.getEmployeeCode() + "-" + e.getFullName().replace(" ", "-") + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }

    @GetMapping("/leave")
    public List<LeaveRequest> allLeaveRequests() {
        return leaveService.allRequests();
    }

    @PutMapping("/leave/{id}/approve")
    public LeaveRequest approve(@PathVariable Long id, @jakarta.validation.Valid @RequestBody LeaveDecisionDto decision, @AuthenticationPrincipal AppUser user) {
        return leaveService.decide(id, true, user.getEmployee(), decision.getSignature(), null);
    }

    @PutMapping("/leave/{id}/reject")
    public LeaveRequest reject(@PathVariable Long id, @jakarta.validation.Valid @RequestBody LeaveDecisionDto decision, @AuthenticationPrincipal AppUser user) {
        return leaveService.decide(id, false, user.getEmployee(), decision.getSignature(), decision.getReason());
    }

    @GetMapping("/payroll")
    public List<Payroll> payrollForPeriod(@RequestParam(required = false) String payPeriod) {
        String period = payPeriod != null ? payPeriod : Payroll.currentPeriod();
        return payrollRepository.findByPayPeriod(period);
    }

    /** Generates (or fetches, if already generated) this month's draft payroll row for one employee.
     *  Returns 409 if this employee's salary hasn't been set by HR yet — no payslip should exist
     *  for someone still sitting at the signup default of zero. */
    @PostMapping("/payroll/{employeeId}/draft")
    public Payroll generateDraft(@PathVariable Long employeeId,
                                  @RequestParam(defaultValue = "0") BigDecimal overtime,
                                  @RequestParam(defaultValue = "0") BigDecimal bonus) {
        Employee employee = employee(employeeId);
        return payrollService.generateDraft(employee, Payroll.currentPeriod(), overtime, bonus)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "This employee's salary hasn't been set yet — add it under Employees before generating a payslip."));
    }

    @PostMapping("/payroll/advance")
    public List<Payroll> advanceStage(@RequestParam(required = false) String payPeriod) {
        String period = payPeriod != null ? payPeriod : Payroll.currentPeriod();
        return payrollService.advanceStage(period);
    }

    /** Retry a payslip email that previously failed (or resend one that already succeeded). */
    @PostMapping("/payroll/{id}/resend-email")
    public Payroll resendEmail(@PathVariable Long id) {
        return payrollService.resendEmail(id);
    }

    /** Removes a payroll record — only while it's still in DRAFT. */
    @DeleteMapping("/payroll/{id}")
    public void deleteDraft(@PathVariable Long id) {
        payrollService.deleteDraft(id);
    }
}
