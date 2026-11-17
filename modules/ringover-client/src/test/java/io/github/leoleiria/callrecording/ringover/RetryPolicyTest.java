package io.github.leoleiria.callrecording.ringover;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

  private final RetryPolicy policy = RetryPolicy.ringoverDefault();

  @Test
  void backsOffExponentially() {
    assertThat(policy.delayAfter(1, Optional.empty())).isEqualTo(Duration.ofSeconds(1));
    assertThat(policy.delayAfter(2, Optional.empty())).isEqualTo(Duration.ofSeconds(2));
    assertThat(policy.delayAfter(3, Optional.empty())).isEqualTo(Duration.ofSeconds(4));
    assertThat(policy.delayAfter(10, Optional.empty())).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void retryAfterHeaderWins() {
    assertThat(policy.delayAfter(1, Optional.of("7"))).isEqualTo(Duration.ofSeconds(7));
    assertThat(policy.delayAfter(1, Optional.of("600"))).isEqualTo(Duration.ofSeconds(30));
    assertThat(policy.delayAfter(2, Optional.of("not-a-number"))).isEqualTo(Duration.ofSeconds(2));
  }

  @Test
  void onlyTransientStatusesAreRetried() {
    assertThat(policy.isRetryable(429)).isTrue();
    assertThat(policy.isRetryable(503)).isTrue();
    assertThat(policy.isRetryable(401)).isFalse();
    assertThat(policy.isRetryable(404)).isFalse();
  }
}
