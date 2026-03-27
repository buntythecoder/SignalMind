package pl.piomin.signalmind.quality.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.piomin.signalmind.quality.domain.DayQualityReport;
import pl.piomin.signalmind.quality.service.CandleQualityService;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * REST API for on-demand data-quality inspection.
 *
 * <pre>
 * GET /api/quality/{symbol}?date=2024-01-15          → DayQualityReport
 * GET /api/quality/{symbol}/range?from=...&amp;to=...    → List&lt;DayQualityReport&gt;
 * GET /api/quality/validators                        → List&lt;String&gt;
 * </pre>
 */
@RestController
@RequestMapping("/api/quality")
public class DataQualityController {

    private final CandleQualityService qualityService;

    public DataQualityController(CandleQualityService qualityService) {
        this.qualityService = qualityService;
    }

    /**
     * Returns the quality report for a single day.
     * If {@code date} is omitted, yesterday (server-local date − 1 day) is used.
     */
    @GetMapping("/{symbol}")
    public DayQualityReport getDayReport(
            @PathVariable String symbol,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate targetDate = (date != null) ? date : LocalDate.now().minusDays(1);
        return qualityService.validateDay(symbol, targetDate);
    }

    /**
     * Returns quality reports for each day in the inclusive range [{@code from}, {@code to}].
     * Maximum range: 31 days.
     */
    @GetMapping("/{symbol}/range")
    public List<DayQualityReport> getRangeReport(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return qualityService.validateRange(symbol, from, to);
    }

    /**
     * Returns the names of all registered {@link pl.piomin.signalmind.quality.service.DataQualityValidator}
     * implementations discovered by Spring.
     */
    @GetMapping("/validators")
    public List<String> listValidators() {
        return qualityService.availableValidators();
    }

    // ── Exception handlers ────────────────────────────────────────────────────

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
