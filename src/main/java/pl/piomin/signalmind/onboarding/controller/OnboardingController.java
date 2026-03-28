package pl.piomin.signalmind.onboarding.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.piomin.signalmind.auth.domain.AppUser;
import pl.piomin.signalmind.auth.repository.AppUserRepository;
import pl.piomin.signalmind.onboarding.dto.DisclaimerAcceptRequest;
import pl.piomin.signalmind.onboarding.dto.OnboardingStatusResponse;
import pl.piomin.signalmind.onboarding.dto.StockDto;
import pl.piomin.signalmind.onboarding.dto.TelegramConnectRequest;
import pl.piomin.signalmind.onboarding.dto.WatchlistUpdateRequest;
import pl.piomin.signalmind.onboarding.service.OnboardingService;

import java.util.List;
import java.util.Map;

/**
 * Authenticated endpoints for the post-registration onboarding flow (SM-34).
 *
 * <p>All endpoints require a valid JWT (HttpOnly cookie).
 * The current user is resolved from the Spring Security {@link Authentication} principal.
 */
@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService   onboardingService;
    private final AppUserRepository   userRepo;

    public OnboardingController(OnboardingService onboardingService,
                                AppUserRepository userRepo) {
        this.onboardingService = onboardingService;
        this.userRepo          = userRepo;
    }

    /**
     * Returns the current onboarding status for the authenticated user.
     */
    @GetMapping("/status")
    public ResponseEntity<OnboardingStatusResponse> getStatus(Authentication auth) {
        Long userId = resolveUserId(auth);
        return ResponseEntity.ok(onboardingService.getStatus(userId));
    }

    /**
     * Records disclaimer acceptance.
     * Requires email to be verified first (enforced by the service layer).
     */
    @PostMapping("/disclaimer")
    public ResponseEntity<Void> acceptDisclaimer(@Valid @RequestBody DisclaimerAcceptRequest req,
                                                 Authentication auth) {
        Long userId = resolveUserId(auth);
        onboardingService.acceptDisclaimer(userId, Boolean.TRUE.equals(req.accepted()));
        return ResponseEntity.ok().build();
    }

    /**
     * Connects the user's Telegram account by verifying the supplied chat ID.
     *
     * @return JSON body {@code {connected: true|false}}
     */
    @PostMapping("/telegram")
    public ResponseEntity<Map<String, Boolean>> connectTelegram(
            @Valid @RequestBody TelegramConnectRequest req,
            Authentication auth) {
        Long userId = resolveUserId(auth);
        boolean connected = onboardingService.connectTelegram(userId, req.chatId());
        return ResponseEntity.ok(Map.of("connected", connected));
    }

    /**
     * Returns all active stocks with an {@code inWatchlist} flag for the current user.
     */
    @GetMapping("/watchlist")
    public ResponseEntity<List<StockDto>> getWatchlist(Authentication auth) {
        Long userId = resolveUserId(auth);
        return ResponseEntity.ok(onboardingService.getWatchlist(userId));
    }

    /**
     * Replaces the user's entire watchlist with the supplied stock IDs.
     */
    @PutMapping("/watchlist")
    public ResponseEntity<Void> updateWatchlist(@Valid @RequestBody WatchlistUpdateRequest req,
                                                Authentication auth) {
        Long userId = resolveUserId(auth);
        onboardingService.updateWatchlist(userId, req.stockIds());
        return ResponseEntity.ok().build();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Resolves the authenticated user's database ID from the Spring Security principal.
     * The principal name is the user's email (set by {@code JwtAuthenticationFilter}).
     */
    private Long resolveUserId(Authentication auth) {
        String email = auth.getName();
        AppUser user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"));
        return user.getId();
    }
}
