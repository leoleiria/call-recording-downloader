package io.github.leoleiria.callrecording.core.port;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Lets the user stop a long search or download between steps. Thread-safe. */
public final class CancellationToken {

  private final AtomicBoolean cancelled = new AtomicBoolean();

  /** Creates a token that is not cancelled yet. */
  public CancellationToken() {}

  /** A token that is never cancelled. */
  public static CancellationToken none() {
    return new CancellationToken();
  }

  public void cancel() {
    cancelled.set(true);
  }

  public boolean isCancelled() {
    return cancelled.get();
  }

  /** Throws {@link CancellationException} if cancellation was requested. */
  public void throwIfCancelled() {
    if (cancelled.get()) {
      throw new CancellationException("Cancelled by the user");
    }
  }
}
