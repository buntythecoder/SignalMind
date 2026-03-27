package pl.piomin.signalmind.stock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One-minute OHLCV candle stored in the {@code candles} table.
 *
 * <p>The table is RANGE-partitioned by {@code candle_time} (month boundaries).
 * PostgreSQL mandates that the partition key is included in the primary key,
 * hence the composite PK {@code (id, candle_time)} mapped via {@link CandleId}.
 *
 * <p>Source codes:
 * <ul>
 *   <li>{@code HIST} — seeded from ICICI Breeze historical API (this module)</li>
 *   <li>{@code LIVE} — filled in real-time from Angel One WebSocket (SM-13)</li>
 * </ul>
 */
@Entity
@IdClass(CandleId.class)
@Table(name = "candles")
public class Candle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Id
    @Column(name = "candle_time", nullable = false)
    private Instant candleTime;

    @ManyToOne(optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "open", nullable = false, precision = 12, scale = 2)
    private BigDecimal open;

    @Column(name = "high", nullable = false, precision = 12, scale = 2)
    private BigDecimal high;

    @Column(name = "low", nullable = false, precision = 12, scale = 2)
    private BigDecimal low;

    @Column(name = "close", nullable = false, precision = 12, scale = 2)
    private BigDecimal close;

    @Column(name = "volume", nullable = false)
    private long volume;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private CandleSource source = CandleSource.HIST;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected Candle() {
    }

    public Candle(Stock stock, Instant candleTime,
                  BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                  long volume, CandleSource source) {
        this.stock = stock;
        this.candleTime = candleTime;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.source = source;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public Instant getCandleTime() {
        return candleTime;
    }

    public Stock getStock() {
        return stock;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public long getVolume() {
        return volume;
    }

    public CandleSource getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "Candle{stock=" + (stock != null ? stock.getSymbol() : "?")
                + ", time=" + candleTime
                + ", close=" + close + "}";
    }
}
