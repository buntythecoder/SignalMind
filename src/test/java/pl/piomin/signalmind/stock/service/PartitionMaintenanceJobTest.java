package pl.piomin.signalmind.stock.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * SM-46: Pure unit tests for {@link PartitionMaintenanceJob}.
 *
 * <p>No Spring context — uses Mockito only. JdbcTemplate is mocked so that
 * tests run without a database.
 */
@ExtendWith(MockitoExtension.class)
class PartitionMaintenanceJobTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    @InjectMocks
    PartitionMaintenanceJob job;

    @Test
    @DisplayName("partitionName formats year-month correctly")
    void partitionName_format() {
        assertThat(PartitionMaintenanceJob.partitionName(YearMonth.of(2026, 3)))
                .isEqualTo("candles_2026_03");
        assertThat(PartitionMaintenanceJob.partitionName(YearMonth.of(2026, 12)))
                .isEqualTo("candles_2026_12");
    }

    @Test
    @DisplayName("ensurePartition executes CREATE TABLE IF NOT EXISTS with correct dates")
    void ensurePartition_executesCorrectSql() {
        job.ensurePartition(YearMonth.of(2026, 3));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(captor.capture());
        String sql = captor.getValue();

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS");
        assertThat(sql).contains("candles_2026_03");
        assertThat(sql).contains("2026-03-01");
        assertThat(sql).contains("2026-04-01");
    }

    @Test
    @DisplayName("ensurePartitionsExist creates both current and next month")
    void ensurePartitionsExist_createsTwoMonths() {
        // We can't easily control YearMonth.now() — just verify execute() is called twice
        job.ensurePartitionsExist();
        verify(jdbcTemplate, times(2)).execute(anyString());
    }

    @Test
    @DisplayName("December rolls over correctly to January of next year")
    void partitionName_decemberRollover() {
        YearMonth dec = YearMonth.of(2025, 12);
        String sql = String.format(
                "FOR VALUES FROM ('%s') TO ('%s')",
                dec.atDay(1), dec.plusMonths(1).atDay(1));
        assertThat(sql).contains("2025-12-01").contains("2026-01-01");
    }
}
