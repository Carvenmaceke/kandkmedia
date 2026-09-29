package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.*;
import co.za.kandkmedia.payroll.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against a real (in-memory) database so foreign keys are enforced exactly as in production. */
@DataJpaTest
@Import(AccountCleanupService.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:cleanup;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AccountCleanupServiceTest {

    @Autowired AccountCleanupService service;
    @Autowired AppUserRepository users;
    @Autowired EmployeeRepository employees;
    @Autowired PayrollRepository payroll;
    @Autowired LeaveRequestRepository leave;
    @Autowired LeaveBalanceRepository balances;
    @Autowired LeaveTypeRepository leaveTypes;

    private Employee employee(String code, String first) {
        return employees.save(Employee.builder().employeeCode(code).firstName(first).lastName("Test")
                .email(first.toLowerCase() + "@kandkmedia.co.za").salary(BigDecimal.valueOf(10000)).active(true).build());
    }

    private AppUser user(Employee e, Role role) {
        return users.save(AppUser.builder().email(e.getEmail()).passwordHash("x").role(role).employee(e).build());
    }

    @Test
    void deletesEveryAccountExceptTheMastersOwn() {
        Employee masterEmp = employee("EMP-00001", "Master");
        AppUser master = user(masterEmp, Role.MASTER);
        Employee hr = employee("EMP-00002", "Hr");
        user(hr, Role.HR);
        Employee staff = employee("EMP-00003", "Staff");
        user(staff, Role.EMPLOYEE);
        staff.setManager(hr);
        masterEmp.setManager(hr);
        employees.save(staff);
        employees.save(masterEmp);

        LeaveType annual = leaveTypes.save(LeaveType.builder().name("Annual Leave").build());
        payroll.save(Payroll.builder().employee(staff).payPeriod("2026-09").status(PayrollStatus.SENT).build());
        payroll.save(Payroll.builder().employee(masterEmp).payPeriod("2026-09").status(PayrollStatus.SENT).build());
        balances.save(LeaveBalance.builder().employee(staff).leaveType(annual).daysRemaining(15).build());
        leave.save(LeaveRequest.builder().employee(staff).leaveType(annual).startDate(LocalDate.now()).endDate(LocalDate.now())
                .daysRequested(1).status(LeaveStatus.APPROVED).decidedBy(hr).build());
        LeaveRequest mastersLeave = leave.save(LeaveRequest.builder().employee(masterEmp).leaveType(annual).startDate(LocalDate.now())
                .endDate(LocalDate.now()).daysRequested(1).status(LeaveStatus.APPROVED).decidedBy(hr).build());

        Map<String, Integer> result = service.deleteAllAccountsExcept(master);
        users.flush();

        assertThat(result).containsEntry("accounts", 2).containsEntry("employees", 2).containsEntry("payrollRecords", 1)
                .containsEntry("leaveRequests", 1).containsEntry("leaveBalances", 1);
        assertThat(users.findAll()).extracting(AppUser::getId).containsExactly(master.getId());
        assertThat(employees.findAll()).extracting(Employee::getEmployeeCode).containsExactly("EMP-00001");
        assertThat(employees.findById(masterEmp.getId()).orElseThrow().getManager()).isNull();
        assertThat(payroll.findAll()).hasSize(1);
        assertThat(leave.findById(mastersLeave.getId()).orElseThrow().getDecidedBy()).isNull();
        assertThat(leaveTypes.findAll()).hasSize(1); // settings-type data is kept
    }
}
