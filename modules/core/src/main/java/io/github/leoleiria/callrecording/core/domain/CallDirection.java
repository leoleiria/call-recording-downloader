package io.github.leoleiria.callrecording.core.domain;

import java.util.Locale;

/** Whether the call was received or placed by the Ringover account. */
public enum CallDirection {
  INBOUND("in"),
  OUTBOUND("out"),
  UNKNOWN("unknown");

  private final String code;

  CallDirection(String code) {
    this.code = code;
  }

  /** Short code used in file names: {@code in}, {@code out} or {@code unknown}. */
  public String code() {
    return code;
  }

  /** Parses the API value ({@code "in"} or {@code "out"}); anything else is {@link #UNKNOWN}. */
  public static CallDirection fromApi(String value) {
    if (value == null) {
      return UNKNOWN;
    }
    return switch (value.strip().toLowerCase(Locale.ROOT)) {
      case "in" -> INBOUND;
      case "out" -> OUTBOUND;
      default -> UNKNOWN;
    };
  }
}
