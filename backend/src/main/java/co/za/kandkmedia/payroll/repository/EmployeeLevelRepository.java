package co.za.kandkmedia.payroll.repository;

import co.za.kandkmedia.payroll.domain.EmployeeLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeLevelRepository extends JpaRepository<EmployeeLevel, Long> {
    Optional<EmployeeLevel> findByNameIgnoreCase(String name);
}
