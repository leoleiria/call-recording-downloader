package io.github.leoleiria.callrecording.core.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A phone number in E.164 international format, for example {@code +34666666666}.
 *
 * <p>Spaces, dashes, dots and parentheses typed by the user are ignored. The leading {@code +} and
 * the country code are mandatory.
 *
 * @param e164 the normalized number, including the leading {@code +}
 */
public record PhoneNumber(String e164) {

  private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{6,14}$");
  private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-.()]");

  public PhoneNumber {
    Objects.requireNonNull(e164, "e164");
    if (!E164.matcher(e164).matches()) {
      throw new IllegalArgumentException("Not an E.164 phone number: " + e164);
    }
  }

  /**
   * Parses user input.
   *
   * @throws IllegalArgumentException if the input is not a valid international number
   */
  public static PhoneNumber parse(String input) {
    Objects.requireNonNull(input, "input");
    return new PhoneNumber(SEPARATORS.matcher(input.strip()).replaceAll(""));
  }

  /** Returns {@code true} if {@code input} would be accepted by {@link #parse(String)}. */
  public static boolean isValid(String input) {
    if (input == null) {
      return false;
    }
    return E164.matcher(SEPARATORS.matcher(input.strip()).replaceAll("")).matches();
  }

  /** The number without the leading {@code +}, for example {@code 34666666666}. */
  public String digits() {
    return e164.substring(1);
  }

  @Override
  public String toString() {
    return e164;
  }
}
