package pl.piomin.signalmind.integration.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pl.piomin.signalmind.signal.repository.SignalRepository;

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

    private final TelegramAlertService alertService;
    private final SignalRepository     signalRepository;

    public TelegramSummaryScheduler(TelegramAlertService alertService,
                                    SignalRepository signalRepository) {
        this.alertService     = alertService;
        this.signalRepository = signalRepository;
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
     * Sends a "market close" summary at 15:35 IST on weekdays, including
     * a count of signals dispatched during the session.
     * Cron: {@code 0 35 15 * * MON-FRI}.
     */
    @Scheduled(cron = "0 35 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void sendDailySummary() {
        LocalDate today = LocalDate.now(IST);
        Instant sessionStart = today.atTime(LocalTime.of(9, 15)).atZone(IST).toInstant();
        Instant sessionEnd   = today.atTime(LocalTime.of(15, 35)).atZone(IST).toInstant();

        long dispatched = signalRepository.countByDispatchedTrueAndGeneratedAtBetween(sessionStart, sessionEnd);

        String message = "\uD83D\uDCC8 <b>Market session ended</b>\n"
                + "Signals dispatched today: " + dispatched + "\n"
                + "Thank you for using SignalMind!";

        log.info("[telegram-summary] Sending daily summary: {} signals dispatched", dispatched);
        alertService.sendAlert(message);
    }
}
