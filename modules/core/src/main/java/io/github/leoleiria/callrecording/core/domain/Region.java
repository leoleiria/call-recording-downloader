package io.github.leoleiria.callrecording.core.domain;

import java.net.URI;

/** Ringover hosting region. API keys only work against the region of their account. */
public enum Region {
  EUROPE(URI.create("https://public-api.ringover.com/v2/")),
  UNITED_STATES(URI.create("https://public-api-us.ringover.com/v2/"));

  private final URI apiBaseUri;

  Region(URI apiBaseUri) {
    this.apiBaseUri = apiBaseUri;
  }

  /** Base URI of the public API, always ending with a slash. */
  public URI apiBaseUri() {
    return apiBaseUri;
  }
}
