package pl.piomin.signalmind.auth.service;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter for login attempts.
 * Blocks an IP after {@value #MAX_ATTEMPTS} failed attempts within a
 * {@value #WINDOW_MS}-millisecond sliding window.
 */
@Service
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 5;
    static final long WINDOW_MS = 15 * 60 * 1000L; // 15 minutes

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    /**
     * Returns {@code true} if the given IP has exceeded the maximum allowed
     * failed login attempts within the current window.
     */
    public boolean isBlocked(String ip) {
        prune(ip);
        Deque<Long> timestamps = attempts.get(ip);
        return timestamps != null && timestamps.size() >= MAX_ATTEMPTS;
    }

    /**
     * Record a failed login attempt for the given IP.
     */
    public void recordFailure(String ip) {
        attempts.computeIfAbsent(ip, k -> new ArrayDeque<>())
                .addLast(System.currentTimeMillis());
    }

    /**
     * Clear all recorded attempts for the given IP (e.g. after successful login).
     */
    public void resetAttempts(String ip) {
        attempts.remove(ip);
    }

    // -- package-private for testing ----------------------------------------

    Map<String, Deque<Long>> getAttempts() {
        return attempts;
    }

    private void prune(String ip) {
        Deque<Long> timestamps = attempts.get(ip);
        if (timestamps == null) return;
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
            timestamps.pollFirst();
        }
        if (timestamps.isEmpty()) {
            attempts.remove(ip);
        }
    }
}
