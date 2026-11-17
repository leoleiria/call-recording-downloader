package io.github.leoleiria.callrecording.core.port;

import java.util.Objects;

/** A failure talking to the provider, classified so the UI can show a helpful message. */
public class GatewayException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Why the request failed. */
  public enum Reason {
    /** API key invalid or missing the required permission. */
    UNAUTHORIZED,
    /** Request refused for this account or resource. */
    FORBIDDEN,
    /** The feature is not part of the account's plan (for example transcriptions). */
    FEATURE_UNAVAILABLE,
    /** Too many requests, even after retrying. */
    RATE_LIMITED,
    /** The request was rejected as invalid. */
    BAD_REQUEST,
    /** The provider failed. */
    SERVER_ERROR,
    /** No connection, timeout or interrupted transfer. */
    NETWORK,
    /** The response could not be understood. */
    UNEXPECTED_RESPONSE
  }

  private final Reason reason;

  public GatewayException(Reason reason, String message) {
    this(reason, message, null);
  }

  public GatewayException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = Objects.requireNonNull(reason, "reason");
  }

  public Reason reason() {
    return reason;
  }
}
