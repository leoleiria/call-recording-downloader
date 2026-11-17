package io.github.leoleiria.callrecording.ringover;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.leoleiria.callrecording.core.domain.CallDirection;
import io.github.leoleiria.callrecording.core.domain.CallLog;
import io.github.leoleiria.callrecording.core.domain.MediaKind;
import io.github.leoleiria.callrecording.core.domain.MediaRef;
import io.github.leoleiria.callrecording.core.transcript.Transcription;
import io.github.leoleiria.callrecording.core.transcript.Transcription.Speaker;
import io.github.leoleiria.callrecording.core.transcript.Transcription.Speech;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Maps Ringover JSON to domain objects.
 *
 * <p>Parsing is lenient on purpose: unknown fields are ignored and missing optional fields become
 * empty values, so additions to the API do not break the app.
 */
final class RingoverJson {

  private RingoverJson() {}

  /** Maps one element of {@code call_list}. */
  static CallLog toCallLog(JsonNode node) {
    String callId = text(node, "call_id").orElse(null);
    if (callId == null || callId.isBlank()) {
      throw new IllegalArgumentException("Call log without call_id");
    }
    OffsetDateTime start =
        text(node, "start_time")
            .map(RingoverJson::parseDateTime)
            .orElseThrow(() -> new IllegalArgumentException("Call log without start_time"));
    return new CallLog(
        node.path("cdr_id").asLong(),
        callId,
        start,
        CallDirection.fromApi(text(node, "direction").orElse(null)),
        node.path("total_duration").asLong(0),
        media(node, "record", MediaKind.RECORDING),
        media(node, "voicemail", MediaKind.VOICEMAIL),
        hasTranscription(node));
  }

  /**
   * Maps a {@code /transcriptions/{callId}} response: an array whose first element is used, or a
   * single object.
   */
  static Optional<Transcription> toTranscription(String callId, JsonNode root, String original) {
    JsonNode item = root.isArray() ? root.path(0) : root;
    if (item.isMissingNode() || item.isNull() || !item.isObject()) {
      return Optional.empty();
    }
    JsonNode data = item.path("transcription_data");
    List<Speech> speeches = new ArrayList<>();
    for (JsonNode speech : data.path("speeches")) {
      speeches.add(
          new Speech(
              speaker(speech.path("channelId")),
              speech.path("start").asDouble(0),
              speech.path("text").asText("")));
    }
    String fullText = data.path("text").asText("");
    if (speeches.isEmpty() && fullText.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(new Transcription(callId, speeches, fullText, original));
  }

  private static Speaker speaker(JsonNode channel) {
    if (!channel.canConvertToInt()) {
      return Speaker.UNKNOWN;
    }
    return switch (channel.asInt()) {
      case 1 -> Speaker.CALLER;
      case 0 -> Speaker.CALLEE;
      default -> Speaker.UNKNOWN;
    };
  }

  /**
   * The {@code transcription} field is only present when requested with {@code expand}. It is
   * {@code null} when the call has none and an object otherwise, possibly carrying an error if the
   * API could not load it; such a call still has a transcription to fetch later.
   */
  private static boolean hasTranscription(JsonNode node) {
    JsonNode transcription = node.path("transcription");
    return transcription.isObject();
  }

  private static Optional<MediaRef> media(JsonNode node, String field, MediaKind kind) {
    return text(node, field)
        .filter(value -> value.startsWith("https://") || value.startsWith("http://"))
        .flatMap(RingoverJson::toUri)
        .map(uri -> new MediaRef(kind, uri));
  }

  private static Optional<URI> toUri(String value) {
    try {
      return Optional.of(URI.create(value.strip()));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static Optional<String> text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return Optional.empty();
    }
    String text = value.asText("").strip();
    return text.isEmpty() ? Optional.empty() : Optional.of(text);
  }

  private static OffsetDateTime parseDateTime(String value) {
    try {
      return OffsetDateTime.parse(value);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Unexpected date format: " + value, e);
    }
  }
}
