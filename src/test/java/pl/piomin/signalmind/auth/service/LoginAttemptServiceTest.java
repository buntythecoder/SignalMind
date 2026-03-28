package pl.piomin.signalmind.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
    }

    @Test
    @DisplayName("isBlocked returns false initially for a new IP")
    void isBlocked_returnsFalse_initiallyForIp() {
        assertThat(service.isBlocked("192.168.1.1")).isFalse();
    }

    @Test
    @DisplayName("isBlocked returns true after 5 failed attempts within the window")
    void isBlocked_returnsTrue_after5Failures() {
        String ip = "10.0.0.1";
        for (int i = 0; i < 5; i++) {
            service.recordFailure(ip);
        }
        assertThat(service.isBlocked(ip)).isTrue();
    }

    @Test
    @DisplayName("resetAttempts clears the block for an IP")
    void resetAttempts_clearsBlock() {
        String ip = "10.0.0.2";
        for (int i = 0; i < 5; i++) {
            service.recordFailure(ip);
        }
        assertThat(service.isBlocked(ip)).isTrue();

        service.resetAttempts(ip);
        assertThat(service.isBlocked(ip)).isFalse();
    }

    @Test
    @DisplayName("isBlocked returns false after the 15-minute window expires")
    void isBlocked_returnsFalse_afterWindowExpires() {
        String ip = "10.0.0.3";

        // Inject old timestamps that are outside the 15-min window
        Deque<Long> oldTimestamps = new ArrayDeque<>();
        long expired = System.currentTimeMillis() - LoginAttemptService.WINDOW_MS - 1000;
        for (int i = 0; i < 5; i++) {
            oldTimestamps.addLast(expired + i);
        }
        service.getAttempts().put(ip, oldTimestamps);

        // After pruning, all timestamps are outside the window
        assertThat(service.isBlocked(ip)).isFalse();
    }
}
