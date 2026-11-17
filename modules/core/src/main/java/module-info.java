/**
 * Domain model, use cases and file storage of Call Recording Downloader.
 *
 * <p>This module knows nothing about HTTP or the user interface. The Ringover API is reached
 * through the {@link io.github.leoleiria.callrecording.core.port.CallGateway} port.
 */
module io.github.leoleiria.callrecording.core {
  requires org.slf4j;

  exports io.github.leoleiria.callrecording.core.domain;
  exports io.github.leoleiria.callrecording.core.port;
  exports io.github.leoleiria.callrecording.core.security;
  exports io.github.leoleiria.callrecording.core.storage;
  exports io.github.leoleiria.callrecording.core.transcript;
  exports io.github.leoleiria.callrecording.core.usecase;
}
