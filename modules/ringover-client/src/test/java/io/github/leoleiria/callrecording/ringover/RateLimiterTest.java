package io.github.leoleiria.callrecording.ringover;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

  private final AtomicLong clock = new AtomicLong();
  private final List<Duration> sleeps = new ArrayList<>();
  private final RateLimiter limiter =
      new RateLimiter(
          2,
          clock::get,
          duration -> {
            sleeps.add(duration);
            clock.addAndGet(duration.toNanos());
          });

  @Test
  void firstRequestDoesNotWait() throws InterruptedException {
    limiter.acquire();

    assertThat(sleeps).isEmpty();
  }

  @Test
  void backToBackRequestsAreSpacedBy500Milliseconds() throws InterruptedException {
    limiter.acquire();
    limiter.acquire();
    limiter.acquire();

    assertThat(sleeps).containsExactly(Duration.ofMillis(500), Duration.ofMillis(500));
  }

  @Test
  void noWaitWhenEnoughTimeHasPassed() throws InterruptedException {
    limiter.acquire();
    clock.addAndGet(Duration.ofSeconds(1).toNanos());
    limiter.acquire();

    assertThat(sleeps).isEmpty();
  }
}
