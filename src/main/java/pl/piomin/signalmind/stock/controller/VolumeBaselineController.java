package pl.piomin.signalmind.stock.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.domain.VolumeBaseline;
import pl.piomin.signalmind.stock.repository.StockRepository;
import pl.piomin.signalmind.stock.service.VolumeBaselineService;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * REST API for querying and manually triggering volume baseline calculations.
 *
 * <p>Base path: {@code /api/baselines}
 */
@RestController
@RequestMapping("/api/baselines")
@Tag(name = "Volume Baselines", description = "20-day time-slot volume baseline queries and administration")
public class VolumeBaselineController {

    private static final Logger log = LoggerFactory.getLogger(VolumeBaselineController.class);

    private final VolumeBaselineService service;
    private final StockRepository stockRepository;

    public VolumeBaselineController(VolumeBaselineService service,
                                    StockRepository stockRepository) {
        this.service = service;
        this.stockRepository = stockRepository;
    }

    // ── Query endpoints ───────────────────────────────────────────────────────

    /**
     * Returns all computed baselines for a stock, ordered by slot time.
     *
     * @param symbol NSE stock symbol (case-sensitive, e.g. {@code RELIANCE})
     * @return 200 with list of baselines; 404 if the symbol is not registered
     */
    @GetMapping("/{symbol}")
    @Operation(summary = "Get all volume baselines for a stock",
               description = "Returns 20-day rolling volume averages for every 1-minute slot")
    public ResponseEntity<List<VolumeBaseline>> getBaselines(@PathVariable String symbol) {
        try {
            List<VolumeBaseline> baselines = service.findBaselines(symbol.toUpperCase());
            return ResponseEntity.ok(baselines);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Returns the baseline for a specific IST minute slot.
     *
     * @param symbol NSE stock symbol
     * @param time   slot time in {@code HH:mm} format, e.g. {@code 09:30}
     * @return 200 with the matching baseline; 404 if not found
     */
    @GetMapping("/{symbol}/slot")
    @Operation(summary = "Get volume baseline for a specific time slot",
               description = "Returns the rolling average volume for the given minute slot (IST)")
    public ResponseEntity<VolumeBaseline> getBaselineForSlot(
            @PathVariable String symbol,
            @RequestParam String time) {
        try {
            LocalTime slotTime = LocalTime.parse(time);
            return service.findBaseline(symbol.toUpperCase(), slotTime)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    /**
     * Triggers an immediate baseline rebuild for a single stock.
     *
     * @param symbol NSE stock symbol
     * @return 200 with rebuild summary ({@code symbol}, {@code slotsComputed},
     *         {@code durationMs}); 404 if the symbol is not registered; 500 on
     *         unexpected error
     */
    @PostMapping("/admin/{symbol}/rebuild")
    @Operation(summary = "Rebuild volume baselines for one stock",
               description = "Synchronously recalculates and persists the 20-day baselines for the given symbol")
    public ResponseEntity<Map<String, Object>> rebuild(@PathVariable String symbol) {
        String upperSymbol = symbol.toUpperCase();
        try {
            Stock stock = stockRepository.findBySymbol(upperSymbol)
                    .orElseThrow(() -> new NoSuchElementException("Unknown symbol: " + upperSymbol));

            long start = System.currentTimeMillis();
            service.recalculate(stock);
            long durationMs = System.currentTimeMillis() - start;

            int slotsComputed = service.findBaselines(upperSymbol).size();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("symbol", upperSymbol);
            response.put("slotsComputed", slotsComputed);
            response.put("durationMs", durationMs);

            log.info("[baseline] Manual rebuild for {} completed: {} slots in {}ms",
                    upperSymbol, slotsComputed, durationMs);

            return ResponseEntity.ok(response);

        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("[baseline] Rebuild failed for {}", upperSymbol, e);
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("symbol", upperSymbol);
            errorBody.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorBody);
        }
    }
}
