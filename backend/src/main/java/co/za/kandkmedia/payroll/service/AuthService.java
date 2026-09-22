package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.*;
import co.za.kandkmedia.payroll.dto.AuthResponse;
import co.za.kandkmedia.payroll.dto.LoginRequest;
import co.za.kandkmedia.payroll.dto.ResendVerificationRequest;
import co.za.kandkmedia.payroll.dto.SignupRequest;
import co.za.kandkmedia.payroll.dto.VerifyEmailRequest;
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
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration CODE_VALIDITY = Duration.ofMinutes(15);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final AppUserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailService emailService;

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
                .office(req.getOffice())
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

        String code = generateCode();
        AppUser user = AppUser.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(Role.EMPLOYEE)
                .employee(employee)
                .emailVerified(false)
                .verificationCode(code)
                .verificationCodeExpiresAt(LocalDateTime.now().plus(CODE_VALIDITY))
                .verificationCodeSentAt(LocalDateTime.now())
                .build();
        userRepository.save(user);

        // Thrown inside the @Transactional method — the whole signup (employee, user, leave
        // balances) rolls back rather than leaving behind an account nobody can ever verify.
        if (!emailService.sendVerificationCode(email, employee.getFirstName(), code)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Couldn't send your verification email — please try signing up again in a moment.");
        }

        return AuthResponse.builder()
                .token(null)
                .role(user.getRole())
                .employeeCode(employee.getEmployeeCode())
                .fullName(employee.getFullName())
                .email(email)
                .emailVerificationRequired(true)
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

        if (!user.isEmailVerified()) {
            return AuthResponse.builder()
                    .token(null)
                    .role(user.getRole())
                    .email(user.getEmail())
                    .fullName(user.getEmployee() != null ? user.getEmployee().getFullName() : user.getEmail())
                    .emailVerificationRequired(true)
                    .build();
        }

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

    /** Confirms the code emailed at signup (or by resendVerification) and, on success, logs the account in. */
    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest req) {
        AppUser user = userRepository.findByEmail(req.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email or code."));

        if (user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This account is already verified — please log in.");
        }
        if (user.getVerificationCode() == null || user.getVerificationCodeExpiresAt() == null
                || LocalDateTime.now().isAfter(user.getVerificationCodeExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That code has expired — request a new one.");
        }
        if (!user.getVerificationCode().equals(req.getCode().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incorrect verification code.");
        }

        user.setEmailVerified(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiresAt(null);
        user.setVerificationCodeSentAt(null);
        userRepository.save(user);

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

    /** Re-sends a fresh code, replacing whatever was issued before — throttled so it can't be used to spam an inbox. */
    @Transactional
    public void resendVerification(ResendVerificationRequest req) {
        AppUser user = userRepository.findByEmail(req.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email."));

        if (user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This account is already verified — please log in.");
        }
        if (user.getVerificationCodeSentAt() != null
                && Duration.between(user.getVerificationCodeSentAt(), LocalDateTime.now()).compareTo(RESEND_COOLDOWN) < 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait a moment before requesting another code.");
        }

        String code = generateCode();
        user.setVerificationCode(code);
        user.setVerificationCodeExpiresAt(LocalDateTime.now().plus(CODE_VALIDITY));
        user.setVerificationCodeSentAt(LocalDateTime.now());
        userRepository.save(user);

        String firstName = user.getEmployee() != null ? user.getEmployee().getFirstName() : "there";
        if (!emailService.sendVerificationCode(user.getEmail(), firstName, code)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Couldn't send the email — please try again in a moment.");
        }
    }

    /** Six digits, zero-padded — simple to type from an email on a phone. */
    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
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
