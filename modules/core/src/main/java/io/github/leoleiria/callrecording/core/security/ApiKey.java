package io.github.leoleiria.callrecording.core.security;

import java.util.Objects;

/**
 * A Ringover API key held in memory only.
 *
 * <p>The key is never persisted. {@link #toString()} always returns a masked form, so the secret
 * cannot leak through string concatenation, logging or exception messages. The raw value is only
 * available through {@link #reveal()}, which must be called solely where the HTTP {@code
 * Authorization} header is built.
 *
 * <p>Every key created is registered with {@link SecretRedactor} so that the logging layer can
 * scrub it even if it reaches a log message by some other route.
 */
public final class ApiKey {

  private static final int VISIBLE_SUFFIX = 4;
  private static final String MASK = "****";

  private final String value;

  private ApiKey(String value) {
    this.value = value;
  }

  /**
   * Creates an API key from user input.
   *
   * @param raw the key as typed or pasted; surrounding whitespace is removed
   * @throws IllegalArgumentException if the key is blank or contains whitespace
   */
  public static ApiKey of(String raw) {
    Objects.requireNonNull(raw, "raw");
    String trimmed = raw.strip();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("API key must not be blank");
    }
    if (trimmed.chars().anyMatch(Character::isWhitespace)) {
      throw new IllegalArgumentException("API key must not contain whitespace");
    }
    SecretRedactor.register(trimmed);
    return new ApiKey(trimmed);
  }

  /** Returns the raw secret. Only the HTTP client may call this. */
  public String reveal() {
    return value;
  }

  /** Returns the masked form, for example {@code ****a1b2}. */
  public String masked() {
    return mask(value);
  }

  /** Masks any secret, keeping at most its last four characters visible. */
  static String mask(String secret) {
    if (secret.length() <= VISIBLE_SUFFIX * 2) {
      return MASK;
    }
    return MASK + secret.substring(secret.length() - VISIBLE_SUFFIX);
  }

  @Override
  public String toString() {
    return masked();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ApiKey key && key.value.equals(value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }
}
