package io.github.leoleiria.callrecording.ringover;

import java.time.Duration;

/** Waits. Replaced by a fake in tests so they run instantly. */
@FunctionalInterface
public interface Sleeper {

  void sleep(Duration duration) throws InterruptedException;

  /** Real sleeping via {@link Thread#sleep(Duration)}. */
  static Sleeper system() {
    return duration -> {
      if (!duration.isNegative() && !duration.isZero()) {
        Thread.sleep(duration);
      }
    };
  }
}
