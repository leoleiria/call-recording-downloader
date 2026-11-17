package io.github.leoleiria.callrecording.ringover;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Spaces requests evenly so the API limit is never exceeded.
 *
 * <p>Ringover allows 2 requests per second per API key; {@link #ringoverDefault()} therefore lets
 * one request through every 500 ms. Thread-safe: concurrent callers queue up.
 */
public final class RateLimiter {

  private final long intervalNanos;
  private final LongSupplier nanoClock;
  private final Sleeper sleeper;
  private long nextAllowedNanos;
  private boolean first = true;

  /**
   * @param requestsPerSecond maximum sustained rate
   * @param nanoClock monotonic clock, normally {@link System#nanoTime()}
   * @param sleeper how to wait
   */
  public RateLimiter(double requestsPerSecond, LongSupplier nanoClock, Sleeper sleeper) {
    if (requestsPerSecond <= 0) {
      throw new IllegalArgumentException("requestsPerSecond must be positive");
    }
    this.intervalNanos = (long) Math.ceil(1_000_000_000L / requestsPerSecond);
    this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
  }

  /** 2 requests per second, the documented Ringover limit. */
  public static RateLimiter ringoverDefault() {
    return new RateLimiter(2, System::nanoTime, Sleeper.system());
  }

  /** Blocks until the next request may be sent. */
  public synchronized void acquire() throws InterruptedException {
    long now = nanoClock.getAsLong();
    if (first) {
      first = false;
    } else if (now - nextAllowedNanos < 0) {
      sleeper.sleep(Duration.ofNanos(nextAllowedNanos - now));
      now = nextAllowedNanos;
    }
    nextAllowedNanos = now + intervalNanos;
  }
}
