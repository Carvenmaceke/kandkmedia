package co.za.kandkmedia.payroll.controller;

import co.za.kandkmedia.payroll.domain.AppUser;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.LeaveRequest;
import co.za.kandkmedia.payroll.domain.Payroll;
import co.za.kandkmedia.payroll.dto.LeaveDecisionDto;
import co.za.kandkmedia.payroll.repository.EmployeeRepository;
import co.za.kandkmedia.payroll.repository.PayrollRepository;
import co.za.kandkmedia.payroll.service.LeaveService;
import co.za.kandkmedia.payroll.service.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @GetMapping("/employees")
    public List<Employee> employees() {
        return employeeRepository.findAll();
    }

    @GetMapping("/employees/{id}")
    public Employee employee(@PathVariable Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found."));
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

    /** Generates (or fetches, if already generated) this month's draft payroll row for one employee. */
    @PostMapping("/payroll/{employeeId}/draft")
    public Payroll generateDraft(@PathVariable Long employeeId,
                                  @RequestParam(defaultValue = "0") BigDecimal overtime,
                                  @RequestParam(defaultValue = "0") BigDecimal bonus) {
        Employee employee = employee(employeeId);
        return payrollService.generateDraft(employee, Payroll.currentPeriod(), overtime, bonus);
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
}
