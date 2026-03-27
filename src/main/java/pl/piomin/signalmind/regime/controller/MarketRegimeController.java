package pl.piomin.signalmind.regime.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.piomin.signalmind.regime.domain.RegimeSnapshot;
import pl.piomin.signalmind.regime.service.MarketRegimeService;

import java.util.Map;
import java.util.Optional;

/**
 * Admin REST endpoint for querying and triggering market regime classification (SM-21).
 *
 * <p>The service is optional — it is absent when Redis is not configured (e.g. in
 * the test profile). The controller handles the absent case gracefully by returning
 * HTTP 503.
 */
@RestController
@RequestMapping("/api/admin/regime")
public class MarketRegimeController {

    private final Optional<MarketRegimeService> regimeService;

    public MarketRegimeController(Optional<MarketRegimeService> regimeService) {
        this.regimeService = regimeService;
    }

    /**
     * Returns the most recently cached regime snapshot.
     *
     * @return 200 with regime JSON, 204 when no snapshot is cached,
     *         or 503 when the regime service is unavailable
     */
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> currentRegime() {
        if (regimeService.isEmpty()) {
            return ResponseEntity.status(503)
                    .body(Map.of("error", "Regime service not available (Redis not configured)"));
        }
        return regimeService.get()
                .currentRegime()
                .map(snap -> ResponseEntity.ok(snapshotToMap(snap)))
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Triggers an on-demand regime recalculation and returns the fresh snapshot.
     *
     * @return 200 with the newly computed regime, or 503 when unavailable
     */
    @PostMapping("/recalculate")
    public ResponseEntity<Map<String, Object>> recalculate() {
        if (regimeService.isEmpty()) {
            return ResponseEntity.status(503)
                    .body(Map.of("error", "Regime service not available (Redis not configured)"));
        }
        RegimeSnapshot snap = regimeService.get().compute();
        return ResponseEntity.ok(snapshotToMap(snap));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Map<String, Object> snapshotToMap(RegimeSnapshot snap) {
        return Map.of(
                "regime",             snap.regime().name(),
                "confidenceModifier", snap.regime().confidenceModifier(),
                "calculatedAt",       snap.calculatedAt().toString(),
                "reason",             snap.reason()
        );
    }
}
