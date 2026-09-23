package co.za.kandkmedia.payroll.controller;

import co.za.kandkmedia.payroll.dto.AuthResponse;
import co.za.kandkmedia.payroll.dto.LoginRequest;
import co.za.kandkmedia.payroll.dto.ResendVerificationRequest;
import co.za.kandkmedia.payroll.dto.SignupRequest;
import co.za.kandkmedia.payroll.dto.VerifyEmailRequest;
import co.za.kandkmedia.payroll.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(authService.signup(request));
    }

    /** Checks an email while the signup form is being filled in: company domain, and not already registered. */
    @GetMapping("/check-email")
    public java.util.Map<String, Object> checkEmail(@RequestParam String email) {
        return authService.checkEmail(email);
    }

    /** The company domains accepted at signup, so the form's hints stay in sync with the server. */
    @GetMapping("/email-domains")
    public java.util.List<String> emailDomains() {
        return authService.allowedDomains();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Confirms the code emailed at signup and, on success, logs the account in. */
    @PostMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request));
    }

    /** Issues a fresh code when the first one expired or wasn't received. */
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
        return ResponseEntity.ok().build();
    }
}
