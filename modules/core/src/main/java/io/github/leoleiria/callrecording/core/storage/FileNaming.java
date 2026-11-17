package io.github.leoleiria.callrecording.core.storage;

import io.github.leoleiria.callrecording.core.domain.Call;
import io.github.leoleiria.callrecording.core.domain.MediaKind;
import io.github.leoleiria.callrecording.core.domain.MediaRef;
import io.github.leoleiria.callrecording.core.domain.PhoneNumber;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Decides where each file is saved.
 *
 * <pre>
 * &lt;base folder&gt;/34666666666/
 *   2025-01-15_14-32-05_in_&lt;callId&gt;.mp3
 *   2025-01-16_09-10-44_in_&lt;callId&gt;_voicemail.mp3
 *   2025-01-15_14-32-05_in_&lt;callId&gt;_transcription.txt
 *   OriginalTranscription_Json/2025-01-15_14-32-05_in_&lt;callId&gt;_transcription.json
 *   OriginalTranscription_Json/manifest.csv
 * </pre>
 *
 * <p>The manifest sits with the JSON files so the contact folder only holds what users open
 * directly. File names inside it are relative to the contact folder.
 *
 * <p>Names start with the local date and time so files sort chronologically, and end with the call
 * id so they are unique.
 */
public final class FileNaming {

  /** Sub-folder holding the transcriptions exactly as returned by the API. */
  public static final String ORIGINAL_JSON_FOLDER = "OriginalTranscription_Json";

  /** Index of every file downloaded for the contact, relative to the contact folder. */
  public static final String MANIFEST_FILE = ORIGINAL_JSON_FOLDER + "/manifest.csv";

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
  private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9_-]");

  private final ZoneId zone;

  /**
   * @param zone time zone used for the date and time in file names
   */
  public FileNaming(ZoneId zone) {
    this.zone = Objects.requireNonNull(zone, "zone");
  }

  /** Folder of a contact inside the folder chosen by the user: the number without {@code +}. */
  public Path contactFolder(Path baseFolder, PhoneNumber contact) {
    return baseFolder.resolve(contact.digits());
  }

  /** Common prefix of every file of a call. */
  public String baseName(Call call) {
    return TIMESTAMP.format(call.startTime().atZoneSameInstant(zone))
        + "_"
        + call.direction().code()
        + "_"
        + UNSAFE.matcher(call.callId()).replaceAll("_");
  }

  /**
   * File name of an audio file.
   *
   * @param indexOfKind zero-based position among the call's files of the same kind; a suffix {@code
   *     _2}, {@code _3}... is added from the second file on
   */
  public String mediaFileName(Call call, MediaRef media, int indexOfKind) {
    StringBuilder name = new StringBuilder(baseName(call));
    if (media.kind() == MediaKind.VOICEMAIL) {
      name.append("_voicemail");
    }
    if (indexOfKind > 0) {
      name.append('_').append(indexOfKind + 1);
    }
    return name.append('.').append(media.fileExtension()).toString();
  }

  /** Readable transcription, inside the contact folder. */
  public String transcriptTextFileName(Call call) {
    return baseName(call) + "_transcription.txt";
  }

  /** Original transcription JSON, relative to the contact folder. */
  public String transcriptJsonFileName(Call call) {
    return ORIGINAL_JSON_FOLDER + "/" + baseName(call) + "_transcription.json";
  }
}
