package co.za.kandkmedia.payroll.repository;

import co.za.kandkmedia.payroll.domain.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    Optional<LeaveType> findByNameIgnoreCase(String name);
}
