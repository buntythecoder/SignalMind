package pl.piomin.signalmind.onboarding.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.piomin.signalmind.auth.domain.AppUser;
import pl.piomin.signalmind.onboarding.dto.RegistrationRequest;
import pl.piomin.signalmind.onboarding.service.RegistrationService;

import java.util.Map;

/**
 * Public endpoints for self-service registration and email verification (SM-34).
 *
 * <p>These paths are listed in {@code SecurityConfig.PUBLIC_PATHS} (under {@code /api/auth/**})
 * and therefore require no JWT.
 */
@RestController
@RequestMapping("/api/auth")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * Registers a new user account.
     *
     * @return 201 Created with a confirmation message and the registered email address
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegistrationRequest req) {
        AppUser saved = registrationService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Registration successful. Check your email for verification link.",
                "email",   saved.getEmail()
        ));
    }

    /**
     * Verifies a user's email address using the token sent by the registration flow.
     *
     * @param token the email-verification token (from the query string)
     * @return 200 OK on success
     */
    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(
            @RequestParam @NotBlank String token) {
        registrationService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully"));
    }
}
