package pl.piomin.signalmind.integration.breeze;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Generates the {@code X-Checksum} header required by the ICICI Breeze REST API.
 *
 * <p>Algorithm: {@code SHA256( timestamp + apiKey + body )} encoded as a lowercase hex string.
 * The {@code X-Timestamp} header must carry the same timestamp value (ISO-8601, UTC).
 *
 * <p>Reference: ICICI Breeze API documentation v1.
 */
public final class BreezeChecksum {

    private static final String HMAC_ALGO = "HmacSHA256";

    // ISO-8601 format expected by the Breeze API: "2024-01-15T09:15:00.000Z"
    static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC);

    private BreezeChecksum() {
    }

    /**
     * Formats {@code instant} as the timestamp string the Breeze API expects.
     */
    public static String formatTimestamp(Instant instant) {
        return TIMESTAMP_FMT.format(instant);
    }

    /**
     * Computes the HMAC-SHA256 checksum for a Breeze API request.
     *
     * @param timestamp formatted timestamp (from {@link #formatTimestamp})
     * @param apiKey    Breeze app key
     * @param apiSecret Breeze API secret (HMAC signing key)
     * @param body      raw request body (empty string {@code ""} for GET requests)
     * @return lowercase hex-encoded HMAC-SHA256 digest
     */
    public static String compute(String timestamp, String apiKey, String apiSecret, String body) {
        String message = timestamp + apiKey + body;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return hexEncode(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static String hexEncode(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
