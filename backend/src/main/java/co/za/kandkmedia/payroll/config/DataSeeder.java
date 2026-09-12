package co.za.kandkmedia.payroll.config;

import co.za.kandkmedia.payroll.domain.*;
import co.za.kandkmedia.payroll.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Seeds real, structural reference data — the company's actual profile,
 * a starting set of departments and salary levels (both editable in-app by
 * Admin/HR afterwards), the standard SA leave types, and exactly ONE real
 * account: the system owner (Master). No other employees, no sample
 * leave/payroll/ticket data — this is a live system, not a demo, and
 * every other person in it should be entered through Sign Up, then have
 * their role assigned by Master.
 *
 * The Master account is the deliberate exception to "no seed data": it's
 * a real account, not sample data, and without it nobody could log in to
 * grant anyone else HR/Admin/IT Support access in the first place.
 *
 * Only runs when the company table is empty, so it's safe on every restart
 * and never overwrites data you've since edited in-app.
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final String MASTER_DEFAULT_PASSWORD = "password123";

    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeLevelRepository levelRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final AppUserRepository userRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!companyRepository.findAll().isEmpty()) {
            return; // already seeded — never re-run over real data
        }

        Company company = companyRepository.save(Company.builder()
                .name("K and K Media (Pty) Ltd")
                .address("526, 16th Road, Constantia Square Office Park, Randjespark, Midrand, Gauteng, South Africa")
                .email("sales@kandkmedia.co.za")
                .phone("+27 11 312 2206")
                .website("www.kandkmedia.co.za")
                .logoUrl("https://www.kandkmedia.co.za/wp-content/uploads/2024/05/cropped-cropped-K-and-K-Media-logo-New-1.png")
                .build());

        var departments = seedDepartments();
        seedLevels();
        List<LeaveType> leaveTypes = seedLeaveTypes();

        seedMasterAccount(company, departments.get("Admin"), leaveTypes);
    }

    private java.util.Map<String, Department> seedDepartments() {
        List<String> names = List.of("Digital Media", "Creative Services", "Publications", "Events Management", "Sales", "HR", "Admin");
        return names.stream()
                .map(n -> departmentRepository.save(Department.builder().name(n).build()))
                .collect(java.util.stream.Collectors.toMap(Department::getName, d -> d));
    }

    private void seedLevels() {
        record L(String name, int def, int min, int max) {}
        List<L> defs = List.of(
                new L("Intern", 4000, 3500, 6000),
                new L("Junior", 12000, 8000, 15000),
                new L("Mid-Level", 20000, 15000, 25000),
                new L("Senior", 30000, 25000, 45000),
                new L("Manager", 42000, 38000, 55000));
        defs.forEach(l -> levelRepository.save(EmployeeLevel.builder()
                .name(l.name())
                .defaultSalary(BigDecimal.valueOf(l.def()))
                .minSalary(BigDecimal.valueOf(l.min()))
                .maxSalary(BigDecimal.valueOf(l.max()))
                .build()));
    }

    private List<LeaveType> seedLeaveTypes() {
        List<String> names = List.of("Annual Leave", "Sick Leave", "Family Responsibility Leave",
                "Study Leave", "Unpaid Leave", "Maternity Leave", "Parental Leave");
        return names.stream()
                .map(n -> leaveTypeRepository.save(LeaveType.builder().name(n).build()))
                .toList();
    }

    private void seedMasterAccount(Company company, Department adminDept, List<LeaveType> leaveTypes) {
        Employee master = employeeRepository.save(Employee.builder()
                .employeeCode(employeeRepository.nextEmployeeCode())
                .firstName("Carven")
                .lastName("Maceke")
                .position("Owner / System Master")
                .department(adminDept)
                .salary(BigDecimal.ZERO)
                .startDate(LocalDate.now())
                .email("carven.maceke@kandkmedia.co.za")
                .phone("0607950837")
                .office("Sandton")
                .company(company)
                .agreedToTerms(true)
                .build());

        userRepository.save(AppUser.builder()
                .email(master.getEmail())
                .passwordHash(passwordEncoder.encode(MASTER_DEFAULT_PASSWORD))
                .role(Role.MASTER)
                .employee(master)
                .build());

        for (LeaveType type : leaveTypes) {
            int days = switch (type.getName()) {
                case "Annual Leave" -> 20;
                case "Sick Leave" -> 10;
                case "Family Responsibility Leave" -> 3;
                default -> 0;
            };
            leaveBalanceRepository.save(LeaveBalance.builder()
                    .employee(master)
                    .leaveType(type)
                    .daysRemaining(days)
                    .build());
        }
    }
}
