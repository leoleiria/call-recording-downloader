package io.github.leoleiria.callrecording.core.usecase;

import io.github.leoleiria.callrecording.core.domain.CallDirection;
import io.github.leoleiria.callrecording.core.domain.CallLog;
import io.github.leoleiria.callrecording.core.domain.Connection;
import io.github.leoleiria.callrecording.core.domain.MediaKind;
import io.github.leoleiria.callrecording.core.domain.MediaRef;
import io.github.leoleiria.callrecording.core.domain.PhoneNumber;
import io.github.leoleiria.callrecording.core.domain.Region;
import io.github.leoleiria.callrecording.core.security.ApiKey;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;

/** Test data shared by the use-case tests. */
final class Fixtures {

  static final PhoneNumber CONTACT = PhoneNumber.parse("+34666666666");
  static final Connection CONNECTION =
      new Connection(Region.EUROPE, ApiKey.of("test-key-0123456789"));

  private Fixtures() {}

  static MediaRef recording(String name) {
    return new MediaRef(MediaKind.RECORDING, URI.create("https://cdn.ringover.com/" + name));
  }

  static MediaRef voicemail(String name) {
    return new MediaRef(MediaKind.VOICEMAIL, URI.create("https://cdn.ringover.com/" + name));
  }

  static CallLog log(
      long cdrId,
      String callId,
      String startTime,
      Optional<MediaRef> recording,
      Optional<MediaRef> voicemail,
      boolean transcription) {
    return new CallLog(
        cdrId,
        callId,
        OffsetDateTime.parse(startTime),
        CallDirection.INBOUND,
        60,
        recording,
        voicemail,
        transcription);
  }
}
