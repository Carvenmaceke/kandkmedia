package co.za.kandkmedia.payroll.repository;

import co.za.kandkmedia.payroll.domain.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findAllByOrderByCreatedAtDesc();
}
