package io.github.leoleiria.callrecording.core.port;

/** Receives progress of a long operation. Called from a background thread. */
@FunctionalInterface
public interface ProgressListener {

  /**
   * @param done steps finished so far
   * @param total total number of steps, or {@code -1} when unknown
   */
  void onProgress(long done, long total);

  /** A listener that ignores everything. */
  static ProgressListener none() {
    return (done, total) -> {};
  }
}
