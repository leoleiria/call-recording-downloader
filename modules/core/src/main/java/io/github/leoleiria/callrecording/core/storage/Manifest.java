package io.github.leoleiria.callrecording.core.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The {@code manifest.csv} index of a contact folder: one row per call with the files saved.
 *
 * <p>Running the app again for another period merges new rows into the existing file instead of
 * replacing it.
 */
public final class Manifest {

  static final List<String> HEADER =
      List.of(
          "call_id",
          "start_time",
          "direction",
          "duration_seconds",
          "recording_files",
          "voicemail_files",
          "transcription_files");

  private static final String LIST_SEPARATOR = "|";

  /**
   * One call.
   *
   * @param callId call id
   * @param startTime ISO-8601 start time
   * @param direction {@code in}, {@code out} or {@code unknown}
   * @param durationSeconds call duration
   * @param recordingFiles recording file names
   * @param voicemailFiles voicemail file names
   * @param transcriptionFiles transcription file names (text and JSON)
   */
  public record Entry(
      String callId,
      String startTime,
      String direction,
      long durationSeconds,
      Set<String> recordingFiles,
      Set<String> voicemailFiles,
      Set<String> transcriptionFiles) {

    public Entry {
      Objects.requireNonNull(callId, "callId");
      recordingFiles = Set.copyOf(new LinkedHashSet<>(recordingFiles));
      voicemailFiles = Set.copyOf(new LinkedHashSet<>(voicemailFiles));
      transcriptionFiles = Set.copyOf(new LinkedHashSet<>(transcriptionFiles));
    }

    Entry mergedWith(Entry newer) {
      return new Entry(
          callId,
          newer.startTime,
          newer.direction,
          newer.durationSeconds,
          union(recordingFiles, newer.recordingFiles),
          union(voicemailFiles, newer.voicemailFiles),
          union(transcriptionFiles, newer.transcriptionFiles));
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
      Set<String> all = new LinkedHashSet<>(a);
      all.addAll(b);
      return all;
    }
  }

  private Manifest() {}

  /** Merges {@code entries} into the manifest at {@code file}, creating it if needed. */
  public static void merge(Path file, List<Entry> entries) throws IOException {
    Map<String, Entry> byCallId = new LinkedHashMap<>();
    for (Entry existing : read(file)) {
      byCallId.put(existing.callId(), existing);
    }
    for (Entry entry : entries) {
      byCallId.merge(entry.callId(), entry, Entry::mergedWith);
    }
    List<Entry> sorted = new ArrayList<>(byCallId.values());
    sorted.sort(Comparator.comparing(Entry::startTime).reversed());
    AtomicFileWriter.writeString(file, toCsv(sorted));
  }

  /** Reads a manifest; returns an empty list if the file does not exist. */
  public static List<Entry> read(Path file) throws IOException {
    if (!Files.exists(file)) {
      return List.of();
    }
    List<Entry> entries = new ArrayList<>();
    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    for (int i = 1; i < lines.size(); i++) {
      if (lines.get(i).isBlank()) {
        continue;
      }
      List<String> cells = Csv.parseLine(lines.get(i));
      if (cells.size() < HEADER.size()) {
        continue;
      }
      entries.add(
          new Entry(
              cells.get(0),
              cells.get(1),
              cells.get(2),
              parseLong(cells.get(3)),
              splitList(cells.get(4)),
              splitList(cells.get(5)),
              splitList(cells.get(6))));
    }
    return entries;
  }

  private static String toCsv(List<Entry> entries) {
    StringBuilder out = new StringBuilder(Csv.formatLine(HEADER)).append('\n');
    for (Entry e : entries) {
      out.append(
              Csv.formatLine(
                  List.of(
                      e.callId(),
                      e.startTime(),
                      e.direction(),
                      Long.toString(e.durationSeconds()),
                      joinList(e.recordingFiles()),
                      joinList(e.voicemailFiles()),
                      joinList(e.transcriptionFiles()))))
          .append('\n');
    }
    return out.toString();
  }

  private static String joinList(Set<String> values) {
    return String.join(LIST_SEPARATOR, values.stream().sorted().toList());
  }

  private static Set<String> splitList(String value) {
    Set<String> result = new LinkedHashSet<>();
    for (String part : value.split("\\|")) {
      if (!part.isBlank()) {
        result.add(part);
      }
    }
    return result;
  }

  private static long parseLong(String value) {
    try {
      return Long.parseLong(value.strip());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /** Minimal RFC 4180 CSV for single-line cells. */
  static final class Csv {

    private Csv() {}

    static String formatLine(List<String> cells) {
      StringBuilder line = new StringBuilder();
      for (int i = 0; i < cells.size(); i++) {
        if (i > 0) {
          line.append(',');
        }
        line.append(escape(cells.get(i)));
      }
      return line.toString();
    }

    static List<String> parseLine(String line) {
      List<String> cells = new ArrayList<>();
      StringBuilder cell = new StringBuilder();
      boolean quoted = false;
      for (int i = 0; i < line.length(); i++) {
        char c = line.charAt(i);
        if (quoted) {
          if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
            cell.append('"');
            i++;
          } else if (c == '"') {
            quoted = false;
          } else {
            cell.append(c);
          }
        } else if (c == '"') {
          quoted = true;
        } else if (c == ',') {
          cells.add(unneutralize(cell.toString()));
          cell.setLength(0);
        } else {
          cell.append(c);
        }
      }
      cells.add(unneutralize(cell.toString()));
      return cells;
    }

    /**
     * Spreadsheet programs run cells starting with {@code = + - @}, tab or carriage return as
     * formulas. Values come partly from the API (the call id), so such cells get a leading {@code
     * '}, the usual marker for "text, not a formula" (OWASP CSV injection guidance).
     */
    static String neutralizeFormula(String value) {
      if (!value.isEmpty() && FORMULA_START.indexOf(value.charAt(0)) >= 0) {
        return "'" + value;
      }
      return value;
    }

    /** Reverses {@link #neutralizeFormula} when reading the manifest back. */
    private static String unneutralize(String value) {
      if (value.length() > 1
          && value.charAt(0) == '\''
          && FORMULA_START.indexOf(value.charAt(1)) >= 0) {
        return value.substring(1);
      }
      return value;
    }

    private static final String FORMULA_START = "=+-@\t\r";

    private static String escape(String value) {
      String safe =
          neutralizeFormula(value == null ? "" : value).replace("\r", " ").replace("\n", " ");
      if (safe.contains(",") || safe.contains("\"")) {
        return '"' + safe.replace("\"", "\"\"") + '"';
      }
      return safe;
    }
  }
}
