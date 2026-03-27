package pl.piomin.signalmind.integration.breeze;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.piomin.signalmind.integration.breeze.dto.BreezeHistoricalResponse;
import pl.piomin.signalmind.integration.breeze.dto.BreezeOhlcv;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Low-level ICICI Breeze REST client using Java 21 {@link HttpClient}.
 *
 * <p>Every request:
 * <ol>
 *   <li>Acquires a slot from {@link BreezeRateLimiter} (blocks if at capacity)</li>
 *   <li>Computes {@code X-Checksum} and {@code X-Timestamp} auth headers via
 *       {@link BreezeChecksum}</li>
 *   <li>Fires the HTTP request with the configured session token</li>
 * </ol>
 *
 * <p>This class is intentionally thin — business logic lives in
 * {@link BreezeHistoricalService}.
 */
@Component
public class BreezeClient {

    private static final Logger log = LoggerFactory.getLogger(BreezeClient.class);

    private static final String BASE_URL    = "https://api.icicidirect.com/breezeapi/api/v1";
    private static final String HIST_PATH   = "/historicalcharts";
    private static final Duration TIMEOUT   = Duration.ofSeconds(30);

    // Breeze date-time format for query parameters: "dd-MM-yyyy HH:mm:ss"
    private static final DateTimeFormatter BREEZE_DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss").withZone(ZoneOffset.UTC);

    private final BreezeConfig      config;
    private final BreezeRateLimiter rateLimiter;
    private final ObjectMapper      objectMapper;
    private final HttpClient        httpClient;

    public BreezeClient(BreezeConfig config, BreezeRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.config      = config;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.httpClient  = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Fetches historical 1-minute candles for a single stock.
     *
     * @param breezeCode   instrument short code (e.g. {@code "RELIANCE"})
     * @param exchangeCode exchange code — {@code "NSE"} or {@code "BSE"}
     * @param from         start of the window (inclusive, UTC)
     * @param to           end of the window (exclusive, UTC)
     * @return ordered list of OHLCV bars (may be empty if Breeze returns no data)
     * @throws BreezeClientException if the HTTP call fails or the API returns an error
     * @throws InterruptedException  if the rate-limit wait is interrupted
     */
    public List<BreezeOhlcv> fetchHistoricalCandles(String breezeCode,
                                                     String exchangeCode,
                                                     Instant from,
                                                     Instant to)
            throws BreezeClientException, InterruptedException {

        rateLimiter.acquire();

        Instant now = Instant.now();
        String timestamp = BreezeChecksum.formatTimestamp(now);
        String checksum  = BreezeChecksum.compute(timestamp, config.apiKey(), config.apiSecret(), "");

        String url = BASE_URL + HIST_PATH + "?" + buildQuery(breezeCode, exchangeCode, from, to);

        log.debug("Breeze GET {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("X-Checksum",    "token " + checksum)
                .header("X-Timestamp",   timestamp)
                .header("X-AppKey",      config.apiKey())
                .header("X-SessionToken", sessionToken())
                .header("Content-Type",  "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new BreezeClientException("HTTP request failed: " + e.getMessage(), e);
        }

        if (response.statusCode() != 200) {
            throw new BreezeClientException(
                    "Breeze API returned HTTP " + response.statusCode() + " for " + breezeCode);
        }

        BreezeHistoricalResponse body;
        try {
            body = objectMapper.readValue(response.body(), BreezeHistoricalResponse.class);
        } catch (IOException e) {
            throw new BreezeClientException("Failed to parse Breeze response: " + e.getMessage(), e);
        }

        if (!body.isSuccess()) {
            throw new BreezeClientException(
                    "Breeze API error for " + breezeCode + ": " + body.error());
        }

        List<BreezeOhlcv> data = body.data();
        log.info("Breeze: fetched {} candles for {} [{} – {}]",
                data == null ? 0 : data.size(), breezeCode,
                BREEZE_DATE_FMT.format(from), BREEZE_DATE_FMT.format(to));

        return data == null ? List.of() : data;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String sessionToken() {
        String token = config.sessionToken();
        if (token == null || token.isBlank()) {
            throw new BreezeClientException("Breeze session token is not configured. "
                    + "Set breeze.session-token in application.yml or via environment variable.");
        }
        return token;
    }

    private String buildQuery(String breezeCode, String exchangeCode, Instant from, Instant to) {
        return "stock_code="    + encode(breezeCode)
             + "&exchange_code=" + encode(exchangeCode)
             + "&from_date="     + encode(BREEZE_DATE_FMT.format(from))
             + "&to_date="       + encode(BREEZE_DATE_FMT.format(to))
             + "&interval="      + encode("1minute")
             + "&product_type="  + encode("cash")
             + "&expiry_date="
             + "&right="
             + "&strike_price=";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
