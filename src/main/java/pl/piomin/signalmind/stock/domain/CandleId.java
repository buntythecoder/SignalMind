package pl.piomin.signalmind.stock.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Composite primary key for the {@link Candle} entity.
 *
 * <p>The {@code candles} table is RANGE-partitioned by {@code candle_time}, so
 * PostgreSQL requires the partition key to be part of the primary key:
 * {@code PRIMARY KEY (id, candle_time)}.
 */
public class CandleId implements Serializable {

    private Long id;
    private Instant candleTime;

    // JPA requires a no-arg constructor on @IdClass
    public CandleId() {
    }

    public CandleId(Long id, Instant candleTime) {
        this.id = id;
        this.candleTime = candleTime;
    }

    public Long getId() {
        return id;
    }

    public Instant getCandleTime() {
        return candleTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CandleId other)) return false;
        return Objects.equals(id, other.id) && Objects.equals(candleTime, other.candleTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, candleTime);
    }
}
