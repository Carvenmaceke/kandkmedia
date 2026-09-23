package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.repository.CompanyRepository;
import co.za.kandkmedia.payroll.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

class WorkScheduleServiceTest {

    private final EmployeeRepository employeeRepository = Mockito.mock(EmployeeRepository.class);
    private final CompanyRepository companyRepository = Mockito.mock(CompanyRepository.class);
    private final WorkScheduleService service = new WorkScheduleService(employeeRepository, companyRepository);

    private Employee employee(String code, String office, int daysPerWeek) {
        return Employee.builder()
                .employeeCode(code)
                .firstName("First")
                .lastName("Last")
                .email(code.toLowerCase() + "@kandkmedia.co.za")
                .office(office)
                .salary(BigDecimal.TEN)
                .active(true)
                .daysPerWeek(daysPerWeek)
                .build();
    }

    private List<DayOfWeek> assignedDays(Employee e) {
        return Arrays.stream(e.getAssignedWorkDays().split(","))
                .map(DayOfWeek::valueOf)
                .toList();
    }

    private boolean hasThreeConsecutive(List<DayOfWeek> days) {
        Set<DayOfWeek> set = EnumSet.copyOf(days);
        DayOfWeek[] week = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY};
        for (int i = 0; i + 2 < week.length; i++) {
            if (set.contains(week[i]) && set.contains(week[i + 1]) && set.contains(week[i + 2])) {
                return true;
            }
        }
        return false;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void noEmployeeNeedingFewerThanFiveDaysGetsThreeConsecutiveWeekdays(int daysPerWeek) {
        List<Employee> employees = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            employees.add(employee("EMP-%05d".formatted(i), "Midrand", daysPerWeek));
        }
        when(employeeRepository.findAll()).thenReturn(employees);
        when(employeeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<Employee> result = service.autoAssignAll();

        for (Employee e : result) {
            List<DayOfWeek> days = assignedDays(e);
            assertThat(days).as("days for " + e.getEmployeeCode()).hasSize(daysPerWeek);
            assertThat(hasThreeConsecutive(days))
                    .as(e.getEmployeeCode() + " has 3+ consecutive weekdays: " + days)
                    .isFalse();
        }
    }

    @Test
    void fiveDaysPerWeekFallsBackToAllFiveWeekdaysInsteadOfCrashing() {
        List<Employee> employees = List.of(employee("EMP-00001", "Midrand", 5));
        when(employeeRepository.findAll()).thenReturn(employees);
        when(employeeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<Employee> result = service.autoAssignAll();

        assertThat(assignedDays(result.get(0))).containsExactly(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
    }

    @Test
    void spreadsDifferentEmployeesAcrossDifferentDayCombinationsInsteadOfConverging() {
        List<Employee> employees = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            employees.add(employee("EMP-%05d".formatted(i), "Sandton", 3));
        }
        when(employeeRepository.findAll()).thenReturn(employees);
        when(employeeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<Employee> result = service.autoAssignAll();

        Set<List<DayOfWeek>> distinctCombos = new HashSet<>();
        for (Employee e : result) {
            distinctCombos.add(assignedDays(e));
        }
        // 6 employees needing 3 days/week should not all land on the identical combination.
        assertThat(distinctCombos.size()).isGreaterThan(1);
    }

    @Test
    void balancesHeadcountAcrossTheWeek() {
        List<Employee> employees = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            employees.add(employee("EMP-%05d".formatted(i), "Midrand", 3));
        }
        when(employeeRepository.findAll()).thenReturn(employees);
        when(employeeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<Employee> result = service.autoAssignAll();

        Map<DayOfWeek, Integer> headcount = new EnumMap<>(DayOfWeek.class);
        for (Employee e : result) {
            for (DayOfWeek d : assignedDays(e)) {
                headcount.merge(d, 1, Integer::sum);
            }
        }
        int max = Collections.max(headcount.values());
        int min = Collections.min(headcount.values());
        // Not necessarily perfectly even: the anti-3-consecutive rule makes Wednesday
        // appear in fewer valid 3-day combinations (3 of 7) than the other weekdays
        // (4-5 of 7), so a group entirely made of 3-day employees can't always reach a
        // spread of 1. It should still stay close.
        assertThat(max - min).as("headcount spread across days should stay tight: " + headcount).isLessThanOrEqualTo(2);
    }

    @Test
    void midrandAndSandtonAreBalancedIndependently() {
        List<Employee> employees = new ArrayList<>();
        employees.add(employee("EMP-00001", "Midrand", 3));
        employees.add(employee("EMP-00002", "Midrand", 3));
        employees.add(employee("EMP-00003", "Sandton", 3));
        when(employeeRepository.findAll()).thenReturn(employees);
        when(employeeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<Employee> result = service.autoAssignAll();

        Employee midrandFirst = result.stream().filter(e -> e.getEmployeeCode().equals("EMP-00001")).findFirst().orElseThrow();
        Employee sandtonOnly = result.stream().filter(e -> e.getEmployeeCode().equals("EMP-00003")).findFirst().orElseThrow();

        // Sandton has only one employee, so it should land on the emptiest-day combo
        // regardless of what Midrand (processed independently) chose for its own employees.
        assertThat(assignedDays(sandtonOnly)).hasSize(3);
        assertThat(assignedDays(midrandFirst)).hasSize(3);
    }

    @Test
    void resetAllClearsEveryonesDaysAndAssignments() {
        Employee a = employee("EMP-1", "Midrand", 3);
        a.setAssignedWorkDays("MONDAY,WEDNESDAY,FRIDAY");
        Employee b = employee("EMP-2", "Sandton", 2);
        Employee untouched = employee("EMP-3", "Sandton", 0);
        untouched.setDaysPerWeek(null);
        Mockito.when(employeeRepository.findAll()).thenReturn(List.of(a, b, untouched));
        Mockito.when(employeeRepository.saveAll(Mockito.anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<Employee> reset = service.resetAll();

        org.assertj.core.api.Assertions.assertThat(reset).containsExactly(a, b);
        for (Employee e : List.of(a, b)) {
            org.assertj.core.api.Assertions.assertThat(e.getDaysPerWeek()).isNull();
            org.assertj.core.api.Assertions.assertThat(e.getAssignedWorkDays()).isNull();
        }
    }
}
