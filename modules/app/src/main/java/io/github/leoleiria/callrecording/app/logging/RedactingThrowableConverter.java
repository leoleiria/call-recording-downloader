package io.github.leoleiria.callrecording.app.logging;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.github.leoleiria.callrecording.core.security.SecretRedactor;

/** Logback {@code %ex} replacement that masks secrets inside stack traces. */
public final class RedactingThrowableConverter extends ThrowableProxyConverter {

  /** Created by Logback. */
  public RedactingThrowableConverter() {}

  @Override
  public String convert(ILoggingEvent event) {
    return SecretRedactor.redact(super.convert(event));
  }
}
