package pl.piomin.signalmind.signal.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.piomin.signalmind.regime.service.MarketRegimeService;
import pl.piomin.signalmind.signal.domain.Signal;
import pl.piomin.signalmind.signal.domain.SignalStatus;
import pl.piomin.signalmind.signal.dto.DashboardStatsDto;
import pl.piomin.signalmind.signal.dto.SignalDto;
import pl.piomin.signalmind.signal.repository.SignalRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * REST endpoints consumed by the React real-time signal dashboard (SM-35).
 *
 * <p>{@link MarketRegimeService} is injected as {@code Optional<>} because it is
 * {@code @ConditionalOnBean(StringRedisTemplate.class)} — it will be absent in the
 * no-Redis test context, and the stats endpoint degrades gracefully to
 * {@code null} regime fields.
 */
@RestController
@RequestMapping("/api")
public class DashboardController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final Set<SignalStatus> WIN_STATUSES =
            EnumSet.of(SignalStatus.TARGET_1_HIT, SignalStatus.TARGET_2_HIT);
    private static final Set<SignalStatus> LOSS_STATUSES =
            EnumSet.of(SignalStatus.STOP_HIT);
    private static final Set<SignalStatus> ACTIVE_STATUSES =
            EnumSet.of(SignalStatus.GENERATED, SignalStatus.TRIGGERED);

    private final SignalRepository signalRepository;
    private final Optional<MarketRegimeService> regimeService;

    public DashboardController(SignalRepository signalRepository,
                               Optional<MarketRegimeService> regimeService) {
        this.signalRepository = signalRepository;
        this.regimeService    = regimeService;
    }

    /**
     * Returns all signals generated today (midnight-to-midnight IST), newest first.
     * Used for the initial dashboard page-load.
     *
     * @return list of {@link SignalDto}, may be empty on non-trading days
     */
    @GetMapping("/signals/today")
    public List<SignalDto> todaySignals() {
        Instant[] window = todayWindow();
        return signalRepository
                .findByGeneratedAtBetweenOrderByGeneratedAtDesc(window[0], window[1])
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Returns aggregated win-rate statistics and the current market regime
     * for the dashboard header.
     *
     * @return stats DTO; regime fields are {@code null} when Redis is unavailable
     */
    @GetMapping("/dashboard/stats")
    public DashboardStatsDto stats() {
        Instant[] window  = todayWindow();
        List<Signal> signals = signalRepository
                .findByGeneratedAtBetweenOrderByGeneratedAtDesc(window[0], window[1]);

        int wins   = (int) signals.stream().filter(s -> WIN_STATUSES.contains(s.getStatus())).count();
        int losses = (int) signals.stream().filter(s -> LOSS_STATUSES.contains(s.getStatus())).count();
        int active = (int) signals.stream().filter(s -> ACTIVE_STATUSES.contains(s.getStatus())).count();
        int completed = wins + losses;
        double winRate = completed == 0 ? 0.0 : (double) wins / completed;

        String  regime   = null;
        String  reason   = null;
        Instant regimeAt = null;

        if (regimeService.isPresent()) {
            var snap = regimeService.get().currentRegime();
            if (snap.isPresent()) {
                regime   = snap.get().regime().name();
                reason   = snap.get().reason();
                regimeAt = snap.get().calculatedAt();
            }
        }

        return new DashboardStatsDto(signals.size(), wins, losses, active,
                winRate, regime, reason, regimeAt);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Returns [start, end) covering today in IST (midnight to next midnight). */
    private Instant[] todayWindow() {
        LocalDate today = LocalDate.now(IST);
        return new Instant[]{
                today.atStartOfDay(IST).toInstant(),
                today.plusDays(1).atStartOfDay(IST).toInstant()
        };
    }

    private SignalDto toDto(Signal s) {
        return new SignalDto(
                s.getId(),
                s.getStock().getSymbol(),
                s.getStock().getCompanyName(),
                s.getStock().getSector(),
                s.getStock().getIndexType().name(),
                s.getSignalType(),
                s.getDirection(),
                s.getEntryPrice(),
                s.getTargetPrice(),
                s.getTarget2(),
                s.getStopLoss(),
                s.getConfidence(),
                s.getRegime(),
                s.getStatus(),
                s.getGeneratedAt(),
                s.getValidUntil()
        );
    }
}
