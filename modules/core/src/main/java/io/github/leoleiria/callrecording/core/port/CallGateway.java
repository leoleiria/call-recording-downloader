package io.github.leoleiria.callrecording.core.port;

import io.github.leoleiria.callrecording.core.domain.CallLog;
import io.github.leoleiria.callrecording.core.domain.Connection;
import io.github.leoleiria.callrecording.core.domain.MediaRef;
import io.github.leoleiria.callrecording.core.domain.PhoneNumber;
import io.github.leoleiria.callrecording.core.domain.TimeWindow;
import io.github.leoleiria.callrecording.core.transcript.Transcription;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

/**
 * Access to a telephony provider's call history. Implemented by the Ringover client module.
 *
 * <p>Implementations must be thread-safe and must report failures as {@link GatewayException}.
 */
public interface CallGateway {

  /**
   * Returns every call log entry with {@code contact} inside {@code window}, following all pages.
   *
   * @param window at most {@value
   *     io.github.leoleiria.callrecording.core.usecase.SearchCallsUseCase#MAX_DAYS_PER_REQUEST}
   *     days long
   * @param cancellation checked between pages
   */
  List<CallLog> findCallLogs(
      Connection connection,
      PhoneNumber contact,
      TimeWindow window,
      CancellationToken cancellation);

  /** Streams an audio file into {@code target}. The stream is not closed. */
  void downloadMedia(Connection connection, MediaRef media, OutputStream target);

  /**
   * Fetches the transcription of a call.
   *
   * @return empty if the call has no transcription
   */
  Optional<Transcription> fetchTranscription(Connection connection, String callId);
}
