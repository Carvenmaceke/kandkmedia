package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.AppUser;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalaryVisibilityServiceTest {

    private final SalaryVisibilityService service = new SalaryVisibilityService();

    private AppUser userWithRole(Role role) {
        AppUser user = new AppUser();
        user.setRole(role);
        return user;
    }

    private Employee employeeWithSalary() {
        return Employee.builder()
                .employeeCode("EMP-00001")
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@kandkmedia.co.za")
                .salary(BigDecimal.valueOf(30000))
                .rateType("Monthly")
                .build();
    }

    @Test
    void hrSeesSalaryUnchanged() {
        Employee redacted = service.redact(employeeWithSalary(), userWithRole(Role.HR));
        assertThat(redacted.getSalary()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        assertThat(redacted.getRateType()).isEqualTo("Monthly");
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"HR"}, mode = EnumSource.Mode.EXCLUDE)
    void everyOtherRoleHasSalaryStripped(Role role) {
        Employee redacted = service.redact(employeeWithSalary(), userWithRole(role));
        assertThat(redacted.getSalary()).isNull();
        assertThat(redacted.getRateType()).isNull();
    }

    @Test
    void redactingDoesNotMutateTheOriginalEntity() {
        Employee original = employeeWithSalary();
        service.redact(original, userWithRole(Role.MASTER));
        assertThat(original.getSalary()).isEqualByComparingTo(BigDecimal.valueOf(30000));
    }

    @Test
    void redactsEveryEmployeeInAListForNonHrViewer() {
        List<Employee> redacted = service.redact(List.of(employeeWithSalary(), employeeWithSalary()), userWithRole(Role.MASTER));
        assertThat(redacted).allSatisfy(e -> assertThat(e.getSalary()).isNull());
    }

    @Test
    void listIsUnchangedForHrViewer() {
        List<Employee> original = List.of(employeeWithSalary());
        List<Employee> result = service.redact(original, userWithRole(Role.HR));
        assertThat(result).isSameAs(original);
    }

    @Test
    void nullViewerCannotSeeSalary() {
        assertThat(service.canSeeSalary(null)).isFalse();
        assertThat(service.redact(employeeWithSalary(), null).getSalary()).isNull();
    }
}
