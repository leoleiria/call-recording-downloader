package io.github.leoleiria.callrecording.core.domain;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A range of calendar days chosen by the user, both ends inclusive.
 *
 * @param start first day included
 * @param end last day included
 */
public record DateRange(LocalDate start, LocalDate end) {

  public DateRange {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    if (end.isBefore(start)) {
      throw new IllegalArgumentException("End date " + end + " is before start date " + start);
    }
  }

  /** Number of calendar days in the range, both ends included. */
  public long days() {
    return ChronoUnit.DAYS.between(start, end) + 1;
  }

  /**
   * Whether the range is longer than one year, for example 2025-01-01 to 2026-01-01. The longest
   * accepted range is 2025-01-01 to 2025-12-31 (365 days, or 366 in a leap year).
   */
  public boolean isLongerThanOneYear() {
    return end.isAfter(start.plusYears(1).minusDays(1));
  }

  /**
   * Splits the range into consecutive UTC windows of at most {@code maxDays} days each.
   *
   * <p>Days are interpreted in {@code zone}: the range covers from midnight of the first day to the
   * last second of the last day, in the user's local time. The Ringover API limits the period of a
   * single call search, so long ranges must be split.
   *
   * @param zone the user's time zone
   * @param maxDays maximum number of calendar days per window, at least 1
   */
  public List<TimeWindow> toWindows(ZoneId zone, int maxDays) {
    Objects.requireNonNull(zone, "zone");
    if (maxDays < 1) {
      throw new IllegalArgumentException("maxDays must be at least 1");
    }
    List<TimeWindow> windows = new ArrayList<>();
    LocalDate windowStart = start;
    while (!windowStart.isAfter(end)) {
      LocalDate windowEnd = windowStart.plusDays(maxDays - 1L);
      if (windowEnd.isAfter(end)) {
        windowEnd = end;
      }
      windows.add(
          new TimeWindow(
              windowStart.atStartOfDay(zone).toInstant(),
              windowEnd.plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1)));
      windowStart = windowEnd.plusDays(1);
    }
    return List.copyOf(windows);
  }
}
