package io.github.leoleiria.callrecording.app.ui;

import io.github.leoleiria.callrecording.app.AppInfo;
import io.github.leoleiria.callrecording.app.i18n.Messages;
import io.github.leoleiria.callrecording.core.port.GatewayException;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Turns exceptions into messages a user can act on. */
final class ErrorMessages {

  private ErrorMessages() {}

  static String describe(Throwable error, Messages messages) {
    if (error instanceof GatewayException gateway) {
      return messages.get("error." + gateway.reason().name());
    }
    if (error instanceof IOException || error instanceof UncheckedIOException) {
      return messages.get("error.fileSystem");
    }
    return messages.get("error.unexpected", AppInfo.logFolder());
  }
}
