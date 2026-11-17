package io.github.leoleiria.callrecording.core.transcript;

import java.util.List;
import java.util.Objects;

/**
 * Transcription of a call.
 *
 * @param callId the call it belongs to
 * @param speeches what was said, in order
 * @param fullText the whole transcription as plain text, used when there are no speeches
 * @param originalJson the API response, kept unchanged so nothing is lost
 */
public record Transcription(
    String callId, List<Speech> speeches, String fullText, String originalJson) {

  public Transcription {
    Objects.requireNonNull(callId, "callId");
    Objects.requireNonNull(originalJson, "originalJson");
    speeches = List.copyOf(speeches);
    fullText = fullText == null ? "" : fullText;
  }

  /**
   * One utterance.
   *
   * @param speaker who spoke
   * @param startSeconds offset from the start of the recording, in seconds
   * @param text what was said
   */
  public record Speech(Speaker speaker, double startSeconds, String text) {

    public Speech {
      Objects.requireNonNull(speaker, "speaker");
      text = text == null ? "" : text.strip();
    }
  }

  /** Call participant, as identified by the API audio channel. */
  public enum Speaker {
    /** The party who placed the call (API channel 1). */
    CALLER,
    /** The party who received the call (API channel 0). */
    CALLEE,
    /** Channel not reported. */
    UNKNOWN
  }
}
