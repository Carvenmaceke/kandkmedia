package co.za.kandkmedia.payroll.config;

import co.za.kandkmedia.payroll.domain.Company;
import co.za.kandkmedia.payroll.domain.Department;
import co.za.kandkmedia.payroll.domain.EmployeeLevel;
import co.za.kandkmedia.payroll.domain.LeaveType;
import co.za.kandkmedia.payroll.repository.CompanyRepository;
import co.za.kandkmedia.payroll.repository.DepartmentRepository;
import co.za.kandkmedia.payroll.repository.EmployeeLevelRepository;
import co.za.kandkmedia.payroll.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds only real, structural reference data — the company's actual profile,
 * a starting set of departments and salary levels (both editable in-app by
 * Admin/HR afterwards), and the standard SA leave types. No employees, no
 * user accounts, no sample leave/payroll/ticket data — this is a live
 * system now, not a demo, and every person in it should be entered through
 * Sign Up or by HR, not pre-loaded by this seeder.
 *
 * Only runs when the company table is empty, so it's safe on every restart
 * and never overwrites data you've since edited in-app.
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeLevelRepository levelRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    @Override
    public void run(String... args) {
        if (!companyRepository.findAll().isEmpty()) {
            return; // already seeded — never re-run over real data
        }

        companyRepository.save(Company.builder()
                .name("K and K Media (Pty) Ltd")
                .address("526, 16th Road, Constantia Square Office Park, Randjespark, Midrand, Gauteng, South Africa")
                .email("sales@kandkmedia.co.za")
                .phone("+27 11 312 2206")
                .website("www.kandkmedia.co.za")
                .logoUrl("https://www.kandkmedia.co.za/wp-content/uploads/2024/05/cropped-cropped-K-and-K-Media-logo-New-1.png")
                .build());

        seedDepartments();
        seedLevels();
        seedLeaveTypes();
    }

    private void seedDepartments() {
        List<String> names = List.of("Digital Media", "Creative Services", "Publications", "Events Management", "Sales", "HR", "Admin");
        names.forEach(n -> departmentRepository.save(Department.builder().name(n).build()));
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

    private void seedLeaveTypes() {
        List<String> names = List.of("Annual Leave", "Sick Leave", "Family Responsibility Leave",
                "Study Leave", "Unpaid Leave", "Maternity Leave", "Parental Leave");
        names.forEach(n -> leaveTypeRepository.save(LeaveType.builder().name(n).build()));
    }
}
