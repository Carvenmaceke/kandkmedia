package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.AppUser;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.domain.Role;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * HR is the only role that gets to see what someone earns — not Master,
 * Admin, IT Support or Manager. Returns a detached copy with salary/
 * rateType nulled out for anyone who isn't HR, rather than mutating the
 * JPA-managed entity (which would risk the null getting flushed to the
 * database via dirty checking).
 */
@Service
public class SalaryVisibilityService {

    public Employee redact(Employee employee, AppUser viewer) {
        if (employee == null || canSeeSalary(viewer)) {
            return employee;
        }
        return employee.toBuilder().salary(null).rateType(null).build();
    }

    public List<Employee> redact(List<Employee> employees, AppUser viewer) {
        if (canSeeSalary(viewer)) {
            return employees;
        }
        return employees.stream().map(e -> e.toBuilder().salary(null).rateType(null).build()).toList();
    }

    public boolean canSeeSalary(AppUser viewer) {
        return viewer != null && viewer.getRole() == Role.HR;
    }
}
