package io.github.leoleiria.callrecording.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ApiKeyTest {

  private static final String SECRET = "a1b2c3d4e5f6g7h8i9j0";

  @AfterEach
  void forgetSecrets() {
    SecretRedactor.clear();
  }

  @Test
  void toStringNeverContainsTheSecret() {
    ApiKey key = ApiKey.of(SECRET);

    assertThat(key.toString()).isEqualTo("****i9j0").doesNotContain(SECRET);
    assertThat("key=" + key).doesNotContain(SECRET);
  }

  @Test
  void shortKeysAreFullyMasked() {
    assertThat(ApiKey.of("abc12345").masked()).isEqualTo("****");
  }

  @Test
  void revealReturnsTheTrimmedValue() {
    assertThat(ApiKey.of("  " + SECRET + "\n").reveal()).isEqualTo(SECRET);
  }

  @Test
  void rejectsBlankOrSpacedKeys() {
    assertThatIllegalArgumentException().isThrownBy(() -> ApiKey.of("   "));
    assertThatIllegalArgumentException().isThrownBy(() -> ApiKey.of("abc def"));
  }

  @Test
  void creatingAKeyRegistersItForRedaction() {
    ApiKey.of(SECRET);

    assertThat(SecretRedactor.redact("request failed for " + SECRET))
        .isEqualTo("request failed for ****i9j0");
  }
}
