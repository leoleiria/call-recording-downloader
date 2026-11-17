package io.github.leoleiria.callrecording.core.transcript;

import io.github.leoleiria.callrecording.core.transcript.Transcription.Speaker;
import io.github.leoleiria.callrecording.core.transcript.Transcription.Speech;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Turns a {@link Transcription} into a readable text file:
 *
 * <pre>
 * [00:00] Caller: Hello, good morning.
 * [00:03] Callee: Good morning, how can I help?
 * </pre>
 */
public final class TranscriptTextFormatter {

  private final Map<Speaker, String> labels;

  /**
   * @param callerLabel localized label for the person who placed the call
   * @param calleeLabel localized label for the person who received the call
   * @param unknownLabel localized label when the speaker is not known
   */
  public TranscriptTextFormatter(String callerLabel, String calleeLabel, String unknownLabel) {
    labels = new EnumMap<>(Speaker.class);
    labels.put(Speaker.CALLER, Objects.requireNonNull(callerLabel, "callerLabel"));
    labels.put(Speaker.CALLEE, Objects.requireNonNull(calleeLabel, "calleeLabel"));
    labels.put(Speaker.UNKNOWN, Objects.requireNonNull(unknownLabel, "unknownLabel"));
  }

  /** Formats the transcription; falls back to the plain full text when there are no speeches. */
  public String format(Transcription transcription) {
    if (transcription.speeches().isEmpty()) {
      String text = transcription.fullText().strip();
      return text.isEmpty() ? "" : text + System.lineSeparator();
    }
    StringBuilder out = new StringBuilder();
    for (Speech speech : transcription.speeches()) {
      if (speech.text().isEmpty()) {
        continue;
      }
      out.append('[')
          .append(timestamp(speech.startSeconds()))
          .append("] ")
          .append(labels.get(speech.speaker()))
          .append(": ")
          .append(speech.text())
          .append(System.lineSeparator());
    }
    return out.toString();
  }

  static String timestamp(double seconds) {
    long total = Math.max(0, (long) Math.floor(seconds));
    long hours = total / 3600;
    long minutes = (total % 3600) / 60;
    long secs = total % 60;
    return hours > 0
        ? "%d:%02d:%02d".formatted(hours, minutes, secs)
        : "%02d:%02d".formatted(minutes, secs);
  }
}
