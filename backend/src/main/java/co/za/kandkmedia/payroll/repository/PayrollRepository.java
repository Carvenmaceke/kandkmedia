package co.za.kandkmedia.payroll.repository;

import co.za.kandkmedia.payroll.domain.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {
    List<Payroll> findByEmployeeIdOrderByPayPeriodDesc(Long employeeId);
    List<Payroll> findByPayPeriod(String payPeriod);
    Optional<Payroll> findByEmployeeIdAndPayPeriod(Long employeeId, String payPeriod);
}
