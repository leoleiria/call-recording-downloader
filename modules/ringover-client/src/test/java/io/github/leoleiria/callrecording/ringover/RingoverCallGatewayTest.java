package io.github.leoleiria.callrecording.ringover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leoleiria.callrecording.core.domain.CallDirection;
import io.github.leoleiria.callrecording.core.domain.CallLog;
import io.github.leoleiria.callrecording.core.domain.Connection;
import io.github.leoleiria.callrecording.core.domain.MediaKind;
import io.github.leoleiria.callrecording.core.domain.MediaRef;
import io.github.leoleiria.callrecording.core.domain.PhoneNumber;
import io.github.leoleiria.callrecording.core.domain.Region;
import io.github.leoleiria.callrecording.core.domain.TimeWindow;
import io.github.leoleiria.callrecording.core.port.CancellationToken;
import io.github.leoleiria.callrecording.core.port.GatewayException;
import io.github.leoleiria.callrecording.core.port.GatewayException.Reason;
import io.github.leoleiria.callrecording.core.security.ApiKey;
import io.github.leoleiria.callrecording.core.security.SecretRedactor;
import io.github.leoleiria.callrecording.core.transcript.Transcription;
import io.github.leoleiria.callrecording.core.transcript.Transcription.Speaker;
import io.github.leoleiria.callrecording.ringover.FakeRingoverServer.Recorded;
import io.github.leoleiria.callrecording.ringover.FakeRingoverServer.Reply;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RingoverCallGatewayTest {

  private static final String KEY = "test-api-key-0123456789";
  private static final Connection CONNECTION = new Connection(Region.EUROPE, ApiKey.of(KEY));
  private static final PhoneNumber CONTACT = PhoneNumber.parse("+34666666666");
  private static final TimeWindow WINDOW =
      new TimeWindow(Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-01-15T23:59:59Z"));

  private final ObjectMapper json = new ObjectMapper();
  private final List<Duration> sleeps = new ArrayList<>();
  private FakeRingoverServer server;
  private RingoverCallGateway gateway;

  @BeforeEach
  void setUp() throws Exception {
    server = new FakeRingoverServer();
    gateway =
        RingoverCallGateway.builder()
            .baseUri(region -> server.baseUri())
            .rateLimiter(new RateLimiter(1000, System::nanoTime, sleeps::add))
            .sleeper(sleeps::add)
            .directDownloadAllowed(uri -> "127.0.0.1".equals(uri.getHost()))
            .build();
  }

  @AfterEach
  void tearDown() {
    server.close();
    SecretRedactor.clear();
  }

  // --- Calls -----------------------------------------------------------------------------------

  @Test
  void searchSendsKeyAndAdvancedFilterOnTheContactNumber() throws Exception {
    server.on("/v2/calls", Reply.json(200, callList(call(10, "A", true, true, false))));

    List<CallLog> logs = find();

    Recorded request = server.requestsTo("/v2/calls").get(0);
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.query()).isEqualTo("expand=transcription");
    assertThat(request.header("Authorization")).contains(KEY);
    JsonNode body = json.readTree(request.body());
    assertThat(body.path("filter").asText()).isEqualTo("ADVANCED");
    assertThat(body.path("start_date").asText()).isEqualTo("2025-01-01T00:00:00Z");
    assertThat(body.path("end_date").asText()).isEqualTo("2025-01-15T23:59:59Z");
    assertThat(body.path("advanced").path("ext_numbers").get(0).asLong()).isEqualTo(34666666666L);
    assertThat(body.has("last_id_returned")).isFalse();

    assertThat(logs)
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.cdrId()).isEqualTo(10);
              assertThat(log.callId()).isEqualTo("A");
              assertThat(log.direction()).isEqualTo(CallDirection.INBOUND);
              assertThat(log.durationSeconds()).isEqualTo(42);
              assertThat(log.recording()).map(MediaRef::kind).contains(MediaKind.RECORDING);
              assertThat(log.voicemail()).isEmpty();
              assertThat(log.transcriptionAvailable()).isTrue();
            });
  }

  @Test
  void followsTheCursorUntilAShortPage() throws Exception {
    List<String> fullPage = new ArrayList<>();
    for (int i = 0; i < RingoverCallGateway.PAGE_SIZE; i++) {
      fullPage.add(call(1000 - i, "C" + i, false, false, false));
    }
    server.on(
        "/v2/calls",
        Reply.json(200, callList(fullPage.toArray(String[]::new))),
        Reply.json(200, callList(call(5, "last", false, false, true))));

    List<CallLog> logs = find();

    assertThat(logs).hasSize(RingoverCallGateway.PAGE_SIZE + 1);
    List<Recorded> requests = server.requestsTo("/v2/calls");
    assertThat(requests).hasSize(2);
    long expectedCursor = 1000 - RingoverCallGateway.PAGE_SIZE + 1;
    assertThat(json.readTree(requests.get(1).body()).path("last_id_returned").asLong())
        .isEqualTo(expectedCursor);
    assertThat(logs.get(logs.size() - 1).voicemail())
        .map(MediaRef::kind)
        .contains(MediaKind.VOICEMAIL);
  }

  @Test
  void noContentMeansNoCalls() {
    server.on("/v2/calls", Reply.status(204));

    assertThat(find()).isEmpty();
  }

  @Test
  void rejectedKeyIsUnauthorized() {
    server.on("/v2/calls", Reply.status(401));

    assertThatExceptionOfType(GatewayException.class)
        .isThrownBy(this::find)
        .satisfies(e -> assertThat(e.reason()).isEqualTo(Reason.UNAUTHORIZED))
        .withMessageNotContaining(KEY);
  }

  @Test
  void retriesWhenRateLimitedHonouringRetryAfter() {
    server.on(
        "/v2/calls",
        Reply.status(429).withHeader("Retry-After", "3"),
        Reply.json(200, callList(call(1, "A", false, false, false))));

    assertThat(find()).hasSize(1);
    assertThat(server.requestsTo("/v2/calls")).hasSize(2);
    assertThat(sleeps).contains(Duration.ofSeconds(3));
  }

  @Test
  void givesUpAfterTheMaximumAttempts() {
    server.on("/v2/calls", Reply.status(503));

    assertThatExceptionOfType(GatewayException.class)
        .isThrownBy(this::find)
        .satisfies(e -> assertThat(e.reason()).isEqualTo(Reason.SERVER_ERROR));
    assertThat(server.requestsTo("/v2/calls")).hasSize(RetryPolicy.ringoverDefault().maxAttempts());
  }

  @Test
  void unreadableEntriesAreSkipped() {
    server.on(
        "/v2/calls",
        Reply.json(200, callList("{\"cdr_id\": 1}", call(2, "ok", false, false, false))));

    assertThat(find()).extracting(CallLog::callId).containsExactly("ok");
  }

  // --- Media -----------------------------------------------------------------------------------

  @Test
  void privateCdnFilesGoThroughTheDownloadProxy() throws Exception {
    URI cdn =
        URI.create("https://cdn.ringover.com/v2/private/download/teams/1/types/records/x/y.mp3");
    server.on(
        "/v2/cdn/download",
        new Reply(200, "AUDIO".getBytes(StandardCharsets.UTF_8), java.util.Map.of()));
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    gateway.downloadMedia(CONNECTION, new MediaRef(MediaKind.RECORDING, cdn), out);

    assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("AUDIO");
    Recorded request = server.requestsTo("/v2/cdn/download").get(0);
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.header("Authorization")).contains(KEY);
    assertThat(json.readTree(request.body()).path("url").asText()).isEqualTo(cdn.toString());
  }

  @Test
  void directDownloadsNeverCarryTheApiKey() {
    server.on("/records/a.mp3", new Reply(200, new byte[] {1, 2, 3}, java.util.Map.of()));
    URI url = server.baseUri().resolve("/records/a.mp3");
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    gateway.downloadMedia(CONNECTION, new MediaRef(MediaKind.VOICEMAIL, url), out);

    assertThat(out.toByteArray()).containsExactly(1, 2, 3);
    assertThat(server.requestsTo("/records/a.mp3").get(0).header("Authorization")).isEmpty();
  }

  @Test
  void refusesToDownloadFromUnknownHosts() {
    MediaRef elsewhere = new MediaRef(MediaKind.RECORDING, URI.create("https://example.com/a.mp3"));

    assertThatExceptionOfType(GatewayException.class)
        .isThrownBy(
            () -> gateway.downloadMedia(CONNECTION, elsewhere, new ByteArrayOutputStream()));
    assertThat(server.requests).isEmpty();
  }

  @Test
  void defaultDirectDownloadRuleOnlyAcceptsRingoverHttps() {
    assertThat(RingoverCallGateway.isRingoverHttps(URI.create("https://cdn.ringover.com/a.mp3")))
        .isTrue();
    assertThat(RingoverCallGateway.isRingoverHttps(URI.create("http://cdn.ringover.com/a.mp3")))
        .isFalse();
    assertThat(RingoverCallGateway.isRingoverHttps(URI.create("https://ringover.com.evil.io/a")))
        .isFalse();
  }

  // --- Transcriptions --------------------------------------------------------------------------

  @Test
  void parsesTranscriptionSpeeches() {
    server.on(
        "/v2/transcriptions/A",
        Reply.json(
            200,
            """
            [{"call_id": "A", "transcription_data": {
              "text": "Hello Hi",
              "speeches": [
                {"channelId": 1, "start": 0.5, "text": "Hello"},
                {"channelId": 0, "start": 2.0, "text": "Hi"}
              ]}}]
            """));

    Optional<Transcription> transcription = gateway.fetchTranscription(CONNECTION, "A");

    assertThat(transcription).isPresent();
    assertThat(transcription.get().speeches())
        .extracting(Transcription.Speech::speaker)
        .containsExactly(Speaker.CALLER, Speaker.CALLEE);
    assertThat(transcription.get().originalJson()).contains("\"speeches\"");
  }

  @Test
  void missingTranscriptionIsEmpty() {
    server.on("/v2/transcriptions/A", Reply.status(404));

    assertThat(gateway.fetchTranscription(CONNECTION, "A")).isEmpty();
  }

  @Test
  void transcriptionRefusedMeansFeatureNotInPlan() {
    server.on("/v2/transcriptions/A", Reply.status(401));

    assertThatExceptionOfType(GatewayException.class)
        .isThrownBy(() -> gateway.fetchTranscription(CONNECTION, "A"))
        .satisfies(e -> assertThat(e.reason()).isEqualTo(Reason.FEATURE_UNAVAILABLE));
  }

  // --- Helpers ---------------------------------------------------------------------------------

  private List<CallLog> find() {
    return gateway.findCallLogs(CONNECTION, CONTACT, WINDOW, CancellationToken.none());
  }

  private static String callList(String... calls) {
    StringJoiner list = new StringJoiner(",", "[", "]");
    for (String call : calls) {
      list.add(call);
    }
    return "{\"call_list_count\": " + calls.length + ", \"call_list\": " + list + "}";
  }

  private static String call(
      long cdrId, String callId, boolean record, boolean transcription, boolean voicemail) {
    return """
        {"cdr_id": %d, "call_id": "%s", "direction": "in",
         "start_time": "2025-01-10T10:00:00+01:00", "total_duration": 42,
         "record": %s, "voicemail": %s, "transcription": %s}
        """
        .formatted(
            cdrId,
            callId,
            record ? "\"https://cdn.ringover.com/records/" + callId + ".mp3\"" : "null",
            voicemail ? "\"https://cdn.ringover.com/voicemails/" + callId + ".mp3\"" : "null",
            transcription ? "{\"text\": \"hi\"}" : "null");
  }
}
