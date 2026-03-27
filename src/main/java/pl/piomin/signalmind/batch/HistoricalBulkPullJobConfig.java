package pl.piomin.signalmind.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.StockRepository;

import java.util.Map;

/**
 * Spring Batch configuration for the historical candle bulk-pull job (SM-14).
 *
 * <h2>Job overview</h2>
 * <pre>
 *   Job: historicalBulkPullJob
 *     Step: backfillCandlesStep (chunk size 1)
 *       Reader:    RepositoryItemReader&lt;Stock&gt; — all active stocks, paged by symbol
 *       Processor: CandleBackfillProcessor     — fetch 1 yr candles per stock, save, return summary
 *       Writer:    IngestionSummaryWriter       — log results
 * </pre>
 *
 * <h2>Resumability</h2>
 * Spring Batch's {@link JobRepository} persists step execution state in the
 * {@code spring_batch_*} tables. If the job fails at stock 30, a restart will
 * begin from where it left off (the {@code RepositoryItemReader} uses a page
 * index tracked in the step {@code ExecutionContext}).
 *
 * <h2>Triggering</h2>
 * The job does NOT run on startup ({@code spring.batch.job.enabled=false}).
 * Trigger via the REST endpoint {@code POST /api/admin/jobs/historical-pull}
 * (implemented in SM-14 REST layer or manually via Spring Batch admin).
 *
 * <h2>Estimated runtime</h2>
 * 60 stocks × 365 days × 1 API call/day = 21,900 calls.
 * At 100 calls/min the rate limiter caps this at ~219 minutes (≈ 3.5 hours).
 * Run off-hours — never during 09:15–15:30 IST market window.
 */
@Configuration
@ConditionalOnBean(JobRepository.class)
public class HistoricalBulkPullJobConfig {

    private final StockRepository              stockRepository;
    private final CandleBackfillProcessor      processor;
    private final IngestionSummaryWriter       writer;

    public HistoricalBulkPullJobConfig(StockRepository stockRepository,
                                       CandleBackfillProcessor processor,
                                       IngestionSummaryWriter writer) {
        this.stockRepository = stockRepository;
        this.processor       = processor;
        this.writer          = writer;
    }

    // ── Reader ────────────────────────────────────────────────────────────────

    /**
     * Reads active stocks ordered by symbol.
     * {@code RepositoryItemReader} is restartable — page position is stored in the
     * step {@code ExecutionContext} so a job restart resumes from the correct page.
     */
    @Bean
    public RepositoryItemReader<Stock> activeStockReader() {
        return new RepositoryItemReaderBuilder<Stock>()
                .name("activeStockReader")
                .repository(stockRepository)
                .methodName("findByActiveTrueOrderBySymbolAsc")
                .pageSize(10)
                .sorts(Map.of("symbol", Sort.Direction.ASC))
                .build();
    }

    // ── Step ──────────────────────────────────────────────────────────────────

    @Bean
    public Step backfillCandlesStep(JobRepository jobRepository,
                                     PlatformTransactionManager txManager) {
        return new StepBuilder("backfillCandlesStep", jobRepository)
                .<Stock, IngestionSummary>chunk(1, txManager)  // commit after every stock
                .reader(activeStockReader())
                .processor(processor)
                .writer(writer)
                .build();
    }

    // ── Job ───────────────────────────────────────────────────────────────────

    @Bean
    public Job historicalBulkPullJob(JobRepository jobRepository,
                                      Step backfillCandlesStep) {
        return new JobBuilder("historicalBulkPullJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(backfillCandlesStep)
                .build();
    }
}
