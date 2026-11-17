package io.github.leoleiria.callrecording.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CallTest {

  private static final MediaRef RECORDING =
      new MediaRef(MediaKind.RECORDING, URI.create("https://cdn.ringover.com/a.mp3"));
  private static final MediaRef VOICEMAIL =
      new MediaRef(MediaKind.VOICEMAIL, URI.create("https://cdn.ringover.com/v.mp3"));

  @Test
  void groupsLegsOfTheSameCall() {
    CallLog transferred = log(2, "10:05:00", Optional.empty(), Optional.of(VOICEMAIL), true, 30);
    CallLog first = log(1, "10:00:00", Optional.of(RECORDING), Optional.empty(), false, 120);

    Call call = Call.fromLogs(List.of(transferred, first));

    assertThat(call.callId()).isEqualTo("call-1");
    assertThat(call.startTime()).isEqualTo(OffsetDateTime.parse("2025-01-10T10:00:00Z"));
    assertThat(call.media()).containsExactly(RECORDING, VOICEMAIL);
    assertThat(call.durationSeconds()).isEqualTo(120);
    assertThat(call.transcriptionAvailable()).isTrue();
    assertThat(call.mediaCount(MediaKind.VOICEMAIL)).isEqualTo(1);
  }

  @Test
  void sameRecordingOnTwoLegsIsCountedOnce() {
    Call call =
        Call.fromLogs(
            List.of(
                log(1, "10:00:00", Optional.of(RECORDING), Optional.empty(), false, 10),
                log(2, "10:01:00", Optional.of(RECORDING), Optional.empty(), false, 10)));

    assertThat(call.media()).containsExactly(RECORDING);
  }

  @Test
  void rejectsMixedCallIds() {
    CallLog other =
        new CallLog(
            9,
            "other",
            OffsetDateTime.parse("2025-01-10T10:00:00Z"),
            CallDirection.INBOUND,
            1,
            Optional.empty(),
            Optional.empty(),
            false);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                Call.fromLogs(
                    List.of(
                        log(1, "10:00:00", Optional.empty(), Optional.empty(), false, 1), other)));
  }

  private static CallLog log(
      long cdrId,
      String time,
      Optional<MediaRef> recording,
      Optional<MediaRef> voicemail,
      boolean transcription,
      long duration) {
    return new CallLog(
        cdrId,
        "call-1",
        OffsetDateTime.parse("2025-01-10T" + time + "Z"),
        CallDirection.INBOUND,
        duration,
        recording,
        voicemail,
        transcription);
  }
}
