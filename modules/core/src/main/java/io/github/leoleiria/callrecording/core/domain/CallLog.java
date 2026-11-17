package io.github.leoleiria.callrecording.core.domain;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * One call log entry (CDR) as returned by the API.
 *
 * <p>A single call can produce several entries sharing the same {@code callId}, for example when it
 * is transferred or routed through an IVR. {@link Call} groups them.
 *
 * @param cdrId unique id of this entry
 * @param callId id shared by all entries of the same call
 * @param startTime when this leg started
 * @param direction inbound or outbound
 * @param durationSeconds total duration of this leg, in seconds
 * @param recording recording of this leg, if any
 * @param voicemail voicemail of this leg, if any
 * @param transcriptionAvailable whether the API reports a transcription for the call
 */
public record CallLog(
    long cdrId,
    String callId,
    OffsetDateTime startTime,
    CallDirection direction,
    long durationSeconds,
    Optional<MediaRef> recording,
    Optional<MediaRef> voicemail,
    boolean transcriptionAvailable) {

  public CallLog {
    Objects.requireNonNull(callId, "callId");
    Objects.requireNonNull(startTime, "startTime");
    Objects.requireNonNull(direction, "direction");
    Objects.requireNonNull(recording, "recording");
    Objects.requireNonNull(voicemail, "voicemail");
  }
}
