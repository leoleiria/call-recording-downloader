package io.github.leoleiria.callrecording.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestTest {

  @TempDir Path folder;

  @Test
  void mergesNewRowsWithExistingOnes() throws IOException {
    Path file = folder.resolve("manifest.csv");
    Manifest.merge(file, List.of(entry("A", "2025-01-01T10:00:00Z", Set.of("a.mp3"), Set.of())));

    Manifest.merge(
        file,
        List.of(
            entry("A", "2025-01-01T10:00:00Z", Set.of(), Set.of("a.txt")),
            entry("B", "2025-01-05T10:00:00Z", Set.of("b.mp3"), Set.of())));

    List<Manifest.Entry> entries = Manifest.read(file);
    assertThat(entries).extracting(Manifest.Entry::callId).containsExactly("B", "A");
    assertThat(entries.get(1).recordingFiles()).containsExactly("a.mp3");
    assertThat(entries.get(1).transcriptionFiles()).containsExactly("a.txt");
  }

  @Test
  void escapesCommasAndQuotes() throws IOException {
    Path file = folder.resolve("manifest.csv");
    Manifest.merge(
        file, List.of(entry("id,with \"quotes\"", "2025-01-01T10:00:00Z", Set.of(), Set.of())));

    assertThat(Files.readAllLines(file).get(1)).startsWith("\"id,with \"\"quotes\"\"\",");
    assertThat(Manifest.read(file).get(0).callId()).isEqualTo("id,with \"quotes\"");
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"=HYPERLINK(\"http://evil\",\"x\")", "+1+1", "-2+3", "@SUM(A1)", "\tcmd"})
  void formulaLikeCallIdsAreWrittenAsTextAndReadBackUnchanged(String callId) throws IOException {
    Path file = folder.resolve("manifest.csv");
    Manifest.merge(file, List.of(entry(callId, "2025-01-01T10:00:00Z", Set.of(), Set.of())));

    String cell = Manifest.Csv.parseLine(Files.readAllLines(file).get(1)).get(0);
    String rawLine = Files.readAllLines(file).get(1);
    assertThat(rawLine.startsWith("'") || rawLine.startsWith("\"'"))
        .as("raw line %s", rawLine)
        .isTrue();
    assertThat(cell).isEqualTo(callId);
    assertThat(Manifest.read(file).get(0).callId()).isEqualTo(callId);
  }

  @Test
  void ordinaryValuesAreNotPrefixed() {
    assertThat(Manifest.Csv.neutralizeFormula("2025-01-01T10:00:00Z"))
        .isEqualTo("2025-01-01T10:00:00Z");
    assertThat(Manifest.Csv.neutralizeFormula("abc-123")).isEqualTo("abc-123");
    assertThat(Manifest.Csv.neutralizeFormula("")).isEmpty();
  }

  @Test
  void missingFileReadsAsEmpty() throws IOException {
    assertThat(Manifest.read(folder.resolve("none.csv"))).isEmpty();
  }

  private static Manifest.Entry entry(
      String id, String start, Set<String> recordings, Set<String> transcriptions) {
    return new Manifest.Entry(id, start, "in", 42, recordings, Set.of(), transcriptions);
  }
}
