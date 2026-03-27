package pl.piomin.signalmind;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SM-11: Integration test that verifies all Flyway migrations (V1–V5) apply
 * cleanly against a real PostgreSQL 16 instance spun up by Testcontainers.
 *
 * <p>Also validates that Hibernate's {@code ddl-auto: validate} agrees with the
 * migrated schema — if the JPA entity mappings diverge from the DDL, the
 * application context will fail to load here before a bad deployment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@Testcontainers
class FlywayMigrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("signalmind_test")
                    .withUsername("signalmind")
                    .withPassword("signalmind_test_pw");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    // ── Flyway history ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Flyway applies exactly 7 migrations (V1-V7) with no failures")
    void flyway_sevenMigrationsAppliedSuccessfully() {
        MigrationInfo[] applied = flyway.info().applied();
        assertEquals(7, applied.length, "Expected V1 through V7 to be applied");
        for (MigrationInfo m : applied) {
            assertEquals(
                    MigrationState.SUCCESS, m.getState(),
                    "Migration " + m.getVersion() + " (" + m.getDescription() + ") should be SUCCESS"
            );
        }
    }

    // ── V1: reference tables ──────────────────────────────────────────────────

    @Test
    @DisplayName("V1: stocks table exists with symbol unique constraint")
    void v1_stocksTableExistsWithUniqueSymbol() {
        assertTableExists("stocks");
        assertColumnExists("stocks", "symbol");
        assertColumnExists("stocks", "index_type");
        assertColumnExists("stocks", "breeze_code");
        assertColumnExists("stocks", "angel_token");
        assertColumnExists("stocks", "active");
        assertColumnExists("stocks", "created_at");

        assertColumnExists("stocks", "sector");
        // Unique constraint on symbol
        assertConstraintExists("uq_stocks_symbol");
        // CHECK on index_type
        assertConstraintExists("chk_stocks_index_type");
    }

    @Test
    @DisplayName("V1: market_holidays table exists with unique date constraint")
    void v1_marketHolidaysTableExists() {
        assertTableExists("market_holidays");
        assertConstraintExists("uq_market_holidays_date");
    }

    // ── V2: partitioned candles ───────────────────────────────────────────────

    @Test
    @DisplayName("V2: candles is a range-partitioned table")
    void v2_candlesIsPartitionedTable() {
        assertTableExists("candles");
        Integer partitioned = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_class WHERE relname = 'candles' AND relkind = 'p'",
                Integer.class);
        assertEquals(1, partitioned, "candles should be a partitioned table (pg_class.relkind = 'p')");
    }

    @Test
    @DisplayName("V2: initial monthly partitions candles_2024_01 and candles_2024_02 exist")
    void v2_initialCandlePartitionsExist() {
        assertTableExists("candles_2024_01");
        assertTableExists("candles_2024_02");
        // Both should be of kind 'r' (regular table / partition)
        Integer partitionCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_inherits i "
                        + "JOIN pg_class c ON c.oid = i.inhrelid "
                        + "WHERE c.relname IN ('candles_2024_01','candles_2024_02')",
                Integer.class);
        assertEquals(2, partitionCount, "Both initial partitions should inherit from candles");
    }

    // ── V3: volume baselines ──────────────────────────────────────────────────

    @Test
    @DisplayName("V3: volume_baselines table exists with stock_id FK and unique slot constraint")
    void v3_volumeBaselinesTableExists() {
        assertTableExists("volume_baselines");
        assertColumnExists("volume_baselines", "stock_id");
        assertColumnExists("volume_baselines", "slot_time");
        assertColumnExists("volume_baselines", "avg_volume");
        assertConstraintExists("uq_volume_baselines_stock_slot");
    }

    // ── V4: signals ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("V4: signals table exists with correct CHECK constraints")
    void v4_signalsTableExistsWithConstraints() {
        assertTableExists("signals");
        assertColumnExists("signals", "signal_type");
        assertColumnExists("signals", "direction");
        assertColumnExists("signals", "confidence");
        assertColumnExists("signals", "regime");
        assertColumnExists("signals", "dispatched");
        assertConstraintExists("chk_signals_type");
        assertConstraintExists("chk_signals_direction");
        assertConstraintExists("chk_signals_confidence");
        assertConstraintExists("chk_signals_regime");
    }

    // ── V5: users and audit ───────────────────────────────────────────────────

    @Test
    @DisplayName("V5: users table exists with unique email and role CHECK")
    void v5_usersTableExists() {
        assertTableExists("users");
        assertColumnExists("users", "email");
        assertColumnExists("users", "password_hash");
        assertColumnExists("users", "role");
        assertConstraintExists("uq_users_email");
        assertConstraintExists("chk_users_role");
    }

    @Test
    @DisplayName("V5: audit_log table exists")
    void v5_auditLogTableExists() {
        assertTableExists("audit_log");
        assertColumnExists("audit_log", "event_type");
        assertColumnExists("audit_log", "entity_type");
        assertColumnExists("audit_log", "entity_id");
    }

    // ── Total table count ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Schema contains exactly 9 business tables (7 core + 2 partitions); excludes flyway_schema_history")
    void schema_correctTotalTableCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' "
                        + "  AND table_type = 'BASE TABLE' "
                        + "  AND table_name NOT LIKE 'flyway%' "
                        + "  AND table_name NOT LIKE 'batch_%'",
                Integer.class);
        // stocks, market_holidays, candles, candles_2024_01, candles_2024_02,
        // volume_baselines, signals, users, audit_log = 9
        // (spring_batch_* tables are excluded by the NOT LIKE 'batch_%' filter)
        // V7 is a data-only migration (holiday seed) — no new tables
        assertEquals(9, count, "Expected 9 business tables in public schema after V1-V7 migrations");
    }

    // ── V7: holiday seed ──────────────────────────────────────────────────────

    @Test
    @DisplayName("V7: market_holidays seeded with 2025-2026 NSE holidays")
    void v7_marketHolidaysSeeded() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM market_holidays", Integer.class);
        assertTrue(count != null && count >= 20,
                "Expected at least 20 seeded holidays (14 for 2025 + 11 for 2026), got: " + count);
    }

    @Test
    @DisplayName("V7: known 2025 holidays are present — Good Friday and Diwali Laxmi Puja")
    void v7_specific2025HolidaysPresent() {
        Integer goodFriday = jdbc.queryForObject(
                "SELECT COUNT(*) FROM market_holidays WHERE holiday_date = '2025-04-18'",
                Integer.class);
        assertEquals(1, goodFriday, "Good Friday 2025 should be seeded");

        Integer diwali = jdbc.queryForObject(
                "SELECT COUNT(*) FROM market_holidays WHERE holiday_date = '2025-10-20'",
                Integer.class);
        assertEquals(1, diwali, "Diwali 2025 Laxmi Puja should be seeded");
    }

    // ── Seed verification ─────────────────────────────────────────────────────

    @Test
    @DisplayName("StockSeedService seeds exactly 62 instruments from stocks.json on startup")
    void stockSeedService_seeds62Instruments() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM stocks", Integer.class);
        assertEquals(62, count,
                "Expected 62 instruments (50 Nifty50 + 10 BankNifty + NIFTY index + INDIA VIX)");
    }

    @Test
    @DisplayName("Seeded instruments include expected Nifty50 anchor symbols")
    void stockSeedService_nifty50AnchorSymbolsPresent() {
        for (String symbol : new String[]{"RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK"}) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM stocks WHERE symbol = ?", Integer.class, symbol);
            assertEquals(1, count, "Symbol " + symbol + " should be seeded");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertTableExists(String tableName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = ?",
                Integer.class, tableName);
        assertTrue(count != null && count > 0,
                "Table '" + tableName + "' should exist in the public schema");
    }

    private void assertColumnExists(String tableName, String columnName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                Integer.class, tableName, columnName);
        assertTrue(count != null && count > 0,
                "Column '" + columnName + "' should exist in table '" + tableName + "'");
    }

    private void assertConstraintExists(String constraintName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = 'public' AND constraint_name = ?",
                Integer.class, constraintName);
        assertTrue(count != null && count > 0,
                "Constraint '" + constraintName + "' should exist");
    }
}
