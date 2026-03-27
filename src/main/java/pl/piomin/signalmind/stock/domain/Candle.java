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

// SM-19: vwap, vwapUpper, vwapLower, rsi added via V8 migration
// SM-20: is_synthetic added via V9 migration

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

    // SM-19: indicator columns (nullable — HIST candles have no indicators)
    @Column(name = "vwap", precision = 12, scale = 4)
    private BigDecimal vwap;

    @Column(name = "vwap_upper", precision = 12, scale = 4)
    private BigDecimal vwapUpper;

    @Column(name = "vwap_lower", precision = 12, scale = 4)
    private BigDecimal vwapLower;

    @Column(name = "rsi", precision = 6, scale = 2)
    private BigDecimal rsi;

    // SM-20: flag for synthetic candles generated when no ticks arrive in a 1-min window
    @Column(name = "is_synthetic", nullable = false)
    private boolean synthetic = false;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected Candle() {
    }

    /** HIST candle constructor — indicators left null. */
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

    /** LIVE candle constructor — all OHLCV + indicator fields provided (SM-19). */
    public Candle(Stock stock, Instant candleTime,
                  BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                  long volume, CandleSource source,
                  BigDecimal vwap, BigDecimal vwapUpper, BigDecimal vwapLower, BigDecimal rsi) {
        this.stock = stock;
        this.candleTime = candleTime;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.source = source;
        this.vwap = vwap;
        this.vwapUpper = vwapUpper;
        this.vwapLower = vwapLower;
        this.rsi = rsi;
    }

    /**
     * Factory for synthetic candles (SM-20).
     *
     * <p>A synthetic candle is produced when no ticks arrive in a 1-minute window.
     * OHLC = prevClose, Volume = 0, isSynthetic = true.
     * Synthetic candles are persisted for continuity but must NOT trigger signal detection.
     */
    public static Candle synthetic(Stock stock, Instant slotStart, BigDecimal prevClose,
                                   BigDecimal vwap, BigDecimal vwapUpper, BigDecimal vwapLower) {
        Candle c = new Candle(stock, slotStart,
                prevClose, prevClose, prevClose, prevClose,
                0L, CandleSource.LIVE,
                vwap, vwapUpper, vwapLower, null);
        c.synthetic = true;
        return c;
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

    public BigDecimal getVwap() {
        return vwap;
    }

    public BigDecimal getVwapUpper() {
        return vwapUpper;
    }

    public BigDecimal getVwapLower() {
        return vwapLower;
    }

    public BigDecimal getRsi() {
        return rsi;
    }

    public boolean isSynthetic() {
        return synthetic;
    }

    @Override
    public String toString() {
        return "Candle{stock=" + (stock != null ? stock.getSymbol() : "?")
                + ", time=" + candleTime
                + ", close=" + close + "}";
    }
}
