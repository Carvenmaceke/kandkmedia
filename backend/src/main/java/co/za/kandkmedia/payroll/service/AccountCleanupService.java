package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.AppUser;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.LeaveRequest;
import co.za.kandkmedia.payroll.repository.AppUserRepository;
import co.za.kandkmedia.payroll.repository.EmployeeRepository;
import co.za.kandkmedia.payroll.repository.LeaveBalanceRepository;
import co.za.kandkmedia.payroll.repository.LeaveRequestRepository;
import co.za.kandkmedia.payroll.repository.PayrollRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Master-only reset for going live: deletes every account except the Master's own, together with
 * everything tied to those accounts (payroll records, leave requests and balances). The Master's
 * account and company settings, levels, departments and leave types are kept.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountCleanupService {

    private final AppUserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollRepository payrollRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;

    @Transactional
    public Map<String, Integer> deleteAllAccountsExcept(AppUser master) {
        if (master == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again.");
        Long keepEmployeeId = master.getEmployee() != null ? master.getEmployee().getId() : null;

        List<AppUser> users = userRepository.findAll().stream()
                .filter(u -> !Objects.equals(u.getId(), master.getId())).toList();
        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(e -> !Objects.equals(e.getId(), keepEmployeeId)).toList();
        Set<Long> goneIds = employees.stream().map(Employee::getId).collect(Collectors.toSet());

        // Anything the kept account still points at must let go first.
        employeeRepository.findAll().stream()
                .filter(e -> Objects.equals(e.getId(), keepEmployeeId) && e.getManager() != null && goneIds.contains(e.getManager().getId()))
                .forEach(e -> { e.setManager(null); employeeRepository.save(e); });

        List<LeaveRequest> leave = leaveRequestRepository.findAll();
        List<LeaveRequest> leaveToDelete = leave.stream()
                .filter(r -> r.getEmployee() != null && goneIds.contains(r.getEmployee().getId())).toList();
        leave.stream()
                .filter(r -> !leaveToDelete.contains(r) && r.getDecidedBy() != null && goneIds.contains(r.getDecidedBy().getId()))
                .forEach(r -> { r.setDecidedBy(null); leaveRequestRepository.save(r); });

        var payroll = payrollRepository.findAll().stream()
                .filter(p -> p.getEmployee() != null && goneIds.contains(p.getEmployee().getId())).toList();
        var balances = leaveBalanceRepository.findAll().stream()
                .filter(b -> b.getEmployee() != null && goneIds.contains(b.getEmployee().getId())).toList();

        payrollRepository.deleteAll(payroll);
        leaveRequestRepository.deleteAll(leaveToDelete);
        leaveBalanceRepository.deleteAll(balances);
        userRepository.deleteAll(users);
        employees.forEach(e -> e.setManager(null)); // break manager links between the ones being removed
        employeeRepository.saveAll(employees);
        employeeRepository.deleteAll(employees);

        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("accounts", users.size());
        result.put("employees", employees.size());
        result.put("payrollRecords", payroll.size());
        result.put("leaveRequests", leaveToDelete.size());
        result.put("leaveBalances", balances.size());
        log.warn("Master cleared test accounts: {}", result);
        return result;
    }
}
