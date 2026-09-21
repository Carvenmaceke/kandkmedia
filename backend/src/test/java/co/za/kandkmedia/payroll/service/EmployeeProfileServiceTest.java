package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.dto.EmployeeProfileDto;
import co.za.kandkmedia.payroll.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class EmployeeProfileServiceTest {

    private final EmployeeRepository employeeRepository = Mockito.mock(EmployeeRepository.class);
    private final PayrollService payrollService = Mockito.mock(PayrollService.class);
    private final EmployeeProfileService service = new EmployeeProfileService(employeeRepository, payrollService);

    private Employee employee() {
        return Employee.builder()
                .id(1L)
                .employeeCode("EMP-00001")
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@kandkmedia.co.za")
                .salary(BigDecimal.valueOf(20000))
                .build();
    }

    @Test
    void hrCanSetSalary() {
        Employee employee = employee();
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmployeeProfileDto dto = new EmployeeProfileDto();
        dto.setSalary(BigDecimal.valueOf(35000));

        Employee result = service.updateProfile(1L, dto, true);

        assertThat(result.getSalary()).isEqualByComparingTo(BigDecimal.valueOf(35000));
    }

    @Test
    void nonHrCallerIsRejectedWhenAttemptingToSetSalary() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee()));

        EmployeeProfileDto dto = new EmployeeProfileDto();
        dto.setSalary(BigDecimal.valueOf(99999));

        assertThatThrownBy(() -> service.updateProfile(1L, dto, false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only HR");
    }

    @Test
    void selfServiceCallerIsRejectedWhenAttemptingToSetRateType() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee()));

        EmployeeProfileDto dto = new EmployeeProfileDto();
        dto.setRateType("Hourly");

        assertThatThrownBy(() -> service.updateProfile(1L, dto, false))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void nonSalaryFieldsStillEditableWithoutSalaryPermission() {
        Employee employee = employee();
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmployeeProfileDto dto = new EmployeeProfileDto();
        dto.setPosition("Senior Developer");

        Employee result = service.updateProfile(1L, dto, false);

        assertThat(result.getPosition()).isEqualTo("Senior Developer");
        assertThat(result.getSalary()).isEqualByComparingTo(BigDecimal.valueOf(20000)); // unchanged
    }
}
