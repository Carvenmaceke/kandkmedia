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
import java.util.Map;

/**
 * Seeds just enough reference + demo data for the API to be usable out of
 * the box, mirroring the frontend prototype's dummy data so the two stay
 * comparable while the real integration is being wired up. Only runs when
 * the company table is empty, so it's safe on every restart.
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final String DEMO_PASSWORD = "password123";

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
            return; // already seeded
        }

        Company company = companyRepository.save(Company.builder()
                .name("K and K Media (Pty) Ltd")
                .address("526, 16th Road, Constantia Square Office Park, Randjespark, Midrand, Gauteng, South Africa")
                .email("sales@kandkmedia.co.za")
                .phone("+27 11 312 2206")
                .website("www.kandkmedia.co.za")
                .logoUrl("https://www.kandkmedia.co.za/wp-content/uploads/2024/05/cropped-cropped-K-and-K-Media-logo-New-1.png")
                .build());

        Map<String, Department> departments = seedDepartments();
        Map<String, EmployeeLevel> levels = seedLevels();
        List<LeaveType> leaveTypes = seedLeaveTypes();

        // HR and Admin accounts
        Employee lindiwe = seedPerson(company, departments.get("HR"), levels.get("Manager"),
                "Lindiwe", "Zulu", "lindiwe.zulu@kandkmedia.co.za", "082 222 3344", "HR Manager", null);
        seedUser(lindiwe, Role.HR);

        Employee karabo = seedPerson(company, departments.get("Admin"), levels.get("Manager"),
                "Karabo", "Mahlangu", "karabo.mahlangu@kandkmedia.co.za", "082 111 2233", "System Administrator", null);
        seedUser(karabo, Role.ADMIN);

        // Managers
        Employee thabo = seedPerson(company, departments.get("Digital Media"), levels.get("Manager"),
                "Thabo", "Nkosi", "thabo.nkosi@kandkmedia.co.za", "082 333 4455", "Digital Media Manager", null);
        seedUser(thabo, Role.MANAGER);

        Employee grace = seedPerson(company, departments.get("Creative Services"), levels.get("Manager"),
                "Grace", "Sithole", "grace.sithole@kandkmedia.co.za", "082 444 5566", "Creative & Events Manager", null);
        seedUser(grace, Role.MANAGER);

        // Employees
        Employee john = seedPerson(company, departments.get("Digital Media"), levels.get("Junior"),
                "John", "Doe", "john.doe@kandkmedia.co.za", "082 555 6677", "Web Developer", thabo);
        seedUser(john, Role.EMPLOYEE);

        Employee amahle = seedPerson(company, departments.get("Digital Media"), levels.get("Mid-Level"),
                "Amahle", "Dlamini", "amahle.dlamini@kandkmedia.co.za", "082 666 7788", "Social Media Manager", thabo);
        seedUser(amahle, Role.EMPLOYEE);

        Employee pieter = seedPerson(company, departments.get("Creative Services"), levels.get("Senior"),
                "Pieter", "van der Merwe", "pieter.vdmerwe@kandkmedia.co.za", "082 777 8899", "Senior Videographer", grace);
        seedUser(pieter, Role.EMPLOYEE);

        Employee sarah = seedPerson(company, departments.get("Publications"), levels.get("Junior"),
                "Sarah", "Botha", "sarah.botha@kandkmedia.co.za", "082 999 0011", "Copywriter", grace);
        seedUser(sarah, Role.EMPLOYEE);

        Employee michael = seedPerson(company, departments.get("Sales"), levels.get("Mid-Level"),
                "Michael", "Chen", "michael.chen@kandkmedia.co.za", "083 111 2200", "Sales Executive", grace);
        seedUser(michael, Role.EMPLOYEE);

        Employee support = seedPerson(company, departments.get("Admin"), levels.get("Junior"),
                "Support", "Desk", "support@kandkmedia.co.za", "083 222 3300", "Support Coordinator", null);
        seedUser(support, Role.EMPLOYEE);

        for (Employee employee : List.of(lindiwe, karabo, thabo, grace, john, amahle, pieter, sarah, michael, support)) {
            seedLeaveBalances(employee, leaveTypes);
        }
    }

    private Map<String, Department> seedDepartments() {
        List<String> names = List.of("Digital Media", "Creative Services", "Publications", "Events Management", "Sales", "HR", "Admin");
        return names.stream()
                .map(n -> departmentRepository.save(Department.builder().name(n).build()))
                .collect(java.util.stream.Collectors.toMap(Department::getName, d -> d));
    }

    private Map<String, EmployeeLevel> seedLevels() {
        record L(String name, int def, int min, int max) {}
        List<L> defs = List.of(
                new L("Intern", 4000, 3500, 6000),
                new L("Junior", 12000, 8000, 15000),
                new L("Mid-Level", 20000, 15000, 25000),
                new L("Senior", 30000, 25000, 45000),
                new L("Manager", 42000, 38000, 55000));
        return defs.stream()
                .map(l -> levelRepository.save(EmployeeLevel.builder()
                        .name(l.name())
                        .defaultSalary(BigDecimal.valueOf(l.def()))
                        .minSalary(BigDecimal.valueOf(l.min()))
                        .maxSalary(BigDecimal.valueOf(l.max()))
                        .build()))
                .collect(java.util.stream.Collectors.toMap(EmployeeLevel::getName, l -> l));
    }

    private List<LeaveType> seedLeaveTypes() {
        List<String> names = List.of("Annual Leave", "Sick Leave", "Family Responsibility Leave",
                "Study Leave", "Unpaid Leave", "Maternity Leave", "Parental Leave");
        return names.stream()
                .map(n -> leaveTypeRepository.save(LeaveType.builder().name(n).build()))
                .toList();
    }

    private Employee seedPerson(Company company, Department dept, EmployeeLevel level,
                                 String first, String last, String email, String phone,
                                 String position, Employee manager) {
        Employee employee = Employee.builder()
                .employeeCode(employeeRepository.nextEmployeeCode())
                .firstName(first)
                .lastName(last)
                .email(email)
                .phone(phone)
                .position(position)
                .department(dept)
                .level(level)
                .salary(level.getDefaultSalary())
                .startDate(LocalDate.now().minusYears(1))
                .manager(manager)
                .company(company)
                .build();
        return employeeRepository.save(employee);
    }

    private void seedUser(Employee employee, Role role) {
        userRepository.save(AppUser.builder()
                .email(employee.getEmail())
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .role(role)
                .employee(employee)
                .build());
    }

    private void seedLeaveBalances(Employee employee, List<LeaveType> types) {
        for (LeaveType type : types) {
            int days = switch (type.getName()) {
                case "Annual Leave" -> 15;
                case "Sick Leave" -> 10;
                case "Family Responsibility Leave" -> 3;
                default -> 0;
            };
            leaveBalanceRepository.save(LeaveBalance.builder()
                    .employee(employee)
                    .leaveType(type)
                    .daysRemaining(days)
                    .build());
        }
    }
}
