package io.github.leoleiria.callrecording.ringover;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leoleiria.callrecording.core.domain.CallLog;
import io.github.leoleiria.callrecording.core.domain.Connection;
import io.github.leoleiria.callrecording.core.domain.MediaRef;
import io.github.leoleiria.callrecording.core.domain.PhoneNumber;
import io.github.leoleiria.callrecording.core.domain.Region;
import io.github.leoleiria.callrecording.core.domain.TimeWindow;
import io.github.leoleiria.callrecording.core.port.CallGateway;
import io.github.leoleiria.callrecording.core.port.CancellationToken;
import io.github.leoleiria.callrecording.core.port.GatewayException;
import io.github.leoleiria.callrecording.core.port.GatewayException.Reason;
import io.github.leoleiria.callrecording.core.transcript.Transcription;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Function;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link CallGateway} backed by the Ringover public API v2.
 *
 * <ul>
 *   <li>Calls: {@code POST /calls?expand=transcription} with the {@code ADVANCED} filter on the
 *       external number, paginated with the {@code last_id_returned} cursor.
 *   <li>Audio: private CDN files are fetched through {@code POST /cdn/download}; other Ringover CDN
 *       links are downloaded directly <em>without</em> the API key.
 *   <li>Transcriptions: {@code GET /transcriptions/{callId}}.
 * </ul>
 *
 * <p>Every API request goes through a shared {@link RateLimiter} and is retried according to the
 * {@link RetryPolicy}. The API key is only ever sent to the configured API base URI, never logged
 * and never forwarded on redirects.
 */
public final class RingoverCallGateway implements CallGateway {

  /** Page size for call searches. Smaller than the API maximum because of {@code expand}. */
  static final int PAGE_SIZE = 200;

  private static final Logger LOG = LoggerFactory.getLogger(RingoverCallGateway.class);
  private static final Set<String> PRIVATE_CDN_HOSTS =
      Set.of("cdn.ringover.com", "cdn-eu.ringover.com", "cdn-us.ringover.com");
  private static final Duration API_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(2);

  private final HttpClient apiClient;
  private final HttpClient cdnClient;
  private final Function<Region, URI> baseUri;
  private final RateLimiter rateLimiter;
  private final RetryPolicy retryPolicy;
  private final Sleeper sleeper;
  private final Predicate<URI> directDownloadAllowed;
  private final ObjectMapper json = new ObjectMapper();
  private final ObjectMapper prettyJson =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private RingoverCallGateway(Builder builder) {
    this.apiClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    this.cdnClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    this.baseUri = builder.baseUri;
    this.rateLimiter = builder.rateLimiter;
    this.retryPolicy = builder.retryPolicy;
    this.sleeper = builder.sleeper;
    this.directDownloadAllowed = builder.directDownloadAllowed;
  }

  /** Gateway with the production settings. */
  public static RingoverCallGateway create() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  // ---------------------------------------------------------------------------------------------
  // Calls

  @Override
  public List<CallLog> findCallLogs(
      Connection connection,
      PhoneNumber contact,
      TimeWindow window,
      CancellationToken cancellation) {
    URI uri = api(connection, "calls?expand=transcription");
    List<CallLog> logs = new ArrayList<>();
    Long cursor = null;
    while (true) {
      cancellation.throwIfCancelled();
      ObjectNode body = searchBody(contact, window, cursor);
      HttpResponse<String> response =
          sendWithRetry(
              () -> jsonPost(connection, uri, body), HttpResponse.BodyHandlers.ofString(), uri);
      if (response.statusCode() == 204) {
        break;
      }
      ensureSuccess(response.statusCode(), uri);

      JsonNode page = parse(response.body(), uri);
      JsonNode list = page.path("call_list");
      int pageSize = 0;
      long lowestCdrId = Long.MAX_VALUE;
      for (JsonNode item : list) {
        pageSize++;
        try {
          CallLog log = RingoverJson.toCallLog(item);
          logs.add(log);
          lowestCdrId = Math.min(lowestCdrId, log.cdrId());
        } catch (IllegalArgumentException e) {
          LOG.warn("Ignoring a call log the app could not read: {}", e.getMessage());
        }
      }
      LOG.debug("Call page: {} item(s)", pageSize);
      boolean lastPage = pageSize < PAGE_SIZE || lowestCdrId == Long.MAX_VALUE;
      boolean cursorStuck = cursor != null && lowestCdrId >= cursor;
      if (lastPage || cursorStuck) {
        break;
      }
      cursor = lowestCdrId;
    }
    return List.copyOf(logs);
  }

  private ObjectNode searchBody(PhoneNumber contact, TimeWindow window, Long cursor) {
    ObjectNode body = json.createObjectNode();
    body.put("filter", "ADVANCED");
    body.put("start_date", window.from().toString());
    body.put("end_date", window.to().toString());
    body.put("limit_count", PAGE_SIZE);
    if (cursor != null) {
      body.put("last_id_returned", cursor);
    }
    body.putObject("advanced").putArray("ext_numbers").add(Long.parseLong(contact.digits()));
    return body;
  }

  // ---------------------------------------------------------------------------------------------
  // Audio

  @Override
  public void downloadMedia(Connection connection, MediaRef media, OutputStream target) {
    URI url = media.url();
    HttpResponse<InputStream> response;
    if (isPrivateCdnDownload(url)) {
      URI proxy = api(connection, "cdn/download");
      ObjectNode body = json.createObjectNode().put("url", url.toString());
      response =
          sendWithRetry(
              () -> jsonPost(connection, proxy, body).timeout(DOWNLOAD_TIMEOUT),
              HttpResponse.BodyHandlers.ofInputStream(),
              proxy);
    } else if (directDownloadAllowed.test(url)) {
      // Not an API URL: the key is deliberately not sent.
      response =
          sendWithRetry(
              () -> HttpRequest.newBuilder(url).timeout(DOWNLOAD_TIMEOUT).GET(),
              HttpResponse.BodyHandlers.ofInputStream(),
              url,
              cdnClient);
    } else {
      throw new GatewayException(
          Reason.UNEXPECTED_RESPONSE, "Refusing to download from an unknown host: " + safe(url));
    }

    try (InputStream in = response.body()) {
      ensureSuccess(response.statusCode(), url);
      in.transferTo(target);
    } catch (IOException e) {
      throw new GatewayException(Reason.NETWORK, "Download interrupted: " + safe(url), e);
    }
  }

  private static boolean isPrivateCdnDownload(URI url) {
    String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
    String path = url.getPath() == null ? "" : url.getPath();
    return "https".equalsIgnoreCase(url.getScheme())
        && PRIVATE_CDN_HOSTS.contains(host)
        && (path.startsWith("/v2/private/download/") || path.startsWith("/v2/public/download/"));
  }

  /** Default rule for direct downloads: HTTPS on a Ringover domain. */
  static boolean isRingoverHttps(URI url) {
    String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
    return "https".equalsIgnoreCase(url.getScheme())
        && (host.equals("ringover.com") || host.endsWith(".ringover.com"));
  }

  // ---------------------------------------------------------------------------------------------
  // Transcriptions

  @Override
  public Optional<Transcription> fetchTranscription(Connection connection, String callId) {
    URI uri =
        api(connection, "transcriptions/" + URLEncoder.encode(callId, StandardCharsets.UTF_8));
    HttpResponse<String> response =
        sendWithRetry(
            () -> authorized(connection, HttpRequest.newBuilder(uri)).GET(),
            HttpResponse.BodyHandlers.ofString(),
            uri);
    int status = response.statusCode();
    if (status == 204 || status == 404) {
      return Optional.empty();
    }
    if (status == 401 || status == 402) {
      // The calls search already proved the key valid, so a refusal here means the team plan
      // does not include transcriptions.
      throw new GatewayException(
          Reason.FEATURE_UNAVAILABLE, "Transcriptions are not enabled for this account");
    }
    ensureSuccess(status, uri);
    JsonNode root = parse(response.body(), uri);
    return RingoverJson.toTranscription(callId, root, pretty(root));
  }

  // ---------------------------------------------------------------------------------------------
  // HTTP plumbing

  /** Builds a request; called again for every retry because builders are single-use. */
  @FunctionalInterface
  private interface RequestFactory {
    HttpRequest.Builder newRequest();
  }

  private <T> HttpResponse<T> sendWithRetry(
      RequestFactory factory, HttpResponse.BodyHandler<T> handler, URI target) {
    return sendWithRetry(factory, handler, target, apiClient);
  }

  private <T> HttpResponse<T> sendWithRetry(
      RequestFactory factory, HttpResponse.BodyHandler<T> handler, URI target, HttpClient client) {
    int attempt = 0;
    while (true) {
      attempt++;
      try {
        rateLimiter.acquire();
        HttpRequest request = factory.newRequest().build();
        HttpResponse<T> response = client.send(request, handler);
        LOG.debug("{} {} -> {}", request.method(), safe(target), response.statusCode());
        if (!retryPolicy.isRetryable(response.statusCode())
            || attempt >= retryPolicy.maxAttempts()) {
          return response;
        }
        discard(response);
        Duration delay =
            retryPolicy.delayAfter(attempt, response.headers().firstValue("Retry-After"));
        LOG.info(
            "HTTP {} from {}, retrying in {} ms",
            response.statusCode(),
            safe(target),
            delay.toMillis());
        sleeper.sleep(delay);
      } catch (IOException e) {
        if (attempt >= retryPolicy.maxAttempts()) {
          throw new GatewayException(Reason.NETWORK, "Cannot reach " + safe(target), e);
        }
        LOG.info("Network error on {} ({}), retrying", safe(target), e.getClass().getSimpleName());
        sleepQuietly(retryPolicy.delayAfter(attempt, Optional.empty()));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new CancellationException("Interrupted");
      }
    }
  }

  private void sleepQuietly(Duration delay) {
    try {
      sleeper.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CancellationException("Interrupted");
    }
  }

  private HttpRequest.Builder jsonPost(Connection connection, URI uri, JsonNode body) {
    return authorized(connection, HttpRequest.newBuilder(uri))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(write(body)));
  }

  private static HttpRequest.Builder authorized(Connection connection, HttpRequest.Builder req) {
    return req.timeout(API_TIMEOUT)
        .header("Accept", "application/json")
        .header("Authorization", connection.apiKey().reveal());
  }

  private URI api(Connection connection, String relativePath) {
    return baseUri.apply(connection.region()).resolve(relativePath);
  }

  private static void ensureSuccess(int status, URI uri) {
    if (status >= 200 && status < 300) {
      return;
    }
    Reason reason =
        switch (status) {
          case 400, 422 -> Reason.BAD_REQUEST;
          case 401 -> Reason.UNAUTHORIZED;
          case 402 -> Reason.FEATURE_UNAVAILABLE;
          case 403 -> Reason.FORBIDDEN;
          case 429 -> Reason.RATE_LIMITED;
          default -> status >= 500 ? Reason.SERVER_ERROR : Reason.UNEXPECTED_RESPONSE;
        };
    throw new GatewayException(reason, "HTTP " + status + " from " + safe(uri));
  }

  private JsonNode parse(String body, URI uri) {
    try {
      return json.readTree(body == null ? "" : body);
    } catch (JsonProcessingException e) {
      throw new GatewayException(Reason.UNEXPECTED_RESPONSE, "Invalid JSON from " + safe(uri), e);
    }
  }

  private String write(JsonNode node) {
    try {
      return json.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String pretty(JsonNode node) {
    try {
      return prettyJson.writeValueAsString(node) + System.lineSeparator();
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void discard(HttpResponse<?> response) {
    if (response.body() instanceof InputStream in) {
      try {
        in.close();
      } catch (IOException ignored) {
        // Nothing useful to do; the connection is dropped.
      }
    }
  }

  /** URI without query string, safe to log. */
  private static String safe(URI uri) {
    return uri.getScheme() + "://" + uri.getHost() + (uri.getPath() == null ? "" : uri.getPath());
  }

  /** Configures a {@link RingoverCallGateway}. Defaults match production. */
  public static final class Builder {

    private Function<Region, URI> baseUri = Region::apiBaseUri;
    private RateLimiter rateLimiter = RateLimiter.ringoverDefault();
    private RetryPolicy retryPolicy = RetryPolicy.ringoverDefault();
    private Sleeper sleeper = Sleeper.system();
    private Predicate<URI> directDownloadAllowed = RingoverCallGateway::isRingoverHttps;

    private Builder() {}

    /** Overrides the API base URI (must end with {@code /}). Used by tests. */
    public Builder baseUri(Function<Region, URI> baseUri) {
      this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
      return this;
    }

    public Builder rateLimiter(RateLimiter rateLimiter) {
      this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
      return this;
    }

    public Builder retryPolicy(RetryPolicy retryPolicy) {
      this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
      return this;
    }

    /** How to wait between retries. */
    public Builder sleeper(Sleeper sleeper) {
      this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
      return this;
    }

    /** Which non-API URLs may be downloaded directly, without the API key. */
    public Builder directDownloadAllowed(Predicate<URI> rule) {
      this.directDownloadAllowed = Objects.requireNonNull(rule, "rule");
      return this;
    }

    public RingoverCallGateway build() {
      return new RingoverCallGateway(this);
    }
  }
}
