package io.github.leoleiria.callrecording.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class DateRangeTest {

  private static final ZoneId UTC = ZoneOffset.UTC;

  @Test
  void singleDayCoversWholeDay() {
    DateRange range = new DateRange(LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 10));

    List<TimeWindow> windows = range.toWindows(UTC, 14);

    assertThat(windows)
        .containsExactly(
            new TimeWindow(
                Instant.parse("2025-01-10T00:00:00Z"), Instant.parse("2025-01-10T23:59:59Z")));
  }

  @Test
  void longRangeIsSplitIntoContiguousWindows() {
    DateRange range = new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 4));

    List<TimeWindow> windows = range.toWindows(UTC, 14);

    assertThat(windows).hasSize(3);
    assertThat(windows.get(0).from()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
    assertThat(windows.get(0).to()).isEqualTo(Instant.parse("2025-01-14T23:59:59Z"));
    assertThat(windows.get(1).from()).isEqualTo(Instant.parse("2025-01-15T00:00:00Z"));
    assertThat(windows.get(2).to()).isEqualTo(Instant.parse("2025-02-04T23:59:59Z"));
    for (int i = 1; i < windows.size(); i++) {
      assertThat(windows.get(i).from()).isEqualTo(windows.get(i - 1).to().plusSeconds(1));
    }
  }

  @Test
  void aFullYearNeedsTwentySevenWindowsOfFourteenDays() {
    DateRange year = new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
    DateRange leapYear = new DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

    assertThat(year.days()).isEqualTo(365);
    assertThat(year.toWindows(UTC, 14)).hasSize(27);
    assertThat(leapYear.days()).isEqualTo(366);
    assertThat(leapYear.toWindows(UTC, 14)).hasSize(27);
  }

  @Test
  void everyWindowStaysWithinFourteenDays() {
    DateRange year = new DateRange(LocalDate.of(2025, 3, 1), LocalDate.of(2026, 2, 28));

    assertThat(year.toWindows(ZoneId.of("Europe/Madrid"), 14))
        .allSatisfy(
            w ->
                assertThat(java.time.Duration.between(w.from(), w.to()))
                    .isLessThan(java.time.Duration.ofDays(14).plusHours(1)));
  }

  @Test
  void oneYearIsTheLongestAcceptedRange() {
    LocalDate start = LocalDate.of(2025, 1, 1);

    assertThat(new DateRange(start, LocalDate.of(2025, 12, 31)).isLongerThanOneYear()).isFalse();
    assertThat(new DateRange(start, LocalDate.of(2026, 1, 1)).isLongerThanOneYear()).isTrue();
    assertThat(new DateRange(start, start).isLongerThanOneYear()).isFalse();
  }

  @Test
  void daysAreInterpretedInTheUserTimeZone() {
    DateRange range = new DateRange(LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 1));

    TimeWindow window = range.toWindows(ZoneId.of("Europe/Madrid"), 14).get(0);

    assertThat(window.from()).isEqualTo(Instant.parse("2025-06-30T22:00:00Z"));
    assertThat(window.to()).isEqualTo(Instant.parse("2025-07-01T21:59:59Z"));
  }

  @Test
  void rejectsEndBeforeStart() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new DateRange(LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 1)));
  }
}
