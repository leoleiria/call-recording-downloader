package io.github.leoleiria.callrecording.ringover;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * When and how long to wait before repeating a failed request.
 *
 * <p>Retries happen on {@code 429 Too Many Requests}, on transient server errors and on network
 * errors, with exponential back-off. A {@code Retry-After} header, when present, takes precedence.
 *
 * @param maxAttempts total attempts including the first one
 * @param initialDelay wait before the second attempt; doubled for each further attempt
 * @param maxDelay upper bound for any single wait
 */
public record RetryPolicy(int maxAttempts, Duration initialDelay, Duration maxDelay) {

  private static final Set<Integer> RETRYABLE_STATUS = Set.of(429, 500, 502, 503, 504);

  public RetryPolicy {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be at least 1");
    }
  }

  /** 4 attempts, waiting 1 s, 2 s and 4 s, never more than 30 s. */
  public static RetryPolicy ringoverDefault() {
    return new RetryPolicy(4, Duration.ofSeconds(1), Duration.ofSeconds(30));
  }

  /** Whether a response with this HTTP status is worth retrying. */
  public boolean isRetryable(int status) {
    return RETRYABLE_STATUS.contains(status);
  }

  /**
   * Delay before attempt number {@code attempt + 1}.
   *
   * @param attempt the attempt that just failed, starting at 1
   * @param retryAfter value of the {@code Retry-After} header, if any
   */
  public Duration delayAfter(int attempt, Optional<String> retryAfter) {
    Optional<Duration> fromHeader = retryAfter.flatMap(RetryPolicy::parseRetryAfterSeconds);
    if (fromHeader.isPresent()) {
      return min(fromHeader.get(), maxDelay);
    }
    Duration delay = initialDelay.multipliedBy(1L << Math.min(attempt - 1, 20));
    return min(delay, maxDelay);
  }

  private static Optional<Duration> parseRetryAfterSeconds(String value) {
    try {
      long seconds = Long.parseLong(value.strip());
      return seconds >= 0 ? Optional.of(Duration.ofSeconds(seconds)) : Optional.empty();
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static Duration min(Duration a, Duration b) {
    return a.compareTo(b) <= 0 ? a : b;
  }
}
