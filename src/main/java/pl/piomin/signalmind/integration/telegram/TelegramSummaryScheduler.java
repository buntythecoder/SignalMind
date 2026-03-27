package pl.piomin.signalmind.integration.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pl.piomin.signalmind.signal.repository.SignalRepository;
import pl.piomin.signalmind.signal.service.OutcomeTrackerService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Sends daily Telegram summary messages at market open and close (SM-28).
 *
 * <p>Only active when a bot token is configured.  Uses the {@link TelegramAlertService}
 * SPI (not a concrete impl) to remain decoupled from delivery details.
 *
 * <p>Both scheduled methods are conditioned on IST market hours (NSE):
 * <ul>
 *   <li>Morning: 09:10 IST Mon–Fri — market opens at 09:15 IST</li>
 *   <li>Evening: 15:35 IST Mon–Fri — market closes at 15:30 IST</li>
 * </ul>
 *
 * <p>The evening summary is enriched with win/loss stats from
 * {@link OutcomeTrackerService} (SM-32).
 */
@Service
@ConditionalOnProperty(prefix = "telegram", name = "bot-token", matchIfMissing = false)
public class TelegramSummaryScheduler {

    private static final Logger log = LoggerFactory.getLogger(TelegramSummaryScheduler.class);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final String MORNING_MSG =
            "\uD83D\uDCCA <b>SignalMind is live</b>\n" +
            "NSE market opens today. Monitoring 62 instruments across 7 signal types.\n" +
            "<i>Have a profitable session!</i>";

    private final TelegramAlertService  alertService;
    private final SignalRepository      signalRepository;
    private final OutcomeTrackerService outcomeTracker;

    public TelegramSummaryScheduler(TelegramAlertService alertService,
                                    SignalRepository signalRepository,
                                    OutcomeTrackerService outcomeTracker) {
        this.alertService     = alertService;
        this.signalRepository = signalRepository;
        this.outcomeTracker   = outcomeTracker;
    }

    /**
     * Sends a "market open" summary message at 09:10 IST on weekdays.
     * Cron: {@code 0 10 9 * * MON-FRI}.
     */
    @Scheduled(cron = "0 10 9 * * MON-FRI", zone = "Asia/Kolkata")
    public void sendMorningSummary() {
        log.info("[telegram-summary] Sending morning summary");
        alertService.sendAlert(MORNING_MSG);
    }

    /**
     * Sends an enriched "market close" summary at 15:35 IST on weekdays.
     * Includes total dispatched signals and the session win-rate computed by
     * {@link OutcomeTrackerService} (SM-32).
     * Cron: {@code 0 35 15 * * MON-FRI}.
     */
    @Scheduled(cron = "0 35 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void sendDailySummary() {
        LocalDate today        = LocalDate.now(IST);
        Instant   sessionStart = today.atTime(LocalTime.of(9, 15)).atZone(IST).toInstant();
        Instant   sessionEnd   = today.atTime(LocalTime.of(15, 35)).atZone(IST).toInstant();

        long dispatched = signalRepository.countByDispatchedTrueAndGeneratedAtBetween(
                sessionStart, sessionEnd);

        OutcomeTrackerService.WinRateSummary summary =
                outcomeTracker.getDailySummary(sessionStart, sessionEnd);

        String message = "\uD83D\uDCC8 <b>Market session ended</b>\n"
                + "Signals today: " + summary.total() + "\n"
                + "Wins: " + summary.wins()
                + " | Losses: " + summary.losses()
                + " | Expired: " + summary.expires() + "\n"
                + "Win rate: " + summary.formattedWinRate() + "\n"
                + "Thank you for using SignalMind!";

        log.info("[telegram-summary] Sending daily summary: {} signals dispatched, "
                + "{} outcomes recorded, win-rate={}",
                dispatched, summary.total(), summary.formattedWinRate());
        alertService.sendAlert(message);
    }
}
