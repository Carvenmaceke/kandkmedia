package co.za.kandkmedia.payroll.controller;

import co.za.kandkmedia.payroll.domain.AppUser;
import co.za.kandkmedia.payroll.domain.Company;
import co.za.kandkmedia.payroll.domain.Department;
import co.za.kandkmedia.payroll.domain.EmployeeLevel;
import co.za.kandkmedia.payroll.domain.SupportTicket;
import co.za.kandkmedia.payroll.domain.OfficeIssue;
import co.za.kandkmedia.payroll.domain.PayrollSettings;
import co.za.kandkmedia.payroll.repository.PayrollSettingsRepository;
import co.za.kandkmedia.payroll.service.OfficeIssueService;
import co.za.kandkmedia.payroll.repository.AppUserRepository;
import co.za.kandkmedia.payroll.repository.CompanyRepository;
import co.za.kandkmedia.payroll.repository.DepartmentRepository;
import co.za.kandkmedia.payroll.repository.EmployeeLevelRepository;
import co.za.kandkmedia.payroll.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeLevelRepository levelRepository;
    private final AppUserRepository userRepository;
    private final SupportService supportService;
    private final OfficeIssueService officeIssueService;
    private final PayrollSettingsRepository payrollSettingsRepository;

    @GetMapping("/company")
    public Company company() {
        return companyRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company profile not yet configured."));
    }

    @PutMapping("/company")
    public Company updateCompany(@Valid @RequestBody Company update) {
        Company company = company();
        company.setName(update.getName());
        company.setRegistrationNumber(update.getRegistrationNumber());
        company.setAddress(update.getAddress());
        company.setEmail(update.getEmail());
        company.setPhone(update.getPhone());
        company.setWebsite(update.getWebsite());
        company.setLogoUrl(update.getLogoUrl());
        return companyRepository.save(company);
    }

    @PutMapping("/office-availability")
    public Company updateOfficeAvailability(@RequestBody java.util.Map<String, String> body) {
        Company company = company();
        company.setOfficeAvailabilityNote(body.get("note"));
        return companyRepository.save(company);
    }

    @GetMapping("/departments")
    public List<Department> departments() {
        return departmentRepository.findAll();
    }

    @PostMapping("/departments")
    public Department addDepartment(@RequestBody Department department) {
        return departmentRepository.save(department);
    }

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

    @GetMapping("/users")
    public List<AppUser> users() {
        return userRepository.findAll();
    }

    /**
     * Master-exclusive: this is the only role-assignment endpoint in the
     * system, and @PreAuthorize enforces MASTER specifically — not covered
     * by the broader /api/admin/** rule that also lets ADMIN/IT_SUPPORT in,
     * since letting any admin-tier account grant further admin access
     * would defeat the whole point of having a single gatekeeper.
     */
    @PutMapping("/users/{id}/role")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('MASTER')")
    public AppUser changeUserRole(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        if (user.getRole() == co.za.kandkmedia.payroll.domain.Role.MASTER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The Master account's role cannot be changed.");
        }
        String roleName = body.get("role");
        co.za.kandkmedia.payroll.domain.Role newRole;
        try {
            newRole = co.za.kandkmedia.payroll.domain.Role.valueOf(roleName.toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + roleName);
        }
        if (newRole == co.za.kandkmedia.payroll.domain.Role.MASTER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot promote another account to Master.");
        }
        user.setRole(newRole);
        return userRepository.save(user);
    }

    @GetMapping("/support")
    public List<SupportTicket> supportTickets() {
        return supportService.all();
    }

    @PutMapping("/support/{id}")
    public SupportTicket updateSupportTicket(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        return supportService.updateStatus(id, body.get("status"), body.get("response"));
    }

    @PutMapping("/support/{id}/resolve")
    public SupportTicket resolveSupportTicket(@PathVariable Long id) {
        return supportService.resolve(id);
    }

    @GetMapping("/office-issues")
    public List<OfficeIssue> officeIssues() {
        return officeIssueService.all();
    }

    @PutMapping("/office-issues/{id}")
    public OfficeIssue updateOfficeIssue(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        return officeIssueService.updateStatus(id, body.get("status"), body.get("response"));
    }

    @GetMapping("/payroll-settings")
    public PayrollSettings payrollSettings() {
        return payrollSettingsRepository.findAll().stream().findFirst()
                .orElseGet(() -> payrollSettingsRepository.save(PayrollSettings.builder().build()));
    }

    @PutMapping("/payroll-settings")
    public PayrollSettings updatePayrollSettings(@RequestBody PayrollSettings update) {
        PayrollSettings settings = payrollSettings();
        settings.setAutoSendEnabled(update.isAutoSendEnabled());
        settings.setSendOn(update.getSendOn());
        settings.setDeliveryHour(update.getDeliveryHour());
        settings.setDeliveryMinute(update.getDeliveryMinute());
        return payrollSettingsRepository.save(settings);
    }
}
