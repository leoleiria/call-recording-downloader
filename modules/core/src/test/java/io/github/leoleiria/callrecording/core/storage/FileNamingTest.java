package io.github.leoleiria.callrecording.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.leoleiria.callrecording.core.domain.Call;
import io.github.leoleiria.callrecording.core.domain.CallDirection;
import io.github.leoleiria.callrecording.core.domain.MediaKind;
import io.github.leoleiria.callrecording.core.domain.MediaRef;
import io.github.leoleiria.callrecording.core.domain.PhoneNumber;
import java.net.URI;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileNamingTest {

  private final FileNaming naming = new FileNaming(ZoneId.of("Europe/Madrid"));
  private final Call call =
      new Call(
          "abc/123:x",
          OffsetDateTime.parse("2025-01-15T13:32:05Z"),
          CallDirection.OUTBOUND,
          10,
          List.of(),
          true);

  @Test
  void contactFolderIsTheNumberWithoutPlus() {
    assertThat(naming.contactFolder(Path.of("base"), PhoneNumber.parse("+34666666666")))
        .isEqualTo(Path.of("base", "34666666666"));
  }

  @Test
  void baseNameUsesLocalTimeDirectionAndSafeCallId() {
    assertThat(naming.baseName(call)).isEqualTo("2025-01-15_14-32-05_out_abc_123_x");
  }

  @Test
  void mediaNamesDistinguishVoicemailsAndRepeatedFiles() {
    MediaRef recording = new MediaRef(MediaKind.RECORDING, URI.create("https://x/r.WAV"));
    MediaRef voicemail = new MediaRef(MediaKind.VOICEMAIL, URI.create("https://x/v"));

    assertThat(naming.mediaFileName(call, recording, 0))
        .isEqualTo("2025-01-15_14-32-05_out_abc_123_x.wav");
    assertThat(naming.mediaFileName(call, recording, 1))
        .isEqualTo("2025-01-15_14-32-05_out_abc_123_x_2.wav");
    assertThat(naming.mediaFileName(call, voicemail, 0))
        .isEqualTo("2025-01-15_14-32-05_out_abc_123_x_voicemail.mp3");
  }

  @Test
  void transcriptionJsonGoesToItsOwnFolder() {
    assertThat(naming.transcriptTextFileName(call))
        .isEqualTo("2025-01-15_14-32-05_out_abc_123_x_transcription.txt");
    assertThat(naming.transcriptJsonFileName(call))
        .isEqualTo(
            "OriginalTranscription_Json/2025-01-15_14-32-05_out_abc_123_x_transcription.json");
  }
}
