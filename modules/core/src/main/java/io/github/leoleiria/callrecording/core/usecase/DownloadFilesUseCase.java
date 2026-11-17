package io.github.leoleiria.callrecording.core.usecase;

import io.github.leoleiria.callrecording.core.domain.Call;
import io.github.leoleiria.callrecording.core.domain.Connection;
import io.github.leoleiria.callrecording.core.domain.DownloadOption;
import io.github.leoleiria.callrecording.core.domain.MediaKind;
import io.github.leoleiria.callrecording.core.domain.MediaRef;
import io.github.leoleiria.callrecording.core.domain.SearchResult;
import io.github.leoleiria.callrecording.core.port.CallGateway;
import io.github.leoleiria.callrecording.core.port.CancellationToken;
import io.github.leoleiria.callrecording.core.port.GatewayException;
import io.github.leoleiria.callrecording.core.port.ProgressListener;
import io.github.leoleiria.callrecording.core.storage.AtomicFileWriter;
import io.github.leoleiria.callrecording.core.storage.FileNaming;
import io.github.leoleiria.callrecording.core.storage.Manifest;
import io.github.leoleiria.callrecording.core.transcript.TranscriptTextFormatter;
import io.github.leoleiria.callrecording.core.transcript.Transcription;
import io.github.leoleiria.callrecording.core.usecase.DownloadReport.Cause;
import io.github.leoleiria.callrecording.core.usecase.DownloadReport.Failure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saves the recordings, voicemails and transcriptions of a {@link SearchResult} to disk.
 *
 * <p>The operation is idempotent: files that already exist are skipped, so running it again only
 * fetches what is missing. Every file is written atomically and the manifest ({@link
 * FileNaming#MANIFEST_FILE}) is updated at the end, even if the user cancels halfway.
 */
public final class DownloadFilesUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(DownloadFilesUseCase.class);

  private final CallGateway gateway;

  public DownloadFilesUseCase(CallGateway gateway) {
    this.gateway = Objects.requireNonNull(gateway, "gateway");
  }

  /** Number of steps {@link #download} will report for this result and option. */
  public static long stepCount(SearchResult result, DownloadOption option) {
    long steps = 0;
    for (Call call : result.calls()) {
      if (option.includesRecordings()) {
        steps += call.media().size();
      }
      if (option.includesTranscriptions() && call.transcriptionAvailable()) {
        steps++;
      }
    }
    return steps;
  }

  /**
   * Downloads the selected files.
   *
   * @param baseFolder the folder chosen by the user; a sub-folder per contact is created inside
   * @param zone time zone used in file names
   * @param formatter turns transcriptions into readable text
   * @throws GatewayException if the API key is rejected (nothing else could succeed)
   * @throws java.util.concurrent.CancellationException if cancelled
   * @throws IOException if the contact folder or the manifest cannot be written
   */
  public DownloadReport download(
      Connection connection,
      SearchResult result,
      DownloadOption option,
      Path baseFolder,
      ZoneId zone,
      TranscriptTextFormatter formatter,
      ProgressListener progress,
      CancellationToken cancellation)
      throws IOException {
    FileNaming naming = new FileNaming(zone);
    Path folder = naming.contactFolder(baseFolder, result.contact());
    Files.createDirectories(folder);
    LOG.info("Downloading {} to {}", option, folder);

    Run run = new Run(connection, folder, naming, formatter, stepCount(result, option), progress);
    List<Manifest.Entry> manifest = new ArrayList<>();
    try {
      for (Call call : result.calls()) {
        cancellation.throwIfCancelled();
        CallFiles files = new CallFiles();
        if (option.includesRecordings()) {
          run.saveMedia(call, files, cancellation);
        }
        if (option.includesTranscriptions() && call.transcriptionAvailable()) {
          cancellation.throwIfCancelled();
          run.saveTranscription(call, files);
        }
        if (!files.isEmpty()) {
          manifest.add(files.toManifestEntry(call));
        }
      }
    } finally {
      if (!manifest.isEmpty()) {
        Manifest.merge(folder.resolve(FileNaming.MANIFEST_FILE), manifest);
      }
    }

    DownloadReport report =
        new DownloadReport(
            folder, run.downloaded, run.skipped, run.failures, run.transcriptionsUnavailable);
    LOG.info(
        "Download finished: {} saved, {} skipped, {} failed",
        report.downloaded(),
        report.skipped(),
        report.failures().size());
    return report;
  }

  /** Mutable state of one download run. */
  private final class Run {

    private final Connection connection;
    private final Path folder;
    private final FileNaming naming;
    private final TranscriptTextFormatter formatter;
    private final long totalSteps;
    private final ProgressListener progress;
    private final List<Failure> failures = new ArrayList<>();
    private long doneSteps;
    private int downloaded;
    private int skipped;
    private boolean transcriptionsUnavailable;

    Run(
        Connection connection,
        Path folder,
        FileNaming naming,
        TranscriptTextFormatter formatter,
        long totalSteps,
        ProgressListener progress) {
      this.connection = connection;
      this.folder = folder;
      this.naming = naming;
      this.formatter = formatter;
      this.totalSteps = totalSteps;
      this.progress = progress;
      progress.onProgress(0, totalSteps);
    }

    void saveMedia(Call call, CallFiles files, CancellationToken cancellation) {
      Map<MediaKind, Integer> indexByKind = new EnumMap<>(MediaKind.class);
      for (MediaRef media : call.media()) {
        cancellation.throwIfCancelled();
        int index = indexByKind.merge(media.kind(), 1, Integer::sum) - 1;
        String fileName = naming.mediaFileName(call, media, index);
        Path target = folder.resolve(fileName);
        try {
          if (isComplete(target)) {
            skipped++;
          } else {
            AtomicFileWriter.write(target, out -> gateway.downloadMedia(connection, media, out));
            downloaded++;
          }
          files.add(media.kind(), fileName);
        } catch (GatewayException e) {
          failOrAbort(call, fileName, e);
        } catch (IOException e) {
          fail(call, fileName, Cause.FILE_SYSTEM, e.toString());
        }
        step();
      }
    }

    void saveTranscription(Call call, CallFiles files) {
      String textName = naming.transcriptTextFileName(call);
      String jsonName = naming.transcriptJsonFileName(call);
      Path textTarget = folder.resolve(textName);
      Path jsonTarget = folder.resolve(jsonName);
      try {
        if (transcriptionsUnavailable) {
          return;
        }
        if (isComplete(textTarget) && isComplete(jsonTarget)) {
          skipped++;
          files.transcriptions.add(textName);
          files.transcriptions.add(jsonName);
          return;
        }
        Optional<Transcription> transcription =
            gateway.fetchTranscription(connection, call.callId());
        if (transcription.isEmpty()) {
          fail(call, textName, Cause.NOT_AVAILABLE, "No transcription returned");
          return;
        }
        AtomicFileWriter.writeString(jsonTarget, transcription.get().originalJson());
        AtomicFileWriter.writeString(textTarget, formatter.format(transcription.get()));
        downloaded++;
        files.transcriptions.add(textName);
        files.transcriptions.add(jsonName);
      } catch (GatewayException e) {
        if (e.reason() == GatewayException.Reason.FEATURE_UNAVAILABLE) {
          LOG.warn("Transcriptions are not available for this account; skipping them");
          transcriptionsUnavailable = true;
        } else {
          failOrAbort(call, textName, e);
        }
      } catch (IOException e) {
        fail(call, textName, Cause.FILE_SYSTEM, e.toString());
      } finally {
        step();
      }
    }

    private void failOrAbort(Call call, String fileName, GatewayException e) {
      if (e.reason() == GatewayException.Reason.UNAUTHORIZED) {
        throw e;
      }
      fail(call, fileName, Cause.API_ERROR, e.reason() + ": " + e.getMessage());
    }

    private void fail(Call call, String fileName, Cause cause, String detail) {
      LOG.warn("Could not save {} (call {}): {}", fileName, call.callId(), detail);
      failures.add(new Failure(call.callId(), fileName, cause, detail));
    }

    private void step() {
      doneSteps++;
      progress.onProgress(doneSteps, totalSteps);
    }

    private static boolean isComplete(Path file) throws IOException {
      return Files.isRegularFile(file) && Files.size(file) > 0;
    }
  }

  /** Files saved (or already present) for one call. */
  private static final class CallFiles {
    private final Set<String> recordings = new LinkedHashSet<>();
    private final Set<String> voicemails = new LinkedHashSet<>();
    private final Set<String> transcriptions = new LinkedHashSet<>();

    void add(MediaKind kind, String fileName) {
      (kind == MediaKind.VOICEMAIL ? voicemails : recordings).add(fileName);
    }

    boolean isEmpty() {
      return recordings.isEmpty() && voicemails.isEmpty() && transcriptions.isEmpty();
    }

    Manifest.Entry toManifestEntry(Call call) {
      return new Manifest.Entry(
          call.callId(),
          call.startTime().toInstant().toString(),
          call.direction().code(),
          call.durationSeconds(),
          recordings,
          voicemails,
          transcriptions);
    }
  }
}
