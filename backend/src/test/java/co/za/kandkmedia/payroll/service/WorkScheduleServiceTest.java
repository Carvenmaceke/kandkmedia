package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Company;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.repository.CompanyRepository;
import co.za.kandkmedia.payroll.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class WorkScheduleServiceTest {

    private final EmployeeRepository employeeRepository = Mockito.mock(EmployeeRepository.class);
    private final CompanyRepository companyRepository = Mockito.mock(CompanyRepository.class);
    private final WorkScheduleService service = new WorkScheduleService(employeeRepository, companyRepository);
    private final List<Employee> people = new ArrayList<>();
    private final Company company = Company.builder().name("K and K").midrandCapacity(15).sandtonCapacity(10).rosebankCapacity(10).build();

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 21);

    @BeforeEach
    void setUp() {
        when(employeeRepository.findAll()).thenAnswer(inv -> people);
        when(employeeRepository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        when(employeeRepository.saveAll(Mockito.anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(companyRepository.findAll()).thenReturn(List.of(company));
    }

    private Employee person(long id, String office) {
        Employee e = Employee.builder().id(id).employeeCode(String.format("EMP-%05d", id)).firstName("P" + id).lastName("L")
                .office(office).active(true).build();
        people.add(e);
        return e;
    }

    private Employee rotating(long id, Map<String, Object> officeDays) {
        Employee e = person(id, "Midrand");
        return service.updatePlan(e, Map.of("mode", "ROTATING", "officeDays", officeDays));
    }

    private Map<DayOfWeek, String> week(Employee e, int weeksFromNow) {
        return service.weekSchedule(MONDAY.plusWeeks(weeksFromNow)).days().get(e.getId());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void rotatingPlansNeverGetThreeDaysInARow(int days) {
        Employee e = rotating(1, Map.of("Midrand", days));
        for (int w = 0; w < 20; w++) {
            Map<DayOfWeek, String> d = week(e, w);
            assertThat(d).hasSize(days);
            assertThat(WorkScheduleService.hasThreeInARow(d.keySet())).as("week %d: %s", w, d.keySet()).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void daysRotateEveryWeek(int days) {
        Employee e = rotating(1, Map.of("Midrand", days));
        for (int w = 0; w < 10; w++) {
            assertThat(week(e, w + 1).keySet()).as("week %d vs %d", w, w + 1).isNotEqualTo(week(e, w).keySet());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void oneAndTwoDayPlansAlwaysSkipADay(int days) {
        Employee e = rotating(1, Map.of("Midrand", days));
        for (int w = 0; w < 20; w++) {
            assertThat(WorkScheduleService.adjacentPairs(new ArrayList<>(week(e, w).keySet()))).as("week %d", w).isZero();
        }
    }

    @Test
    void fourDaysUsesTheOnlyPatternWithoutThreeInARow() {
        Employee e = rotating(1, Map.of("Midrand", 4));
        assertThat(week(e, 0).keySet()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
    }

    @Test
    void splitOfficePlanKeepsTheCountsAndRotatesTheOffice() {
        Employee e = rotating(1, Map.of("Midrand", 2, "Rosebank", 1));
        Set<DayOfWeek> rosebankDays = new HashSet<>();
        for (int w = 0; w < 8; w++) {
            Map<DayOfWeek, String> d = week(e, w);
            assertThat(d.values().stream().filter("Midrand"::equals).count()).isEqualTo(2);
            assertThat(d.values().stream().filter("Rosebank"::equals).count()).isEqualTo(1);
            d.forEach((day, office) -> { if (office.equals("Rosebank")) rosebankDays.add(day); });
        }
        assertThat(rosebankDays).as("Rosebank day should move around").hasSizeGreaterThan(2);
    }

    @Test
    void everyDayAndMandatoryFixedDaysAreKeptExactly() {
        Employee everyDay = person(1, "Sandton");
        service.updatePlan(everyDay, Map.of("mode", "FIXED", "fixedDays", Map.of(
                "MONDAY", "Sandton", "TUESDAY", "Sandton", "WEDNESDAY", "Sandton", "THURSDAY", "Sandton", "FRIDAY", "Sandton")));
        Employee mandatory = person(2, "Midrand");
        service.updatePlan(mandatory, Map.of("mode", "FIXED", "fixedDays", Map.of("MONDAY", "Midrand", "TUESDAY", "Midrand", "WEDNESDAY", "Rosebank")));

        for (int w = 0; w < 3; w++) {
            assertThat(week(everyDay, w)).hasSize(5).containsValue("Sandton").doesNotContainValue("Midrand");
            assertThat(week(mandatory, w)).containsEntry(DayOfWeek.MONDAY, "Midrand").containsEntry(DayOfWeek.TUESDAY, "Midrand")
                    .containsEntry(DayOfWeek.WEDNESDAY, "Rosebank").hasSize(3);
        }
    }

    @Test
    void staysWithinCapacityWhenItFits() {
        company.setMidrandCapacity(2);
        for (long i = 1; i <= 5; i++) rotating(i, Map.of("Midrand", 2)); // 10 desk-days = 5 days x 2 desks
        for (int w = 0; w < 6; w++) {
            WorkScheduleService.WeekSchedule s = service.weekSchedule(MONDAY.plusWeeks(w));
            assertThat(s.headcount().get("Midrand").values()).allMatch(n -> n <= 2);
            assertThat(s.warnings()).isEmpty();
        }
    }

    @Test
    void warnsWhenCapacityCannotBeMet() {
        company.setRosebankCapacity(1);
        for (long i = 1; i <= 3; i++) rotating(i, Map.of("Rosebank", 2)); // 6 desk-days > 5
        assertThat(service.weekSchedule(MONDAY).warnings()).anyMatch(w -> w.startsWith("Rosebank is over capacity"));
    }

    @Test
    void legacyDaysPerWeekCountsAsRotatingAtHomeOffice() {
        Employee e = person(1, "Sandton");
        e.setDaysPerWeek(2);
        assertThat(week(e, 0)).hasSize(2).containsValue("Sandton");
    }

    @Test
    void validatesPlans() {
        Employee e = person(1, "Midrand");
        assertThatThrownBy(() -> service.updatePlan(e, Map.of("mode", "ROTATING", "officeDays", Map.of("Midrand", 4, "Rosebank", 2))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("between 1 and 5");
        assertThatThrownBy(() -> service.updatePlan(e, Map.of("mode", "ROTATING", "officeDays", Map.of("Cape Town", 1))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Unknown office");
        assertThatThrownBy(() -> service.updatePlan(e, Map.of("mode", "FIXED", "fixedDays", Map.of("SATURDAY", "Midrand"))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("weekdays");
    }

    @Test
    void clearingAndResetRemoveThePlan() {
        Employee a = rotating(1, Map.of("Midrand", 3));
        Map<String, Object> clear = new HashMap<>();
        clear.put("daysPerWeek", null);
        service.updatePlan(a, clear);
        assertThat(service.planOf(a)).isNull();
        assertThat(a.getDaysPerWeek()).isNull();

        Employee b = rotating(2, Map.of("Midrand", 2));
        Employee c = person(3, "Sandton");
        service.updatePlan(c, Map.of("mode", "FIXED", "fixedDays", Map.of("MONDAY", "Sandton")));
        service.resetAll();
        assertThat(service.planOf(b)).isNull();
        assertThat(service.planOf(c)).isNull();
        assertThat(service.weekSchedule(MONDAY).days()).isEmpty();
    }
}
