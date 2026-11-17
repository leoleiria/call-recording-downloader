package io.github.leoleiria.callrecording.core.usecase;

import static io.github.leoleiria.callrecording.core.usecase.Fixtures.CONNECTION;
import static io.github.leoleiria.callrecording.core.usecase.Fixtures.CONTACT;
import static io.github.leoleiria.callrecording.core.usecase.Fixtures.log;
import static io.github.leoleiria.callrecording.core.usecase.Fixtures.recording;
import static io.github.leoleiria.callrecording.core.usecase.Fixtures.voicemail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.leoleiria.callrecording.core.domain.Call;
import io.github.leoleiria.callrecording.core.domain.DateRange;
import io.github.leoleiria.callrecording.core.domain.DownloadOption;
import io.github.leoleiria.callrecording.core.domain.SearchResult;
import io.github.leoleiria.callrecording.core.port.CancellationToken;
import io.github.leoleiria.callrecording.core.port.GatewayException;
import io.github.leoleiria.callrecording.core.port.GatewayException.Reason;
import io.github.leoleiria.callrecording.core.port.ProgressListener;
import io.github.leoleiria.callrecording.core.storage.Manifest;
import io.github.leoleiria.callrecording.core.transcript.TranscriptTextFormatter;
import io.github.leoleiria.callrecording.core.transcript.Transcription;
import io.github.leoleiria.callrecording.core.transcript.Transcription.Speaker;
import io.github.leoleiria.callrecording.core.transcript.Transcription.Speech;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DownloadFilesUseCaseTest {

  private static final String CALL_A = "2025-01-02_10-00-00_in_A";
  private static final String CALL_B = "2025-01-03_11-00-00_in_B";

  @TempDir Path base;

  private final FakeGateway gateway = new FakeGateway();
  private final DownloadFilesUseCase useCase = new DownloadFilesUseCase(gateway);
  private final TranscriptTextFormatter formatter =
      new TranscriptTextFormatter("Caller", "Callee", "Speaker");
  private SearchResult result;

  @BeforeEach
  void setUp() {
    Call a =
        Call.fromLogs(
            List.of(
                log(
                    1,
                    "A",
                    "2025-01-02T10:00:00Z",
                    Optional.of(recording("a.mp3")),
                    Optional.empty(),
                    true),
                log(
                    2,
                    "A",
                    "2025-01-02T10:01:00Z",
                    Optional.empty(),
                    Optional.of(voicemail("v.mp3")),
                    true)));
    Call b =
        Call.fromLogs(
            List.of(
                log(
                    3,
                    "B",
                    "2025-01-03T11:00:00Z",
                    Optional.of(recording("b.wav")),
                    Optional.empty(),
                    false)));
    result =
        new SearchResult(
            CONTACT,
            new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 5)),
            List.of(b, a));
    gateway.transcriptions.put(
        "A",
        new Transcription(
            "A",
            List.of(new Speech(Speaker.CALLER, 0, "Hello"), new Speech(Speaker.CALLEE, 3.4, "Hi")),
            "Hello Hi",
            "{\"original\":true}"));
  }

  @Test
  void savesEverythingInTheContactFolder() throws IOException {
    DownloadReport report = download(DownloadOption.RECORDINGS_AND_TRANSCRIPTIONS);

    Path folder = base.resolve("34666666666");
    assertThat(report.folder()).isEqualTo(folder);
    assertThat(report.downloaded()).isEqualTo(4);
    assertThat(report.failures()).isEmpty();
    assertThat(files(folder))
        .containsExactlyInAnyOrder(
            CALL_A + ".mp3",
            CALL_A + "_voicemail.mp3",
            CALL_A + "_transcription.txt",
            "OriginalTranscription_Json/" + CALL_A + "_transcription.json",
            CALL_B + ".wav",
            "OriginalTranscription_Json/manifest.csv");
    assertThat(Files.readString(folder.resolve(CALL_A + "_transcription.txt")))
        .isEqualTo(
            "[00:00] Caller: Hello"
                + System.lineSeparator()
                + "[00:03] Callee: Hi"
                + System.lineSeparator());
    assertThat(
            Files.readString(
                folder.resolve("OriginalTranscription_Json/" + CALL_A + "_transcription.json")))
        .isEqualTo("{\"original\":true}");
  }

  @Test
  void recordingsOnlyIncludesVoicemailsButNoTranscriptions() throws IOException {
    DownloadReport report = download(DownloadOption.RECORDINGS_ONLY);

    assertThat(report.downloaded()).isEqualTo(3);
    assertThat(gateway.fetchedTranscriptions).isEmpty();
    assertThat(files(base.resolve("34666666666")))
        .containsExactlyInAnyOrder(
            CALL_A + ".mp3",
            CALL_A + "_voicemail.mp3",
            CALL_B + ".wav",
            "OriginalTranscription_Json/manifest.csv");
  }

  @Test
  void transcriptionsOnlyDownloadsNoAudio() throws IOException {
    DownloadReport report = download(DownloadOption.TRANSCRIPTIONS_ONLY);

    assertThat(report.downloaded()).isEqualTo(1);
    assertThat(gateway.downloadedMedia).isEmpty();
  }

  @Test
  void secondRunSkipsExistingFiles() throws IOException {
    download(DownloadOption.RECORDINGS_AND_TRANSCRIPTIONS);
    gateway.downloadedMedia.clear();
    gateway.fetchedTranscriptions.clear();

    DownloadReport second = download(DownloadOption.RECORDINGS_AND_TRANSCRIPTIONS);

    assertThat(second.downloaded()).isZero();
    assertThat(second.skipped()).isEqualTo(4);
    assertThat(gateway.downloadedMedia).isEmpty();
    assertThat(gateway.fetchedTranscriptions).isEmpty();
  }

  @Test
  void failedFileIsReportedAndOthersContinue() throws IOException {
    gateway.mediaFailures.put(
        recording("b.wav").url(), new GatewayException(Reason.SERVER_ERROR, "boom"));

    DownloadReport report = download(DownloadOption.RECORDINGS_ONLY);

    assertThat(report.downloaded()).isEqualTo(2);
    assertThat(report.failures())
        .singleElement()
        .satisfies(
            failure -> {
              assertThat(failure.callId()).isEqualTo("B");
              assertThat(failure.cause()).isEqualTo(DownloadReport.Cause.API_ERROR);
            });
    assertThat(base.resolve("34666666666").resolve(CALL_B + ".wav")).doesNotExist();
    assertThat(base.resolve("34666666666").resolve(CALL_B + ".wav.part")).doesNotExist();
  }

  @Test
  void rejectedApiKeyAbortsTheDownload() {
    gateway.mediaFailures.put(
        recording("b.wav").url(), new GatewayException(Reason.UNAUTHORIZED, "bad key"));

    assertThatExceptionOfType(GatewayException.class)
        .isThrownBy(() -> download(DownloadOption.RECORDINGS_ONLY));
  }

  @Test
  void transcriptionsNotInPlanAreReportedOnce() throws IOException {
    gateway.transcriptionFailure = new GatewayException(Reason.FEATURE_UNAVAILABLE, "not in plan");

    DownloadReport report = download(DownloadOption.RECORDINGS_AND_TRANSCRIPTIONS);

    assertThat(report.transcriptionsUnavailable()).isTrue();
    assertThat(report.failures()).isEmpty();
    assertThat(report.downloaded()).isEqualTo(3);
  }

  @Test
  void manifestListsTheFilesOfEachCall() throws IOException {
    download(DownloadOption.RECORDINGS_AND_TRANSCRIPTIONS);

    List<Manifest.Entry> entries =
        Manifest.read(base.resolve("34666666666/OriginalTranscription_Json/manifest.csv"));

    assertThat(entries).extracting(Manifest.Entry::callId).containsExactly("B", "A");
    Manifest.Entry a = entries.get(1);
    assertThat(a.recordingFiles()).containsExactly(CALL_A + ".mp3");
    assertThat(a.voicemailFiles()).containsExactly(CALL_A + "_voicemail.mp3");
    assertThat(a.transcriptionFiles()).hasSize(2);
  }

  @Test
  void cancellingStillWritesTheManifestOfFinishedCalls() throws IOException {
    CancellationToken token = new CancellationToken();
    ProgressListener cancelAfterFirstFile =
        (done, total) -> {
          if (done == 1) {
            token.cancel();
          }
        };

    assertThatExceptionOfType(java.util.concurrent.CancellationException.class)
        .isThrownBy(
            () ->
                useCase.download(
                    CONNECTION,
                    result,
                    DownloadOption.RECORDINGS_ONLY,
                    base,
                    ZoneOffset.UTC,
                    formatter,
                    cancelAfterFirstFile,
                    token));
    assertThat(Manifest.read(base.resolve("34666666666/OriginalTranscription_Json/manifest.csv")))
        .extracting(Manifest.Entry::callId)
        .containsExactly("B");
  }

  @Test
  void stepCountMatchesTheOption() {
    assertThat(DownloadFilesUseCase.stepCount(result, DownloadOption.RECORDINGS_AND_TRANSCRIPTIONS))
        .isEqualTo(4);
    assertThat(DownloadFilesUseCase.stepCount(result, DownloadOption.RECORDINGS_ONLY)).isEqualTo(3);
    assertThat(DownloadFilesUseCase.stepCount(result, DownloadOption.TRANSCRIPTIONS_ONLY))
        .isEqualTo(1);
  }

  private DownloadReport download(DownloadOption option) throws IOException {
    return useCase.download(
        CONNECTION,
        result,
        option,
        base,
        ZoneOffset.UTC,
        formatter,
        ProgressListener.none(),
        CancellationToken.none());
  }

  private static List<String> files(Path folder) throws IOException {
    try (Stream<Path> paths = Files.walk(folder)) {
      return paths
          .filter(Files::isRegularFile)
          .map(p -> folder.relativize(p).toString().replace('\\', '/'))
          .toList();
    }
  }
}
