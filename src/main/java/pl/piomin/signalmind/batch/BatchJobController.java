package pl.piomin.signalmind.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Admin REST endpoint for triggering Spring Batch jobs manually.
 *
 * <p>Access is restricted to ADMIN role via {@link pl.piomin.signalmind.common.config.SecurityConfig}.
 * Intended for one-time or scheduled off-hours execution — never during
 * 09:15–15:30 IST market hours.
 */
@ConditionalOnBean(JobLauncher.class)
@RestController
@RequestMapping("/api/admin/jobs")
public class BatchJobController {

    private static final Logger log = LoggerFactory.getLogger(BatchJobController.class);

    private final JobLauncher jobLauncher;
    private final Job         historicalBulkPullJob;

    public BatchJobController(JobLauncher jobLauncher, Job historicalBulkPullJob) {
        this.jobLauncher          = jobLauncher;
        this.historicalBulkPullJob = historicalBulkPullJob;
    }

    /**
     * Triggers the historical bulk pull job asynchronously.
     * Returns 202 Accepted immediately; job progress visible in batch tables and logs.
     */
    @PostMapping("/historical-pull")
    public ResponseEntity<Map<String, Object>> triggerHistoricalPull() {
        JobParameters params = new JobParametersBuilder()
                .addString("triggeredAt", Instant.now().toString())
                .toJobParameters();

        try {
            var execution = jobLauncher.run(historicalBulkPullJob, params);
            log.info("historicalBulkPullJob launched — executionId={}, status={}",
                    execution.getId(), execution.getStatus());

            return ResponseEntity.accepted().body(Map.of(
                    "jobExecutionId", execution.getId(),
                    "status",         execution.getStatus().toString(),
                    "message",        "Historical bulk pull job started. Check logs for progress."
            ));
        } catch (Exception e) {
            log.error("Failed to launch historicalBulkPullJob: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
}
