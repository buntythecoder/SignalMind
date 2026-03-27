package pl.piomin.signalmind.signal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.piomin.signalmind.signal.domain.Signal;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.stock.domain.Stock;

import java.time.Instant;
import java.util.List;

/**
 * Data access for {@link Signal} entities.
 */
public interface SignalRepository extends JpaRepository<Signal, Long> {

    /**
     * Guards against duplicate signals: returns {@code true} when a signal for the
     * given stock and detector type was already generated within the provided
     * time window (typically today's trading session bounds).
     *
     * @param stock the stock to check
     * @param type  the signal detector classification
     * @param from  session window start (inclusive)
     * @param to    session window end (exclusive)
     * @return {@code true} if a signal already exists for this stock/type today
     */
    boolean existsByStockAndSignalTypeAndGeneratedAtBetween(Stock stock, SignalType type,
                                                             Instant from, Instant to);

    /**
     * Finds signals that have not yet been dispatched and whose validity window
     * has not expired.  Used by the dispatch worker to retry sending undelivered
     * alerts after transient failures.
     *
     * @param now current instant used to filter out expired signals
     * @return non-dispatched, still-valid signals ordered oldest-generated first
     */
    List<Signal> findByDispatchedFalseAndValidUntilAfterOrderByGeneratedAtAsc(Instant now);
}
