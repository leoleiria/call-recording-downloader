package io.github.leoleiria.callrecording.core.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A call, made of one or more {@link CallLog} entries sharing the same call id.
 *
 * @param callId the call id
 * @param startTime start of the first leg
 * @param direction direction of the first leg
 * @param durationSeconds longest leg duration, in seconds
 * @param media distinct recordings and voicemails of all legs, in leg order
 * @param transcriptionAvailable whether any leg reports a transcription
 */
public record Call(
    String callId,
    OffsetDateTime startTime,
    CallDirection direction,
    long durationSeconds,
    List<MediaRef> media,
    boolean transcriptionAvailable) {

  public Call {
    Objects.requireNonNull(callId, "callId");
    Objects.requireNonNull(startTime, "startTime");
    Objects.requireNonNull(direction, "direction");
    media = List.copyOf(media);
  }

  /**
   * Builds a call from all of its log entries.
   *
   * @throws IllegalArgumentException if {@code logs} is empty or mixes different call ids
   */
  public static Call fromLogs(List<CallLog> logs) {
    if (logs.isEmpty()) {
      throw new IllegalArgumentException("A call needs at least one log entry");
    }
    List<CallLog> ordered = new ArrayList<>(logs);
    ordered.sort(
        Comparator.comparing(CallLog::startTime, OffsetDateTime.timeLineOrder())
            .thenComparingLong(CallLog::cdrId));
    CallLog first = ordered.get(0);
    Set<MediaRef> media = new LinkedHashSet<>();
    long duration = 0;
    boolean transcription = false;
    for (CallLog log : ordered) {
      if (!log.callId().equals(first.callId())) {
        throw new IllegalArgumentException(
            "Mixed call ids: " + first.callId() + " and " + log.callId());
      }
      log.recording().ifPresent(media::add);
      log.voicemail().ifPresent(media::add);
      duration = Math.max(duration, log.durationSeconds());
      transcription |= log.transcriptionAvailable();
    }
    return new Call(
        first.callId(),
        first.startTime(),
        first.direction(),
        duration,
        List.copyOf(media),
        transcription);
  }

  /** Number of media files of the given kind. */
  public long mediaCount(MediaKind kind) {
    return media.stream().filter(m -> m.kind() == kind).count();
  }
}
