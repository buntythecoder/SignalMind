package pl.piomin.signalmind.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

@Entity
@Table(name = "market_holidays", uniqueConstraints = {
        @UniqueConstraint(name = "uq_market_holidays_date", columnNames = "holiday_date")
})
public class MarketHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "description", length = 100)
    private String description;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected MarketHoliday() {
    }

    public MarketHoliday(LocalDate holidayDate, String description) {
        this.holidayDate = holidayDate;
        this.description = description;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public String getDescription() {
        return description;
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setHolidayDate(LocalDate holidayDate) {
        this.holidayDate = holidayDate;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
