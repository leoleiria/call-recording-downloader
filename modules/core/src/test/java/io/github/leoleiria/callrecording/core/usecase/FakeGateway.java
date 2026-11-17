package io.github.leoleiria.callrecording.core.usecase;

import io.github.leoleiria.callrecording.core.domain.CallLog;
import io.github.leoleiria.callrecording.core.domain.Connection;
import io.github.leoleiria.callrecording.core.domain.MediaRef;
import io.github.leoleiria.callrecording.core.domain.PhoneNumber;
import io.github.leoleiria.callrecording.core.domain.TimeWindow;
import io.github.leoleiria.callrecording.core.port.CallGateway;
import io.github.leoleiria.callrecording.core.port.CancellationToken;
import io.github.leoleiria.callrecording.core.port.GatewayException;
import io.github.leoleiria.callrecording.core.transcript.Transcription;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link CallGateway} for use-case tests. */
final class FakeGateway implements CallGateway {

  final List<CallLog> logs = new ArrayList<>();
  final Map<URI, byte[]> media = new HashMap<>();
  final Map<URI, GatewayException> mediaFailures = new HashMap<>();
  final Map<String, Transcription> transcriptions = new HashMap<>();
  final List<TimeWindow> searchedWindows = new ArrayList<>();
  final List<URI> downloadedMedia = new ArrayList<>();
  final List<String> fetchedTranscriptions = new ArrayList<>();
  GatewayException transcriptionFailure;

  @Override
  public List<CallLog> findCallLogs(
      Connection connection,
      PhoneNumber contact,
      TimeWindow window,
      CancellationToken cancellation) {
    searchedWindows.add(window);
    return logs.stream()
        .filter(
            log -> {
              var instant = log.startTime().toInstant();
              return !instant.isBefore(window.from()) && !instant.isAfter(window.to());
            })
        .toList();
  }

  @Override
  public void downloadMedia(Connection connection, MediaRef ref, OutputStream target) {
    downloadedMedia.add(ref.url());
    GatewayException failure = mediaFailures.get(ref.url());
    if (failure != null) {
      throw failure;
    }
    try {
      target.write(media.getOrDefault(ref.url(), "audio".getBytes(StandardCharsets.UTF_8)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public Optional<Transcription> fetchTranscription(Connection connection, String callId) {
    fetchedTranscriptions.add(callId);
    if (transcriptionFailure != null) {
      throw transcriptionFailure;
    }
    return Optional.ofNullable(transcriptions.get(callId));
  }
}
