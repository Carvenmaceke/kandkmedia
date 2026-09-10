package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.*;
import co.za.kandkmedia.payroll.dto.LeaveRequestDto;
import co.za.kandkmedia.payroll.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;

    public LeaveRequest apply(Employee employee, LeaveRequestDto dto) {
        LeaveType type = leaveTypeRepository.findByNameIgnoreCase(dto.getLeaveType())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown leave type."));

        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must be on or after the start date.");
        }
        int days = (int) (ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1);

        LeaveRequest request = LeaveRequest.builder()
                .employee(employee)
                .leaveType(type)
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .daysRequested(days)
                .reason(dto.getReason())
                .status(LeaveStatus.PENDING)
                .build();

        return leaveRequestRepository.save(request);
    }

    public List<LeaveRequest> myRequests(Long employeeId) {
        return leaveRequestRepository.findByEmployeeIdOrderByStartDateDesc(employeeId);
    }

    public List<LeaveRequest> teamRequests(Long managerId) {
        return leaveRequestRepository.findByEmployeeManagerIdOrderByStartDateDesc(managerId);
    }

    public List<LeaveRequest> allRequests() {
        return leaveRequestRepository.findAllByOrderByStartDateDesc();
    }

    public LeaveRequest decide(Long requestId, boolean approve, Employee decidedBy) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request not found."));

        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request has already been decided.");
        }

        request.setStatus(approve ? LeaveStatus.APPROVED : LeaveStatus.REJECTED);
        request.setDecidedBy(decidedBy);

        if (approve) {
            leaveBalanceRepository.findByEmployeeIdAndLeaveTypeId(
                            request.getEmployee().getId(), request.getLeaveType().getId())
                    .ifPresent(balance -> {
                        int remaining = Math.max(0, balance.getDaysRemaining() - request.getDaysRequested());
                        balance.setDaysRemaining(remaining);
                        leaveBalanceRepository.save(balance);
                    });
        }

        return leaveRequestRepository.save(request);
    }
}
