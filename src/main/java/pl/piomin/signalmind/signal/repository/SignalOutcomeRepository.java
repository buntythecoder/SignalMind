package pl.piomin.signalmind.signal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.piomin.signalmind.signal.domain.SignalOutcome;

import java.time.Instant;
import java.util.List;

/**
 * Data access for {@link SignalOutcome} entities (SM-32).
 */
public interface SignalOutcomeRepository extends JpaRepository<SignalOutcome, Long> {

    /**
     * Returns {@code true} when an outcome record already exists for this signal.
     * Used by {@link pl.piomin.signalmind.signal.service.OutcomeTrackerService}
     * to prevent duplicate outcome entries.
     *
     * @param signalId the signal ID to check
     * @return {@code true} if an outcome row already exists
     */
    boolean existsBySignalId(Long signalId);

    /**
     * Returns all outcomes recorded within the given time window (inclusive on both ends).
     * Used to compute win-rate summaries for the daily Telegram recap.
     *
     * @param from start of the window (inclusive)
     * @param to   end of the window (inclusive)
     * @return outcomes whose {@code recorded_at} falls within the window
     */
    List<SignalOutcome> findByRecordedAtBetween(Instant from, Instant to);
}
