package pl.piomin.signalmind.stock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "stocks", uniqueConstraints = {
        @UniqueConstraint(name = "uq_stocks_symbol", columnNames = "symbol")
})
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "index_type", nullable = false, length = 20)
    private IndexType indexType;

    @Column(name = "breeze_code", length = 20)
    private String breezeCode;

    @Column(name = "angel_token", length = 20)
    private String angelToken;

    @Column(name = "sector", length = 50)
    private String sector;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected Stock() {
    }

    public Stock(String symbol, String companyName, IndexType indexType) {
        this.symbol = symbol;
        this.companyName = companyName;
        this.indexType = indexType;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getCompanyName() {
        return companyName;
    }

    public IndexType getIndexType() {
        return indexType;
    }

    public String getBreezeCode() {
        return breezeCode;
    }

    public String getAngelToken() {
        return angelToken;
    }

    public String getSector() {
        return sector;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public void setIndexType(IndexType indexType) {
        this.indexType = indexType;
    }

    public void setBreezeCode(String breezeCode) {
        this.breezeCode = breezeCode;
    }

    public void setAngelToken(String angelToken) {
        this.angelToken = angelToken;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Stock{id=" + id + ", symbol='" + symbol + "', indexType=" + indexType + "}";
    }
}
