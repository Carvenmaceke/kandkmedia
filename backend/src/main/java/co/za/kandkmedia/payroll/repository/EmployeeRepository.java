package co.za.kandkmedia.payroll.repository;

import co.za.kandkmedia.payroll.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByEmail(String email);
    Optional<Employee> findByEmployeeCode(String employeeCode);
    List<Employee> findByManagerId(Long managerId);
    boolean existsByEmail(String email);

    default String nextEmployeeCode() {
        long count = count();
        return String.format("EMP-%05d", count + 1);
    }
}
