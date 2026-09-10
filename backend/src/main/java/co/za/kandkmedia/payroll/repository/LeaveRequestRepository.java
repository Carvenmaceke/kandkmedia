package co.za.kandkmedia.payroll.repository;

import co.za.kandkmedia.payroll.domain.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeIdOrderByStartDateDesc(Long employeeId);
    List<LeaveRequest> findByEmployeeManagerIdOrderByStartDateDesc(Long managerId);
    List<LeaveRequest> findAllByOrderByStartDateDesc();
}
