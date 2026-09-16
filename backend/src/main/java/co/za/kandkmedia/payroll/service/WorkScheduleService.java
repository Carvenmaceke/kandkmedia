package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Company;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.repository.CompanyRepository;
import co.za.kandkmedia.payroll.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Assigns WHICH specific weekdays each employee comes into the office,
 * given HR has set how many days/week they need (Employee.daysPerWeek).
 * Deliberately not "everyone picks their own days" — the whole point is
 * balancing daily headcount against limited desk capacity, so employees
 * needing fewer days get spread across the week rather than everyone
 * clustering on the same days.
 *
 * Greedy algorithm: process employees in a stable order (by employee
 * code), and for each one, assign their required number of days to
 * whichever weekdays currently have the LOWEST headcount for their
 * office. This naturally spreads people out — someone needing 3 days
 * doesn't get Monday/Tuesday/Wednesday just because those happen to
 * sort first; they get whichever days are least crowded at the time
 * they're processed.
 */
@Service
@RequiredArgsConstructor
public class WorkScheduleService {

    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;

    private static final DayOfWeek[] WEEKDAYS = {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    };

    /**
     * Re-runs the balancing algorithm for every active employee who has a
     * daysPerWeek requirement set, across both offices independently
     * (Midrand's headcount never affects Sandton's assignment). Employees
     * without daysPerWeek set are left untouched — HR hasn't decided their
     * requirement yet, so there's nothing to auto-assign.
     */
    public List<Employee> autoAssignAll() {
        List<Employee> allActive = employeeRepository.findAll().stream()
                .filter(Employee::isActive)
                .filter(e -> e.getDaysPerWeek() != null && e.getDaysPerWeek() > 0)
                .sorted(Comparator.comparing(Employee::getEmployeeCode, Comparator.nullsLast(String::compareTo)))
                .toList();

        Map<String, List<Employee>> byOffice = allActive.stream()
                .collect(Collectors.groupingBy(e -> e.getOffice() == null ? "" : e.getOffice(), LinkedHashMap::new, Collectors.toList()));

        List<Employee> updated = new ArrayList<>();
        for (Map.Entry<String, List<Employee>> entry : byOffice.entrySet()) {
            updated.addAll(assignForOffice(entry.getValue()));
        }
        return employeeRepository.saveAll(updated);
    }

    private List<Employee> assignForOffice(List<Employee> employees) {
        Map<DayOfWeek, Integer> headcount = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : WEEKDAYS) headcount.put(d, 0);

        List<Employee> result = new ArrayList<>();
        for (Employee e : employees) {
            int need = Math.min(e.getDaysPerWeek(), WEEKDAYS.length);
            List<DayOfWeek> chosen = Arrays.stream(WEEKDAYS)
                    .sorted(Comparator.comparingInt(headcount::get))
                    .limit(need)
                    .sorted() // display in natural Mon-Fri order once chosen
                    .toList();
            for (DayOfWeek d : chosen) headcount.merge(d, 1, Integer::sum);

            e.setAssignedWorkDays(chosen.stream().map(Enum::name).collect(Collectors.joining(",")));
            result.add(e);
        }
        return result;
    }

    /** Current headcount per weekday for one office, for HR's capacity view. */
    public Map<DayOfWeek, Integer> headcountForOffice(String office) {
        Map<DayOfWeek, Integer> headcount = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : WEEKDAYS) headcount.put(d, 0);
        employeeRepository.findAll().stream()
                .filter(Employee::isActive)
                .filter(e -> office.equals(e.getOffice()))
                .filter(e -> e.getAssignedWorkDays() != null && !e.getAssignedWorkDays().isBlank())
                .forEach(e -> Arrays.stream(e.getAssignedWorkDays().split(","))
                        .forEach(d -> headcount.merge(DayOfWeek.valueOf(d), 1, Integer::sum)));
        return headcount;
    }

    public Company company() {
        return companyRepository.findAll().stream().findFirst()
                .orElseGet(() -> companyRepository.save(Company.builder().build()));
    }

    public Company saveCompany(Company company) {
        return companyRepository.save(company);
    }
}
