package pl.piomin.signalmind.integration.breeze;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BreezeRateLimiterTest {

    @Test
    void acquire_firstCallSucceedsImmediately() throws InterruptedException {
        BreezeRateLimiter limiter = new BreezeRateLimiter();
        long before = System.currentTimeMillis();
        limiter.acquire();
        long elapsed = System.currentTimeMillis() - before;
        assertThat(elapsed).isLessThan(100); // should be instant
    }

    @Test
    void minuteWindowSize_incrementsAfterAcquire() throws InterruptedException {
        BreezeRateLimiter limiter = new BreezeRateLimiter();
        assertThat(limiter.minuteWindowSize()).isZero();
        limiter.acquire();
        assertThat(limiter.minuteWindowSize()).isEqualTo(1);
        limiter.acquire();
        assertThat(limiter.minuteWindowSize()).isEqualTo(2);
    }

    @Test
    void dayWindowSize_incrementsAfterAcquire() throws InterruptedException {
        BreezeRateLimiter limiter = new BreezeRateLimiter();
        limiter.acquire();
        limiter.acquire();
        limiter.acquire();
        assertThat(limiter.dayWindowSize()).isEqualTo(3);
    }

    @Test
    void acquire_99ConcurrentCallsCompleteWithoutBlocking() throws InterruptedException {
        // 99 calls < 100-per-minute limit; all should acquire without waiting
        BreezeRateLimiter limiter = new BreezeRateLimiter();
        int n = 99;
        CountDownLatch latch = new CountDownLatch(n);
        AtomicInteger acquired = new AtomicInteger();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < n; i++) {
                executor.submit(() -> {
                    try {
                        limiter.acquire();
                        acquired.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            boolean done = latch.await(5, TimeUnit.SECONDS);
            assertThat(done).isTrue();
        }

        assertThat(acquired.get()).isEqualTo(n);
        assertThat(limiter.minuteWindowSize()).isEqualTo(n);
    }
}
