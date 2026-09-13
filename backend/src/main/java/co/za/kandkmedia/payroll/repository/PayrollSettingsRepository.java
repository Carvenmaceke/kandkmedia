package co.za.kandkmedia.payroll.repository;

import co.za.kandkmedia.payroll.domain.PayrollSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollSettingsRepository extends JpaRepository<PayrollSettings, Long> {
}
