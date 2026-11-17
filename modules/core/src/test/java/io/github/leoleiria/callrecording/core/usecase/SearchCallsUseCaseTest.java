package io.github.leoleiria.callrecording.core.usecase;

import static io.github.leoleiria.callrecording.core.usecase.Fixtures.CONNECTION;
import static io.github.leoleiria.callrecording.core.usecase.Fixtures.CONTACT;
import static io.github.leoleiria.callrecording.core.usecase.Fixtures.log;
import static io.github.leoleiria.callrecording.core.usecase.Fixtures.recording;
import static io.github.leoleiria.callrecording.core.usecase.Fixtures.voicemail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.leoleiria.callrecording.core.domain.Call;
import io.github.leoleiria.callrecording.core.domain.DateRange;
import io.github.leoleiria.callrecording.core.domain.SearchResult;
import io.github.leoleiria.callrecording.core.port.CancellationToken;
import io.github.leoleiria.callrecording.core.port.ProgressListener;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class SearchCallsUseCaseTest {

  private final FakeGateway gateway = new FakeGateway();
  private final SearchCallsUseCase useCase = new SearchCallsUseCase(gateway);
  private final DateRange january =
      new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

  @Test
  void searchesEveryWindowAndCountsDistinctCalls() {
    gateway.logs.add(
        log(
            1,
            "A",
            "2025-01-02T10:00:00Z",
            Optional.of(recording("a.mp3")),
            Optional.empty(),
            true));
    gateway.logs.add(
        log(
            2,
            "A",
            "2025-01-02T10:02:00Z",
            Optional.empty(),
            Optional.of(voicemail("v.mp3")),
            true));
    gateway.logs.add(
        log(
            3,
            "B",
            "2025-01-20T09:00:00Z",
            Optional.of(recording("b.mp3")),
            Optional.empty(),
            false));
    gateway.logs.add(
        log(4, "C", "2025-01-30T09:00:00Z", Optional.empty(), Optional.empty(), false));

    SearchResult result = search(january);

    assertThat(gateway.searchedWindows).hasSize(3);
    assertThat(result.callCount()).isEqualTo(3);
    assertThat(result.audioCount()).isEqualTo(3);
    assertThat(result.voicemailCount()).isEqualTo(1);
    assertThat(result.transcriptionCount()).isEqualTo(1);
    assertThat(result.calls()).extracting(Call::callId).containsExactly("C", "B", "A");
  }

  @Test
  void reportsProgressPerWindow() {
    List<long[]> progress = new ArrayList<>();

    useCase.search(
        CONNECTION,
        CONTACT,
        january,
        ZoneOffset.UTC,
        (done, total) -> progress.add(new long[] {done, total}),
        CancellationToken.none());

    assertThat(progress).extracting(p -> p[0]).containsExactly(0L, 1L, 2L, 3L);
    assertThat(progress).extracting(p -> p[1]).containsOnly(3L);
  }

  @Test
  void emptyResultWhenThereAreNoCalls() {
    assertThat(search(january).callCount()).isZero();
  }

  @Test
  void aFullYearIsSearchedInTwentySevenRequestsOfFourteenDays() {
    search(new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));

    assertThat(gateway.searchedWindows).hasSize(27);
  }

  @Test
  void rejectsPeriodsLongerThanOneYearWithoutCallingTheApi() {
    DateRange tooLong = new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));

    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> search(tooLong));
    assertThat(gateway.searchedWindows).isEmpty();
  }

  @Test
  void stopsWhenCancelled() {
    CancellationToken token = new CancellationToken();
    token.cancel();

    assertThatExceptionOfType(CancellationException.class)
        .isThrownBy(
            () ->
                useCase.search(
                    CONNECTION, CONTACT, january, ZoneOffset.UTC, ProgressListener.none(), token));
    assertThat(gateway.searchedWindows).isEmpty();
  }

  private SearchResult search(DateRange range) {
    return useCase.search(
        CONNECTION,
        CONTACT,
        range,
        ZoneOffset.UTC,
        ProgressListener.none(),
        CancellationToken.none());
  }
}
