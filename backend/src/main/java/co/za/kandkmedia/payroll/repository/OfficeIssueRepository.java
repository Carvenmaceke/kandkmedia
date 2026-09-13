package co.za.kandkmedia.payroll.repository;

import co.za.kandkmedia.payroll.domain.OfficeIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OfficeIssueRepository extends JpaRepository<OfficeIssue, Long> {
    List<OfficeIssue> findAllByOrderByCreatedAtDesc();
}
