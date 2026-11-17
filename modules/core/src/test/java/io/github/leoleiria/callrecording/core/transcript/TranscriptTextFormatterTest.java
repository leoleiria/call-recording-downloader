package io.github.leoleiria.callrecording.core.transcript;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.leoleiria.callrecording.core.transcript.Transcription.Speaker;
import io.github.leoleiria.callrecording.core.transcript.Transcription.Speech;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranscriptTextFormatterTest {

  private static final String NL = System.lineSeparator();
  private final TranscriptTextFormatter formatter =
      new TranscriptTextFormatter("Caller", "Callee", "Speaker");

  @Test
  void formatsSpeechesWithTimestampsAndLabels() {
    Transcription transcription =
        new Transcription(
            "1",
            List.of(
                new Speech(Speaker.CALLER, 0.4, " Hello "),
                new Speech(Speaker.CALLEE, 65.9, "Good morning"),
                new Speech(Speaker.UNKNOWN, 3725, "Bye"),
                new Speech(Speaker.CALLER, 3726, "  ")),
            "",
            "{}");

    assertThat(formatter.format(transcription))
        .isEqualTo(
            "[00:00] Caller: Hello"
                + NL
                + "[01:05] Callee: Good morning"
                + NL
                + "[1:02:05] Speaker: Bye"
                + NL);
  }

  @Test
  void fallsBackToFullTextWithoutSpeeches() {
    Transcription transcription = new Transcription("1", List.of(), " whole text ", "{}");

    assertThat(formatter.format(transcription)).isEqualTo("whole text" + NL);
  }
}
