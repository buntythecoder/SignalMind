package pl.piomin.signalmind.integration.breeze;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe sliding-window rate limiter for the ICICI Breeze REST API.
 *
 * <p>Enforces two limits simultaneously:
 * <ul>
 *   <li><b>Per-minute</b>: max {@value #MAX_PER_MINUTE} calls in any 60-second window</li>
 *   <li><b>Daily</b>: max {@value #MAX_PER_DAY} calls within a rolling 24-hour window</li>
 * </ul>
 *
 * <p>{@link #acquire()} blocks the calling thread until capacity is available.
 * Virtual threads (Project Loom) handle the blocking cheaply; do not pool them.
 */
@Component
public class BreezeRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(BreezeRateLimiter.class);

    static final int MAX_PER_MINUTE = 100;
    static final int MAX_PER_DAY    = 5_000;

    private static final long MINUTE_WINDOW_MS = 60_000L;
    private static final long DAY_WINDOW_MS    = 86_400_000L;

    private final ReentrantLock lock = new ReentrantLock();
    private final Deque<Long> minuteTimestamps = new ArrayDeque<>(MAX_PER_MINUTE);
    private final Deque<Long> dayTimestamps    = new ArrayDeque<>(MAX_PER_DAY);

    /**
     * Acquires a rate-limit slot, blocking until one is available.
     * Call this before every Breeze API HTTP request.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void acquire() throws InterruptedException {
        while (true) {
            long sleepMs;
            lock.lock();
            try {
                long now = Instant.now().toEpochMilli();
                evict(minuteTimestamps, now, MINUTE_WINDOW_MS);
                evict(dayTimestamps,    now, DAY_WINDOW_MS);

                if (minuteTimestamps.size() < MAX_PER_MINUTE
                        && dayTimestamps.size() < MAX_PER_DAY) {
                    minuteTimestamps.addLast(now);
                    dayTimestamps.addLast(now);
                    return;
                }
                sleepMs = computeSleepMs(now);
            } finally {
                lock.unlock();
            }

            log.debug("Breeze rate limit reached — sleeping {}ms", sleepMs);
            Thread.sleep(sleepMs);
        }
    }

    /** Current number of calls tracked in the per-minute window. */
    public int minuteWindowSize() {
        lock.lock();
        try {
            long now = Instant.now().toEpochMilli();
            evict(minuteTimestamps, now, MINUTE_WINDOW_MS);
            return minuteTimestamps.size();
        } finally {
            lock.unlock();
        }
    }

    /** Current number of calls tracked in the daily window. */
    public int dayWindowSize() {
        lock.lock();
        try {
            long now = Instant.now().toEpochMilli();
            evict(dayTimestamps, now, DAY_WINDOW_MS);
            return dayTimestamps.size();
        } finally {
            lock.unlock();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void evict(Deque<Long> queue, long now, long windowMs) {
        while (!queue.isEmpty() && (now - queue.peekFirst()) >= windowMs) {
            queue.pollFirst();
        }
    }

    private long computeSleepMs(long now) {
        long minuteSleep = minuteTimestamps.size() >= MAX_PER_MINUTE && !minuteTimestamps.isEmpty()
                ? MINUTE_WINDOW_MS - (now - minuteTimestamps.peekFirst()) + 1
                : 0;
        long daySleep = dayTimestamps.size() >= MAX_PER_DAY && !dayTimestamps.isEmpty()
                ? DAY_WINDOW_MS - (now - dayTimestamps.peekFirst()) + 1
                : 0;
        return Math.max(1, Math.min(minuteSleep, daySleep == 0 ? minuteSleep : daySleep));
    }
}
