package io.github.leoleiria.callrecording.core.domain;

/** What the user chose to download. Voicemails always count as recordings. */
public enum DownloadOption {
  RECORDINGS_AND_TRANSCRIPTIONS(true, true),
  RECORDINGS_ONLY(true, false),
  TRANSCRIPTIONS_ONLY(false, true);

  private final boolean recordings;
  private final boolean transcriptions;

  DownloadOption(boolean recordings, boolean transcriptions) {
    this.recordings = recordings;
    this.transcriptions = transcriptions;
  }

  public boolean includesRecordings() {
    return recordings;
  }

  public boolean includesTranscriptions() {
    return transcriptions;
  }
}
