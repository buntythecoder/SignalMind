package pl.piomin.signalmind.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.CandleId;

import java.time.Instant;
import java.util.List;

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
}
