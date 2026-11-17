package io.github.leoleiria.callrecording.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumberTest {

  @Test
  void parsesE164Number() {
    PhoneNumber number = PhoneNumber.parse("+34666666666");

    assertThat(number.e164()).isEqualTo("+34666666666");
    assertThat(number.digits()).isEqualTo("34666666666");
  }

  @Test
  void ignoresCommonSeparators() {
    assertThat(PhoneNumber.parse(" +34 666-66.66 (66) ").e164()).isEqualTo("+34666666666");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "34666666666", "+0666666666", "+34abc", "+123", "+1234567890123456"})
  void rejectsInvalidNumbers(String input) {
    assertThat(PhoneNumber.isValid(input)).isFalse();
    assertThatIllegalArgumentException().isThrownBy(() -> PhoneNumber.parse(input));
  }

  @Test
  void nullIsNotValid() {
    assertThat(PhoneNumber.isValid(null)).isFalse();
  }
}
