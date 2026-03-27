package pl.piomin.signalmind.integration.breeze;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BreezeChecksumTest {

    @Test
    void formatTimestamp_producesExpectedPattern() {
        Instant ts = Instant.parse("2024-01-15T09:15:00Z");
        String formatted = BreezeChecksum.formatTimestamp(ts);
        assertThat(formatted).isEqualTo("2024-01-15T09:15:00.000Z");
    }

    @Test
    void compute_returnsLowercaseHex64Chars() {
        String result = BreezeChecksum.compute(
                "2024-01-15T09:15:00.000Z",
                "my-api-key",
                "my-api-secret",
                "");
        // HMAC-SHA256 always produces 32 bytes = 64 hex chars
        assertThat(result).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void compute_isDeterministic() {
        String ts     = "2024-01-15T09:15:00.000Z";
        String key    = "testKey";
        String secret = "testSecret";
        String body   = "";

        String first  = BreezeChecksum.compute(ts, key, secret, body);
        String second = BreezeChecksum.compute(ts, key, secret, body);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void compute_changesWithDifferentTimestamp() {
        String key    = "testKey";
        String secret = "testSecret";
        String ts1    = "2024-01-15T09:15:00.000Z";
        String ts2    = "2024-01-15T09:16:00.000Z";

        assertThat(BreezeChecksum.compute(ts1, key, secret, ""))
                .isNotEqualTo(BreezeChecksum.compute(ts2, key, secret, ""));
    }

    @Test
    void compute_changesWithBody() {
        String ts     = "2024-01-15T09:15:00.000Z";
        String key    = "testKey";
        String secret = "testSecret";

        assertThat(BreezeChecksum.compute(ts, key, secret, ""))
                .isNotEqualTo(BreezeChecksum.compute(ts, key, secret, "{\"some\":\"body\"}"));
    }
}
