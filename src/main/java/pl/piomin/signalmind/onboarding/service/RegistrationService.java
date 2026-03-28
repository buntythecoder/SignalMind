package pl.piomin.signalmind.onboarding.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.piomin.signalmind.auth.domain.AppUser;
import pl.piomin.signalmind.auth.repository.AppUserRepository;
import pl.piomin.signalmind.onboarding.dto.RegistrationRequest;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles self-service user registration and email-address verification (SM-34).
 *
 * <p>In V1 (without an SMTP service), the verification link is written to the application log.
 * In production, replace the log statement with an email-delivery call.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final AppUserRepository userRepo;
    private final PasswordEncoder   passwordEncoder;

    public RegistrationService(AppUserRepository userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo        = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new user account.
     *
     * <p>A unique email-verification token is generated and stored.
     * The verification link is currently logged (no SMTP in V1).
     *
     * @param req validated registration payload
     * @return the persisted {@link AppUser} (not yet email-verified)
     * @throws ResponseStatusException 409 CONFLICT if the email is already registered
     */
    @Transactional
    public AppUser register(RegistrationRequest req) {
        if (userRepo.findByEmail(req.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        String hash = passwordEncoder.encode(req.password());
        AppUser user = new AppUser(req.email(), hash, "USER");
        user.setName(req.name());
        user.setPhone(req.phone());

        // Generate a URL-safe token (hex UUID, 32 chars)
        String token = UUID.randomUUID().toString().replace("-", "");
        user.setEmailVerificationToken(token);
        user.setEmailVerificationSentAt(Instant.now());

        AppUser saved = userRepo.save(user);

        // TODO (SM-35 / SMTP): send verification email instead of logging the link
        log.info("[registration] New user registered email={} verificationToken={}", req.email(), token);
        log.info("[registration] Verification link (dev only): /api/auth/verify-email?token={}", token);

        return saved;
    }

    /**
     * Marks the user's email as verified after they follow the verification link.
     *
     * @param token the email-verification token from the link query parameter
     * @throws ResponseStatusException 400 BAD_REQUEST if the token is unknown or already consumed
     */
    @Transactional
    public void verifyEmail(String token) {
        AppUser user = userRepo.findByEmailVerificationToken(token)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid or expired verification token"));

        user.verifyEmail();
        userRepo.save(user);

        log.info("[registration] Email verified for user email={}", user.getEmail());
    }
}
