/**
 * Ringover public API adapter: implements {@link
 * io.github.leoleiria.callrecording.core.port.CallGateway} over HTTPS.
 */
module io.github.leoleiria.callrecording.ringover {
  requires transitive io.github.leoleiria.callrecording.core;
  requires java.net.http;
  requires com.fasterxml.jackson.databind;
  requires org.slf4j;

  exports io.github.leoleiria.callrecording.ringover;
}
