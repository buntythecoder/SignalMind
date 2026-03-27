package pl.piomin.signalmind.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.CandleId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CandleRepository extends JpaRepository<Candle, CandleId> {

    /**
     * Returns candles for a stock within the given time window, newest first.
     * The {@code candle_time} range predicate enables partition pruning.
     */
    @Query("""
            SELECT c FROM Candle c
            WHERE c.stock.id = :stockId
              AND c.candleTime >= :from
              AND c.candleTime < :to
            ORDER BY c.candleTime DESC
            """)
    List<Candle> findByStockAndTimeRange(@Param("stockId") Long stockId,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to);

    /**
     * Returns the N most-recent candles for a stock, ordered newest first.
     */
    @Query("""
            SELECT c FROM Candle c
            WHERE c.stock.id = :stockId
            ORDER BY c.candleTime DESC
            LIMIT :n
            """)
    List<Candle> findLatestByStock(@Param("stockId") Long stockId, @Param("n") int n);

    /**
     * Count candles per stock_id for a time range (for daily cross-check, SM-20).
     * Returns List of Object[] where [0] = stockId (Long), [1] = count (Long).
     */
    @Query("""
            SELECT c.stock.id, COUNT(c)
            FROM Candle c
            WHERE c.candleTime >= :start AND c.candleTime < :end
            GROUP BY c.stock.id
            """)
    List<Object[]> countCandlesPerStockBetween(@Param("start") Instant start,
                                               @Param("end") Instant end);

    /**
     * Returns the single most-recent candle for a stock (SM-20: synthetic candle prev-close lookup).
     */
    @Query("""
            SELECT c FROM Candle c
            WHERE c.stock.id = :stockId
            ORDER BY c.candleTime DESC
            LIMIT 1
            """)
    Optional<Candle> findLatestCandle(@Param("stockId") Long stockId);

    /**
     * Returns the most-recent candle for a stock strictly before the given instant.
     * Used by gap-fill detectors (SM-25) to find the previous session's closing price.
     */
    @Query("""
            SELECT c FROM Candle c
            WHERE c.stock.id = :stockId
              AND c.candleTime < :before
            ORDER BY c.candleTime DESC
            LIMIT 1
            """)
    Optional<Candle> findPrevSessionClose(@Param("stockId") Long stockId,
                                           @Param("before") Instant before);
}
