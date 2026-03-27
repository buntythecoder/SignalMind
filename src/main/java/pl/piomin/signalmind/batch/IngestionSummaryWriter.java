package pl.piomin.signalmind.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Logs {@link IngestionSummary} records produced by {@link CandleBackfillProcessor}.
 * Persistence happens inside the processor; this writer only reports results.
 */
@Component
public class IngestionSummaryWriter implements ItemWriter<IngestionSummary> {

    private static final Logger log = LoggerFactory.getLogger(IngestionSummaryWriter.class);

    @Override
    public void write(Chunk<? extends IngestionSummary> chunk) {
        int ok     = 0;
        int failed = 0;
        int total  = 0;

        for (IngestionSummary summary : chunk) {
            log.info(summary.toString());
            total += summary.candlesInserted();
            if (summary.success()) ok++; else failed++;
        }

        log.info("[batch] chunk report — stocks OK: {}, failed: {}, candles in chunk: {}",
                ok, failed, total);
    }
}
