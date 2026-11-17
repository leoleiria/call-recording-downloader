package io.github.leoleiria.callrecording.core.domain;

import io.github.leoleiria.callrecording.core.security.ApiKey;
import java.util.Objects;

/**
 * What is needed to talk to a Ringover account. Lives only for one search/download session.
 *
 * @param region the account's hosting region
 * @param apiKey the user's API key
 */
public record Connection(Region region, ApiKey apiKey) {

  public Connection {
    Objects.requireNonNull(region, "region");
    Objects.requireNonNull(apiKey, "apiKey");
  }
}
