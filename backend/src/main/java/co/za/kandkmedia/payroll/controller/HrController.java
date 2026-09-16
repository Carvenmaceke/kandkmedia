package co.za.kandkmedia.payroll.controller;

import co.za.kandkmedia.payroll.domain.AppUser;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.LeaveRequest;
import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.domain.EmployeeLevel;
import co.za.kandkmedia.payroll.repository.EmployeeLevelRepository;
import co.za.kandkmedia.payroll.service.WorkScheduleService;
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
    private final WorkScheduleService workScheduleService;
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

    /** Sets how many days/week an employee needs to be in-office. Doesn't
     *  assign WHICH days by itself — call /schedule/auto-assign after
     *  changing this (or several employees' requirements at once) to
     *  re-run the balancing algorithm. */
    @PutMapping("/employees/{id}/schedule")
    public Employee setDaysPerWeek(@PathVariable Long id, @RequestBody java.util.Map<String, Integer> body) {
        Employee e = employee(id);
        Integer days = body.get("daysPerWeek");
        if (days == null || days < 0 || days > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "daysPerWeek must be between 0 and 5.");
        }
        e.setDaysPerWeek(days);
        return employeeRepository.save(e);
    }

    /** Re-runs the day-balancing algorithm for every employee with a
     *  daysPerWeek requirement set. Safe to call repeatedly — e.g. after
     *  a new employee gets their requirement set, or an office's capacity
     *  changes. */
    @PostMapping("/schedule/auto-assign")
    public List<Employee> autoAssignSchedule() {
        return workScheduleService.autoAssignAll();
    }

    /** Per-weekday headcount for one office — how full each day currently
     *  is against that office's capacity, for HR to check before deciding
     *  whether to raise someone's days or adjust capacity. */
    @GetMapping("/schedule/headcount")
    public java.util.Map<String, Integer> scheduleHeadcount(@RequestParam String office) {
        java.util.Map<String, Integer> result = new java.util.LinkedHashMap<>();
        workScheduleService.headcountForOffice(office).forEach((day, count) -> result.put(day.name(), count));
        return result;
    }

    @GetMapping("/schedule/capacity")
    public java.util.Map<String, Integer> scheduleCapacity() {
        co.za.kandkmedia.payroll.domain.Company company = workScheduleService.company();
        java.util.Map<String, Integer> result = new java.util.LinkedHashMap<>();
        result.put("Midrand", company.getMidrandCapacity());
        result.put("Sandton", company.getSandtonCapacity());
        return result;
    }

    @PutMapping("/schedule/capacity")
    public java.util.Map<String, Integer> updateScheduleCapacity(@RequestBody java.util.Map<String, Integer> body) {
        co.za.kandkmedia.payroll.domain.Company company = workScheduleService.company();
        if (body.get("Midrand") != null) company.setMidrandCapacity(body.get("Midrand"));
        if (body.get("Sandton") != null) company.setSandtonCapacity(body.get("Sandton"));
        workScheduleService.saveCompany(company);
        java.util.Map<String, Integer> result = new java.util.LinkedHashMap<>();
        result.put("Midrand", company.getMidrandCapacity());
        result.put("Sandton", company.getSandtonCapacity());
        return result;
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

    /**
     * Removes a payroll record regardless of status — Master-exclusive,
     * for correcting a record that was wrong from the start (e.g. the
     * zero-salary auto-draft bug) and already advanced past DRAFT before
     * anyone noticed. Not for deleting a normal, correctly-calculated
     * Finalized/Sent payslip — @PreAuthorize keeps this out of reach of
     * HR/Admin/IT Support, same reasoning as the role-assignment endpoint:
     * a single gatekeeper for an action that can erase real history.
     */
    @DeleteMapping("/payroll/{id}/force")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('MASTER')")
    public void forceDeletePayroll(@PathVariable Long id) {
        payrollService.forceDelete(id);
    }
}
