package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.*;
import co.za.kandkmedia.payroll.dto.AuthResponse;
import co.za.kandkmedia.payroll.dto.LoginRequest;
import co.za.kandkmedia.payroll.dto.SignupRequest;
import co.za.kandkmedia.payroll.repository.*;
import co.za.kandkmedia.payroll.security.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Value("${app.allowed-email-domain}")
    private String allowedEmailDomain;

    @Transactional
    public AuthResponse signup(SignupRequest req) {
        String email = req.getEmail().trim().toLowerCase();

        if (!email.endsWith("@" + allowedEmailDomain)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Please use your company email address, ending in @" + allowedEmailDomain + ".");
        }
        if (userRepository.existsByEmail(email) || employeeRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists.");
        }
        if ((req.getIdNumber() == null || req.getIdNumber().isBlank())
                && (req.getPassportNumber() == null || req.getPassportNumber().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Please provide either an Identity Number or a Passport Number.");
        }

        EmployeeLevel level = null; // levels no longer drive salary — HR sets it per employee after signup
        Department department = req.getDepartment() == null ? null
                : departmentRepository.findByNameIgnoreCase(req.getDepartment()).orElse(null);

        // Postal address falls back to the residential address when left
        // blank, matching the frontend's "same as residential" checkbox.
        boolean postalProvided = req.getPostStreetNumber() != null && !req.getPostStreetNumber().isBlank();

        Employee employee = Employee.builder()
                .employeeCode(employeeRepository.nextEmployeeCode())
                .title(req.getTitle())
                .firstName(req.getFirstName())
                .secondName(req.getSecondName())
                .lastName(req.getLastName())
                .initials(req.getInitials())
                .dateOfBirth(req.getDateOfBirth())
                .idNumber(req.getIdNumber())
                .passportNumber(req.getPassportNumber())
                .passportCountry(req.getPassportCountry())
                .race(req.getRace())
                .relationshipStatus(req.getRelationshipStatus())
                .contactTelephone(req.getContactTelephone())
                .contactCellphone(req.getContactCellphone())
                .emergencyContactName(req.getEmergencyContactName())
                .emergencyContactTelephone(req.getEmergencyContactTelephone())
                .emergencyContactCellphone(req.getEmergencyContactCellphone())
                .taxOffice(req.getTaxOffice())
                .incomeTaxNumber(req.getIncomeTaxNumber())
                .bankAccountType(req.getBankAccountType())
                .bankBranchCode(req.getBankBranchCode())
                .bankName(req.getBankName())
                .bankBranchName(req.getBankBranchName())
                .bankAccountNumber(req.getBankAccountNumber())
                .bankAccountHolder(req.getBankAccountHolder())
                .bankAccountRelationship(req.getBankAccountRelationship())
                .resUnitNumber(req.getResUnitNumber())
                .resComplexName(req.getResComplexName())
                .resStreetNumber(req.getResStreetNumber())
                .resStreetName(req.getResStreetName())
                .resSuburb(req.getResSuburb())
                .resCity(req.getResCity())
                .resPostalCode(req.getResPostalCode())
                .postalService(req.getPostalService())
                .postalNumber(req.getPostalNumber())
                .postStreetNumber(postalProvided ? req.getPostStreetNumber() : req.getResStreetNumber())
                .postStreetName(postalProvided ? req.getPostStreetName() : req.getResStreetName())
                .postSuburb(postalProvided ? req.getPostSuburb() : req.getResSuburb())
                .postCity(postalProvided ? req.getPostCity() : req.getResCity())
                .postPostalCode(postalProvided ? req.getPostPostalCode() : req.getResPostalCode())
                .email(email)
                .phone(req.getPhone())
                .position(req.getPosition())
                .department(department)
                .level(level)
                .salary(BigDecimal.ZERO)
                .startDate(LocalDate.now())
                .agreedToTerms(Boolean.TRUE.equals(req.getAgreedToTerms()))
                .termsAgreedAt(java.time.LocalDateTime.now())
                .onboardingSignature(req.getSignature())
                .build();
        employee = employeeRepository.save(employee);

        seedDefaultLeaveBalances(employee);

        AppUser user = AppUser.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(Role.EMPLOYEE)
                .employee(employee)
                .build();
        userRepository.save(user);

        String token = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(token)
                .role(user.getRole())
                .employeeCode(employee.getEmployeeCode())
                .fullName(employee.getFullName())
                .email(email)
                .build();
    }

    public AuthResponse login(LoginRequest req) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getEmail().trim().toLowerCase(), req.getPassword()));
        } catch (BadCredentialsException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
        }

        AppUser user = userRepository.findByEmail(req.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password."));

        String token = jwtService.generateToken(user);
        Employee employee = user.getEmployee();
        return AuthResponse.builder()
                .token(token)
                .role(user.getRole())
                .employeeCode(employee != null ? employee.getEmployeeCode() : null)
                .fullName(employee != null ? employee.getFullName() : user.getEmail())
                .email(user.getEmail())
                .build();
    }

    private void seedDefaultLeaveBalances(Employee employee) {
        List<LeaveType> types = leaveTypeRepository.findAll();
        for (LeaveType type : types) {
            int defaultDays = switch (type.getName()) {
                case "Annual Leave" -> 15;
                case "Sick Leave" -> 10;
                case "Family Responsibility Leave" -> 3;
                default -> 0;
            };
            leaveBalanceRepository.save(LeaveBalance.builder()
                    .employee(employee)
                    .leaveType(type)
                    .daysRemaining(defaultDays)
                    .build());
        }
    }
}
