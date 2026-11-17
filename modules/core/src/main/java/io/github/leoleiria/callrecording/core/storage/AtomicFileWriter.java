package io.github.leoleiria.callrecording.core.storage;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes a file so that it either appears complete or not at all.
 *
 * <p>Content goes to {@code <name>.part} first and is renamed when finished. An interrupted
 * download therefore never leaves a truncated file that a later run would wrongly skip.
 */
public final class AtomicFileWriter {

  /** Suffix of files still being written. */
  public static final String PART_SUFFIX = ".part";

  /** Produces the file content. */
  @FunctionalInterface
  public interface ContentWriter {
    void writeTo(OutputStream out) throws IOException;
  }

  private AtomicFileWriter() {}

  /** Writes {@code target} atomically, creating parent folders as needed. */
  public static void write(Path target, ContentWriter content) throws IOException {
    Path parent = target.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    Path part = parent.resolve(target.getFileName() + PART_SUFFIX);
    try {
      try (OutputStream out = Files.newOutputStream(part)) {
        content.writeTo(out);
      }
      moveIntoPlace(part, target);
    } catch (IOException | RuntimeException e) {
      Files.deleteIfExists(part);
      throw e;
    }
  }

  /** Writes text as UTF-8, atomically. */
  public static void writeString(Path target, String text) throws IOException {
    write(target, out -> out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  private static void moveIntoPlace(Path part, Path target) throws IOException {
    try {
      Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
