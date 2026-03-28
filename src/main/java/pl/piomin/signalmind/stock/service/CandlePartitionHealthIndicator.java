package pl.piomin.signalmind.stock.service;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * SM-46: Actuator health indicator for candle table partitions.
 *
 * <p>Reports {@code UP} when both the current month's and the next month's
 * partitions are present in the PostgreSQL catalogue. Reports {@code DOWN}
 * when either partition is missing, which means the nightly
 * {@link PartitionMaintenanceJob} has not yet run or failed to execute.
 *
 * <p>Exposed at {@code /actuator/health/partitionHealth} once the Actuator
 * is configured to show health component details.
 */
@Component("partitionHealth")
@ConditionalOnBean(DataSource.class)
public class CandlePartitionHealthIndicator implements HealthIndicator {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final JdbcTemplate jdbcTemplate;

    public CandlePartitionHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        YearMonth current = YearMonth.now(IST);
        YearMonth next    = current.plusMonths(1);

        String currentPartition = PartitionMaintenanceJob.partitionName(current);
        String nextPartition    = PartitionMaintenanceJob.partitionName(next);

        boolean currentOk = partitionExists(currentPartition);
        boolean nextOk    = partitionExists(nextPartition);

        if (currentOk && nextOk) {
            return Health.up()
                    .withDetail("current", currentPartition)
                    .withDetail("next",    nextPartition)
                    .build();
        }
        return Health.down()
                .withDetail("current_exists", currentOk)
                .withDetail("next_exists",    nextOk)
                .build();
    }

    private boolean partitionExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_class WHERE relname = ?",
                Integer.class,
                tableName);
        return count != null && count > 0;
    }
}
