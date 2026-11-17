package io.github.leoleiria.callrecording.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SecretRedactorTest {

  @AfterEach
  void forgetSecrets() {
    SecretRedactor.clear();
  }

  @Test
  void masksAuthorizationHeadersEvenIfNotRegistered() {
    String text = "headers {Authorization: abcdefghijklmnop, Accept=json}";

    assertThat(SecretRedactor.redact(text))
        .isEqualTo("headers {Authorization: ****mnop, Accept=json}");
  }

  @Test
  void masksAuthorizationCaseInsensitively() {
    assertThat(SecretRedactor.redact("authorization=zyxwvutsrqponm"))
        .isEqualTo("authorization=****ponm");
  }

  @Test
  void masksEveryOccurrenceOfARegisteredSecret() {
    SecretRedactor.register("secret-value-123456");

    assertThat(SecretRedactor.redact("a secret-value-123456 b secret-value-123456"))
        .isEqualTo("a ****3456 b ****3456");
  }

  @Test
  void leavesOtherTextUntouched() {
    assertThat(SecretRedactor.redact("nothing to hide")).isEqualTo("nothing to hide");
    assertThat(SecretRedactor.redact(null)).isNull();
  }
}
