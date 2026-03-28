package pl.piomin.signalmind.stock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * SM-46: Automated monthly candle table partition maintenance.
 *
 * <p>Runs nightly at 00:01 IST and ensures that the current month's partition
 * and the next month's partition both exist in the {@code candles} parent
 * table. The CREATE TABLE uses {@code IF NOT EXISTS}, making each execution
 * fully idempotent — running it multiple times has no side-effects.
 *
 * <p>No Flyway migration is needed: partitions are created at runtime by this
 * job so that the schema automatically extends as months roll over.
 */
@Service
@ConditionalOnBean(DataSource.class)
public class PartitionMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenanceJob.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final JdbcTemplate jdbcTemplate;

    public PartitionMaintenanceJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Runs nightly at 00:01 IST. Creates the next month's partition (and current
     * month's) if they do not already exist. Idempotent via IF NOT EXISTS.
     */
    @Scheduled(cron = "0 1 0 * * *", zone = "Asia/Kolkata")
    public void ensurePartitionsExist() {
        YearMonth current = YearMonth.now(IST);
        ensurePartition(current);
        ensurePartition(current.plusMonths(1));
    }

    /**
     * Creates a monthly partition for the given YearMonth if it doesn't exist.
     *
     * @param month the target month for which the partition should be created
     */
    public void ensurePartition(YearMonth month) {
        String tableName = partitionName(month);
        String fromDate  = month.atDay(1).toString();                // e.g. 2026-03-01
        String toDate    = month.plusMonths(1).atDay(1).toString();  // e.g. 2026-04-01
        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS %s PARTITION OF candles " +
                "FOR VALUES FROM ('%s') TO ('%s')",
                tableName, fromDate, toDate);
        jdbcTemplate.execute(sql);
        log.info("[partition] Ensured partition: {}", tableName);
    }

    /**
     * Returns the partition table name for a given YearMonth, e.g. {@code candles_2026_03}.
     *
     * <p>Package-private and static so that {@link CandlePartitionHealthIndicator}
     * and tests can reference it without creating a full bean.
     *
     * @param month the month whose partition name to compute
     * @return partition table name in the form {@code candles_YYYY_MM}
     */
    public static String partitionName(YearMonth month) {
        return String.format("candles_%04d_%02d", month.getYear(), month.getMonthValue());
    }
}
