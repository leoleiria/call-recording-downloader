package io.github.leoleiria.callrecording.app.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.github.leoleiria.callrecording.core.security.SecretRedactor;

/**
 * Logback {@code %msg} replacement that masks API keys and {@code Authorization} values.
 *
 * <p>Registered in {@code logback.xml}; this is the last line of defence after {@link
 * io.github.leoleiria.callrecording.core.security.ApiKey#toString()}.
 */
public final class RedactingMessageConverter extends MessageConverter {

  /** Created by Logback. */
  public RedactingMessageConverter() {}

  @Override
  public String convert(ILoggingEvent event) {
    return SecretRedactor.redact(super.convert(event));
  }
}
