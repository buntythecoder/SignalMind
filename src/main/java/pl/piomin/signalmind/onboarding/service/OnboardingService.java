package pl.piomin.signalmind.onboarding.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import pl.piomin.signalmind.auth.domain.AppUser;
import pl.piomin.signalmind.auth.repository.AppUserRepository;
import pl.piomin.signalmind.integration.telegram.TelegramProperties;
import pl.piomin.signalmind.onboarding.domain.UserWatchlistEntry;
import pl.piomin.signalmind.onboarding.domain.UserWatchlistId;
import pl.piomin.signalmind.onboarding.dto.OnboardingStatusResponse;
import pl.piomin.signalmind.onboarding.dto.StockDto;
import pl.piomin.signalmind.onboarding.repository.UserWatchlistRepository;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.StockRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the post-registration onboarding flow: disclaimer acceptance,
 * Telegram connection verification, and watchlist management (SM-34).
 */
@Service
public class OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    private static final String TELEGRAM_API = "https://api.telegram.org/bot";
    private static final String CONNECT_MSG  =
            "\u2705 SignalMind connected! You will receive signal alerts here.";

    private final AppUserRepository       userRepo;
    private final UserWatchlistRepository watchlistRepo;
    private final StockRepository         stockRepo;
    private final TelegramProperties      telegramProps;
    private final RestClient              restClient;

    public OnboardingService(AppUserRepository userRepo,
                             UserWatchlistRepository watchlistRepo,
                             StockRepository stockRepo,
                             TelegramProperties telegramProps) {
        this.userRepo       = userRepo;
        this.watchlistRepo  = watchlistRepo;
        this.stockRepo      = stockRepo;
        this.telegramProps  = telegramProps;
        this.restClient     = RestClient.create();
    }

    // ── Disclaimer ────────────────────────────────────────────────────────────

    /**
     * Records that the user has accepted the risk disclaimer.
     * Email must be verified before this step is allowed.
     *
     * @param userId the authenticated user's ID
     * @throws ResponseStatusException 400 if email is not yet verified
     * @throws ResponseStatusException 400 if the client passed accepted=false
     */
    @Transactional
    public void acceptDisclaimer(Long userId, boolean accepted) {
        if (!accepted) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Disclaimer must be explicitly accepted");
        }
        AppUser user = getUser(userId);
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Email must be verified before accepting the disclaimer");
        }
        user.acceptDisclaimer(Instant.now());
        userRepo.save(user);
        log.info("[onboarding] Disclaimer accepted by userId={}", userId);
    }

    // ── Telegram ──────────────────────────────────────────────────────────────

    /**
     * Attempts to verify a Telegram chat ID by sending a test message via the Bot API.
     *
     * <p>When {@code telegram.bot-token} is blank (development/testing without a live bot),
     * the API call is skipped and the chat is marked as verified immediately.
     *
     * @param userId the authenticated user's ID
     * @param chatId the Telegram chat ID supplied by the user
     * @return {@code true} if connection succeeded (or was skipped in dev mode)
     */
    @Transactional
    public boolean connectTelegram(Long userId, String chatId) {
        String botToken = telegramProps.getBotToken();

        if (botToken == null || botToken.isBlank()) {
            // Dev / test mode: skip the live API call, mark verified for convenience
            log.info("[onboarding] telegram.bot-token is blank — skipping API call, marking verified. userId={}", userId);
            AppUser user = getUser(userId);
            user.connectTelegram(chatId);
            userRepo.save(user);
            return true;
        }

        try {
            sendTelegramTestMessage(chatId, botToken);
            AppUser user = getUser(userId);
            user.connectTelegram(chatId);
            userRepo.save(user);
            log.info("[onboarding] Telegram connected for userId={} chatId={}", userId, chatId);
            return true;
        } catch (Exception e) {
            log.warn("[onboarding] Telegram connect failed for userId={} chatId={}: {}",
                    userId, chatId, e.getMessage());
            return false;
        }
    }

    // ── Watchlist ─────────────────────────────────────────────────────────────

    /**
     * Returns all active stocks annotated with whether the current user has them in their watchlist.
     *
     * @param userId the authenticated user's ID
     */
    @Transactional(readOnly = true)
    public List<StockDto> getWatchlist(Long userId) {
        List<Stock> allStocks = stockRepo.findAllByActiveTrue();
        Set<Long> watchedIds = watchlistRepo.findByIdUserId(userId).stream()
                .map(e -> e.getId().getStockId())
                .collect(Collectors.toSet());

        return allStocks.stream()
                .map(s -> new StockDto(
                        s.getId(),
                        s.getSymbol(),
                        s.getCompanyName(),
                        s.getSector(),
                        s.getIndexType().name(),
                        watchedIds.contains(s.getId())
                ))
                .toList();
    }

    /**
     * Replaces the user's entire watchlist with the supplied stock IDs.
     *
     * <p>An empty list means the user receives no stock-specific alerts.
     * A {@code null} list would mean "all stocks", but since the DTO enforces {@code @NotNull},
     * callers must pass an explicit list (including empty).
     *
     * @param userId   the authenticated user's ID
     * @param stockIds replacement list of stock IDs (may be empty)
     */
    @Transactional
    public void updateWatchlist(Long userId, List<Long> stockIds) {
        // Clear existing entries
        watchlistRepo.deleteByIdUserId(userId);

        // Insert new entries
        if (stockIds != null && !stockIds.isEmpty()) {
            Instant now = Instant.now();
            List<UserWatchlistEntry> entries = stockIds.stream()
                    .map(sid -> new UserWatchlistEntry(new UserWatchlistId(userId, sid), now))
                    .toList();
            watchlistRepo.saveAll(entries);
        }

        log.info("[onboarding] Watchlist updated for userId={} stockCount={}", userId,
                stockIds == null ? 0 : stockIds.size());
    }

    // ── Status ────────────────────────────────────────────────────────────────

    /**
     * Returns the current onboarding status for the authenticated user.
     *
     * @param userId the authenticated user's ID
     */
    @Transactional(readOnly = true)
    public OnboardingStatusResponse getStatus(Long userId) {
        AppUser user = getUser(userId);
        int watchlistSize = watchlistRepo.findByIdUserId(userId).size();
        return new OnboardingStatusResponse(
                user.getOnboardingStep(),
                user.isEmailVerified(),
                user.isDisclaimerAccepted(),
                user.isTelegramVerified(),
                user.isOnboardingComplete(),
                user.getName(),
                user.getEmail(),
                user.getTelegramChatId(),
                watchlistSize
        );
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private AppUser getUser(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found: " + userId));
    }

    private void sendTelegramTestMessage(String chatId, String botToken) {
        String url = TELEGRAM_API + botToken + "/sendMessage";
        Map<String, String> body = Map.of(
                "chat_id", chatId,
                "text",    CONNECT_MSG
        );
        restClient.post()
                .uri(url)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
