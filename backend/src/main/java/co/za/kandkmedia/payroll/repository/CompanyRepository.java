package co.za.kandkmedia.payroll.repository;

import co.za.kandkmedia.payroll.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
