package io.github.leoleiria.callrecording.core.domain;

import java.util.List;
import java.util.Objects;

/**
 * Calls found for a contact in a date range, most recent first.
 *
 * @param contact the contact searched for
 * @param range the date range searched
 * @param calls the calls found
 */
public record SearchResult(PhoneNumber contact, DateRange range, List<Call> calls) {

  public SearchResult {
    Objects.requireNonNull(contact, "contact");
    Objects.requireNonNull(range, "range");
    calls = List.copyOf(calls);
  }

  /** Number of distinct calls. */
  public int callCount() {
    return calls.size();
  }

  /** Number of audio files available: recordings plus voicemails. */
  public long audioCount() {
    return calls.stream().mapToLong(c -> c.media().size()).sum();
  }

  /** Number of voicemail files, a subset of {@link #audioCount()}. */
  public long voicemailCount() {
    return calls.stream().mapToLong(c -> c.mediaCount(MediaKind.VOICEMAIL)).sum();
  }

  /** Number of calls with a transcription. */
  public long transcriptionCount() {
    return calls.stream().filter(Call::transcriptionAvailable).count();
  }
}
