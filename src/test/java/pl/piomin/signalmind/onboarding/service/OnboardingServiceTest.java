package pl.piomin.signalmind.onboarding.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import pl.piomin.signalmind.auth.domain.AppUser;
import pl.piomin.signalmind.auth.repository.AppUserRepository;
import pl.piomin.signalmind.integration.telegram.TelegramProperties;
import pl.piomin.signalmind.onboarding.domain.UserWatchlistEntry;
import pl.piomin.signalmind.onboarding.domain.UserWatchlistId;
import pl.piomin.signalmind.onboarding.dto.OnboardingStatusResponse;
import pl.piomin.signalmind.onboarding.dto.RegistrationRequest;
import pl.piomin.signalmind.onboarding.dto.StockDto;
import pl.piomin.signalmind.onboarding.repository.UserWatchlistRepository;
import pl.piomin.signalmind.stock.domain.IndexType;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.StockRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RegistrationService} and {@link OnboardingService} (SM-34).
 * No Spring context — uses Mockito for all collaborators.
 */
@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    // ── RegistrationService fixtures ──────────────────────────────────────────

    @Mock
    private AppUserRepository userRepo;

    private PasswordEncoder passwordEncoder;

    private RegistrationService registrationService;

    // ── OnboardingService fixtures ────────────────────────────────────────────

    @Mock
    private UserWatchlistRepository watchlistRepo;

    @Mock
    private StockRepository stockRepo;

    @Mock
    private TelegramProperties telegramProps;

    private OnboardingService onboardingService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4); // fast for tests
        registrationService = new RegistrationService(userRepo, passwordEncoder);
        onboardingService   = new OnboardingService(userRepo, watchlistRepo, stockRepo, telegramProps);
    }

    // ── RegistrationService: register ─────────────────────────────────────────

    @Test
    @DisplayName("register: creates user with hashed password and non-null verification token")
    void register_createsUserWithToken() {
        when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.empty());

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        when(userRepo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        RegistrationRequest req = new RegistrationRequest(
                "alice@example.com", "Alice Smith", "SecurePass1!", "+91-9000000000");

        AppUser saved = registrationService.register(req);

        AppUser captured = captor.getValue();
        assertThat(captured.getEmail()).isEqualTo("alice@example.com");
        assertThat(captured.getName()).isEqualTo("Alice Smith");
        assertThat(captured.getPhone()).isEqualTo("+91-9000000000");
        assertThat(passwordEncoder.matches("SecurePass1!", captured.getPasswordHash())).isTrue();
        assertThat(captured.getEmailVerificationToken()).isNotBlank();
        assertThat(captured.getEmailVerificationSentAt()).isNotNull();
        assertThat(captured.isEmailVerified()).isFalse();
        assertThat(captured.getOnboardingStep()).isEqualTo(0);
    }

    @Test
    @DisplayName("register: throws 409 when email already in use")
    void register_conflictOnDuplicateEmail() {
        AppUser existing = new AppUser("alice@example.com", "hash", "USER");
        when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));

        RegistrationRequest req = new RegistrationRequest(
                "alice@example.com", "Alice", "SecurePass1!", null);

        assertThatThrownBy(() -> registrationService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(userRepo, never()).save(any());
    }

    // ── RegistrationService: verifyEmail ─────────────────────────────────────

    @Test
    @DisplayName("verifyEmail: clears token and sets emailVerified=true")
    void verifyEmail_clearsTokenAndSetsVerified() {
        AppUser user = new AppUser("alice@example.com", "hash", "USER");
        user.setEmailVerificationToken("abc123token");

        when(userRepo.findByEmailVerificationToken("abc123token")).thenReturn(Optional.of(user));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        registrationService.verifyEmail("abc123token");

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerificationToken()).isNull();
        assertThat(user.getOnboardingStep()).isEqualTo(1);
    }

    @Test
    @DisplayName("verifyEmail: throws 400 for unknown token")
    void verifyEmail_throwsBadRequestForUnknownToken() {
        when(userRepo.findByEmailVerificationToken("badtoken")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.verifyEmail("badtoken"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── OnboardingService: acceptDisclaimer ───────────────────────────────────

    @Test
    @DisplayName("acceptDisclaimer: sets flag and advances step when email is verified")
    void acceptDisclaimer_setsFlag() {
        AppUser user = new AppUser("alice@example.com", "hash", "USER");
        user.verifyEmail(); // step = 1, emailVerified = true

        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        onboardingService.acceptDisclaimer(1L, true);

        assertThat(user.isDisclaimerAccepted()).isTrue();
        assertThat(user.getDisclaimerAcceptedAt()).isNotNull();
        assertThat(user.getOnboardingStep()).isEqualTo(2);
    }

    @Test
    @DisplayName("acceptDisclaimer: throws 400 when email is not verified")
    void acceptDisclaimer_throwsWhenEmailNotVerified() {
        AppUser user = new AppUser("alice@example.com", "hash", "USER");
        // email not verified

        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> onboardingService.acceptDisclaimer(1L, true))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── OnboardingService: updateWatchlist ────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("updateWatchlist: deletes existing entries and saves new ones")
    void updateWatchlist_replacesEntries() {
        when(watchlistRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        onboardingService.updateWatchlist(1L, List.of(10L, 20L, 30L));

        verify(watchlistRepo, times(1)).deleteByIdUserId(1L);

        ArgumentCaptor<List<UserWatchlistEntry>> captor =
                (ArgumentCaptor<List<UserWatchlistEntry>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        verify(watchlistRepo, times(1)).saveAll(captor.capture());
        List<UserWatchlistEntry> saved = captor.getValue();
        assertThat(saved).hasSize(3);
        assertThat(saved.stream().map(e -> e.getId().getStockId()).toList())
                .containsExactlyInAnyOrder(10L, 20L, 30L);
    }

    @Test
    @DisplayName("updateWatchlist: clears entries when stock list is empty")
    void updateWatchlist_clearsWhenEmpty() {
        onboardingService.updateWatchlist(1L, List.of());

        verify(watchlistRepo, times(1)).deleteByIdUserId(1L);
        verify(watchlistRepo, never()).saveAll(any());
    }

    // ── OnboardingService: getStatus ──────────────────────────────────────────

    @Test
    @DisplayName("getStatus: returns correct status for a fully onboarded user")
    void getStatus_fullyOnboarded() {
        AppUser user = new AppUser("alice@example.com", "hash", "USER");
        user.setName("Alice");
        user.verifyEmail();
        user.acceptDisclaimer(Instant.now());
        user.connectTelegram("123456789");

        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(watchlistRepo.findByIdUserId(1L)).thenReturn(
                List.of(new UserWatchlistEntry(new UserWatchlistId(1L, 5L), Instant.now()))
        );

        OnboardingStatusResponse status = onboardingService.getStatus(1L);

        assertThat(status.emailVerified()).isTrue();
        assertThat(status.disclaimerAccepted()).isTrue();
        assertThat(status.telegramVerified()).isTrue();
        assertThat(status.onboardingComplete()).isTrue();
        assertThat(status.step()).isEqualTo(3);
        assertThat(status.name()).isEqualTo("Alice");
        assertThat(status.telegramChatId()).isEqualTo("123456789");
        assertThat(status.watchlistSize()).isEqualTo(1);
    }

    // ── OnboardingService: getWatchlist ───────────────────────────────────────

    @Test
    @DisplayName("getWatchlist: sets inWatchlist=true only for stocks in user watchlist")
    void getWatchlist_flagsCorrectly() {
        Stock s1 = new Stock("RELIANCE", "Reliance Industries", IndexType.NIFTY50);
        setId(s1, 1L);
        Stock s2 = new Stock("TCS", "Tata Consultancy", IndexType.NIFTY50);
        setId(s2, 2L);

        when(stockRepo.findAllByActiveTrue()).thenReturn(List.of(s1, s2));
        when(watchlistRepo.findByIdUserId(42L)).thenReturn(
                List.of(new UserWatchlistEntry(new UserWatchlistId(42L, 1L), Instant.now()))
        );

        List<StockDto> result = onboardingService.getWatchlist(42L);

        assertThat(result).hasSize(2);
        assertThat(result.stream().filter(d -> d.symbol().equals("RELIANCE")).findFirst())
                .get().extracting(StockDto::inWatchlist).isEqualTo(true);
        assertThat(result.stream().filter(d -> d.symbol().equals("TCS")).findFirst())
                .get().extracting(StockDto::inWatchlist).isEqualTo(false);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Reflectively sets the @Id field on a Stock (no public setter). */
    private void setId(Stock stock, Long id) {
        try {
            var field = Stock.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(stock, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
