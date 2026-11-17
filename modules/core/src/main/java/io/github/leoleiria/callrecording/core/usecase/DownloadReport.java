package io.github.leoleiria.callrecording.core.usecase;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Outcome of a download.
 *
 * @param folder the contact folder files were saved to
 * @param downloaded files saved in this run
 * @param skipped files that already existed and were left untouched
 * @param failures files that could not be saved
 * @param transcriptionsUnavailable {@code true} if the account's plan does not include
 *     transcriptions, in which case no transcription was attempted after the first refusal
 */
public record DownloadReport(
    Path folder,
    int downloaded,
    int skipped,
    List<Failure> failures,
    boolean transcriptionsUnavailable) {

  public DownloadReport {
    Objects.requireNonNull(folder, "folder");
    failures = List.copyOf(failures);
  }

  /** Why a file was not saved. */
  public enum Cause {
    /** The API refused or failed. */
    API_ERROR,
    /** The file could not be written to disk. */
    FILE_SYSTEM,
    /** The API reported the transcription but did not return it. */
    NOT_AVAILABLE
  }

  /**
   * A file that could not be saved.
   *
   * @param callId the call
   * @param fileName the file that was not saved
   * @param cause category of the problem
   * @param detail technical detail for the log, never shown with secrets
   */
  public record Failure(String callId, String fileName, Cause cause, String detail) {}
}
