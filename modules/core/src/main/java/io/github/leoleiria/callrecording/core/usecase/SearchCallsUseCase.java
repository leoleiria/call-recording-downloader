package io.github.leoleiria.callrecording.core.usecase;

import io.github.leoleiria.callrecording.core.domain.Call;
import io.github.leoleiria.callrecording.core.domain.CallLog;
import io.github.leoleiria.callrecording.core.domain.Connection;
import io.github.leoleiria.callrecording.core.domain.DateRange;
import io.github.leoleiria.callrecording.core.domain.PhoneNumber;
import io.github.leoleiria.callrecording.core.domain.SearchResult;
import io.github.leoleiria.callrecording.core.domain.TimeWindow;
import io.github.leoleiria.callrecording.core.port.CallGateway;
import io.github.leoleiria.callrecording.core.port.CancellationToken;
import io.github.leoleiria.callrecording.core.port.ProgressListener;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds every call with a contact in a date range.
 *
 * <p>The range (at most one year) is split into windows of {@value #MAX_DAYS_PER_REQUEST} days, log
 * entries are de-duplicated and grouped into calls, and calls are sorted most recent first. A full
 * year therefore takes 27 window searches, plus one extra request per additional page.
 */
public final class SearchCallsUseCase {

  /**
   * Days per call search. The API documents a 15-day maximum but rejected 15 local days in
   * practice: once converted to UTC they can touch 16 calendar dates. 14 days stays safely inside
   * the limit in every time zone.
   */
  public static final int MAX_DAYS_PER_REQUEST = 14;

  private static final Logger LOG = LoggerFactory.getLogger(SearchCallsUseCase.class);

  private final CallGateway gateway;

  public SearchCallsUseCase(CallGateway gateway) {
    this.gateway = Objects.requireNonNull(gateway, "gateway");
  }

  /**
   * Runs the search.
   *
   * @param zone the user's time zone, used to interpret the dates
   * @param progress reports windows searched out of the total
   * @throws IllegalArgumentException if the range is longer than one year
   * @throws io.github.leoleiria.callrecording.core.port.GatewayException if the API fails
   * @throws java.util.concurrent.CancellationException if cancelled
   */
  public SearchResult search(
      Connection connection,
      PhoneNumber contact,
      DateRange range,
      ZoneId zone,
      ProgressListener progress,
      CancellationToken cancellation) {
    if (range.isLongerThanOneYear()) {
      throw new IllegalArgumentException("The period cannot be longer than one year: " + range);
    }
    List<TimeWindow> windows = range.toWindows(zone, MAX_DAYS_PER_REQUEST);
    LOG.info("Searching calls in {} ({} request window(s))", range, windows.size());

    Set<Long> seenCdrIds = new HashSet<>();
    Map<String, List<CallLog>> logsByCall = new LinkedHashMap<>();
    progress.onProgress(0, windows.size());
    for (int i = 0; i < windows.size(); i++) {
      cancellation.throwIfCancelled();
      for (CallLog log : gateway.findCallLogs(connection, contact, windows.get(i), cancellation)) {
        if (seenCdrIds.add(log.cdrId())) {
          logsByCall.computeIfAbsent(log.callId(), id -> new ArrayList<>()).add(log);
        }
      }
      progress.onProgress(i + 1, windows.size());
    }

    List<Call> calls = new ArrayList<>(logsByCall.size());
    for (List<CallLog> logs : logsByCall.values()) {
      calls.add(Call.fromLogs(logs));
    }
    calls.sort(Comparator.comparing(Call::startTime, OffsetDateTime.timeLineOrder()).reversed());

    SearchResult result = new SearchResult(contact, range, calls);
    LOG.info(
        "Found {} call(s), {} audio file(s), {} transcription(s)",
        result.callCount(),
        result.audioCount(),
        result.transcriptionCount());
    return result;
  }
}
