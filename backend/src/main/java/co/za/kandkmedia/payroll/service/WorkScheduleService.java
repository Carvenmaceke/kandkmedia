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
 * Algorithm: for each "days needed" count (1-5), precompute every valid
 * combination of weekdays — valid meaning it doesn't contain 3+
 * consecutive weekdays as a subset (Mon+Tue+Wed, Tue+Wed+Thu or
 * Wed+Thu+Fri), since three days in a row in the office defeats the
 * point of a hybrid schedule. The one exception is 5 days/week, where
 * 3-consecutive is mathematically unavoidable (it's every weekday) —
 * that case falls back to the single unfiltered combination (all five
 * days) instead of having no valid options.
 *
 * Then process employees in a stable order (by employee code), and for
 * each one, score every valid candidate combination for their needed
 * count by: (a) the max headcount any single day in the combination
 * would reach, (b) the total headcount across the combination's days,
 * and (c) how many times that exact combination has already been used
 * this run. The lowest-scoring combination wins. (a)+(b) keep daily
 * headcount balanced office-wide; (c) is a tiebreaker that spreads
 * different employees onto genuinely different day-groups instead of
 * everyone converging on the identical "emptiest days" answer.
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
     * Every valid weekday combination for each "days needed" count (1-5),
     * keyed by count. "Valid" excludes any combination containing 3+
     * consecutive weekdays, except where that would leave zero options
     * (5 days/week), where the unfiltered combination is used instead.
     * Computed once — the set of possible combinations never changes.
     */
    private static final Map<Integer, List<List<DayOfWeek>>> VALID_COMBOS = computeValidCombos();

    private static Map<Integer, List<List<DayOfWeek>>> computeValidCombos() {
        Map<Integer, List<List<DayOfWeek>>> byCount = new LinkedHashMap<>();
        for (int k = 1; k <= WEEKDAYS.length; k++) {
            List<List<DayOfWeek>> all = combinationsOfSize(k);
            List<List<DayOfWeek>> filtered = all.stream()
                    .filter(combo -> !hasThreeConsecutive(combo))
                    .toList();
            byCount.put(k, filtered.isEmpty() ? all : filtered);
        }
        return byCount;
    }

    /** All size-k subsets of WEEKDAYS, each in natural Mon-Fri order. */
    private static List<List<DayOfWeek>> combinationsOfSize(int k) {
        List<List<DayOfWeek>> result = new ArrayList<>();
        combine(0, new ArrayList<>(), k, result);
        return result;
    }

    private static void combine(int start, List<DayOfWeek> current, int k, List<List<DayOfWeek>> result) {
        if (current.size() == k) {
            result.add(List.copyOf(current));
            return;
        }
        for (int i = start; i < WEEKDAYS.length; i++) {
            current.add(WEEKDAYS[i]);
            combine(i + 1, current, k, result);
            current.remove(current.size() - 1);
        }
    }

    /** True if the combination contains 3 (or more) consecutive weekdays as a subset. */
    private static boolean hasThreeConsecutive(List<DayOfWeek> combo) {
        Set<DayOfWeek> set = EnumSet.copyOf(combo);
        for (int i = 0; i + 2 < WEEKDAYS.length; i++) {
            if (set.contains(WEEKDAYS[i]) && set.contains(WEEKDAYS[i + 1]) && set.contains(WEEKDAYS[i + 2])) {
                return true;
            }
        }
        return false;
    }

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
        Map<List<DayOfWeek>, Integer> comboUsage = new HashMap<>();

        List<Employee> result = new ArrayList<>();
        for (Employee e : employees) {
            int need = Math.min(e.getDaysPerWeek(), WEEKDAYS.length);
            List<DayOfWeek> chosen = bestCombo(VALID_COMBOS.get(need), headcount, comboUsage);

            for (DayOfWeek d : chosen) headcount.merge(d, 1, Integer::sum);
            comboUsage.merge(chosen, 1, Integer::sum);

            e.setAssignedWorkDays(chosen.stream().map(Enum::name).collect(Collectors.joining(",")));
            result.add(e);
        }
        return result;
    }

    /**
     * Picks the candidate combination that minimizes, in order: the max
     * headcount any of its days would reach once this employee is added,
     * the total headcount across its days, then how many times this exact
     * combination has already been used this run (so ties spread across
     * genuinely different day-groups instead of all landing on one).
     */
    private List<DayOfWeek> bestCombo(List<List<DayOfWeek>> candidates,
                                       Map<DayOfWeek, Integer> headcount,
                                       Map<List<DayOfWeek>, Integer> comboUsage) {
        Comparator<List<DayOfWeek>> byScore = Comparator
                .<List<DayOfWeek>>comparingInt(combo -> resultingMax(combo, headcount))
                .thenComparingInt(combo -> resultingTotal(combo, headcount))
                .thenComparingInt(combo -> comboUsage.getOrDefault(combo, 0));
        return candidates.stream().min(byScore).orElseThrow();
    }

    private int resultingMax(List<DayOfWeek> combo, Map<DayOfWeek, Integer> headcount) {
        return combo.stream().mapToInt(d -> headcount.get(d) + 1).max().orElse(0);
    }

    private int resultingTotal(List<DayOfWeek> combo, Map<DayOfWeek, Integer> headcount) {
        return combo.stream().mapToInt(d -> headcount.get(d) + 1).sum();
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
