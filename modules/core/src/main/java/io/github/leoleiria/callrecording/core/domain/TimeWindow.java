package io.github.leoleiria.callrecording.core.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A closed time interval {@code [from, to]} in UTC, as sent to the API.
 *
 * @param from first instant included
 * @param to last instant included
 */
public record TimeWindow(Instant from, Instant to) {

  public TimeWindow {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("Window ends before it starts: " + from + " > " + to);
    }
  }
}
