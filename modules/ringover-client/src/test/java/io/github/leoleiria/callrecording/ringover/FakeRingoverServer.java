package io.github.leoleiria.callrecording.ringover;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Minimal local HTTP server that plays scripted responses and records requests. */
final class FakeRingoverServer implements AutoCloseable {

  /** A request as received. */
  record Recorded(
      String method, String path, String query, Map<String, List<String>> headers, String body) {

    Optional<String> header(String name) {
      return headers.entrySet().stream()
          .filter(e -> e.getKey().equalsIgnoreCase(name))
          .flatMap(e -> e.getValue().stream())
          .findFirst();
    }
  }

  /** A scripted response. */
  record Reply(int status, byte[] body, Map<String, String> headers) {

    static Reply json(int status, String json) {
      return new Reply(status, json.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    static Reply status(int status) {
      return new Reply(status, new byte[0], Map.of());
    }

    Reply withHeader(String name, String value) {
      Map<String, String> copy = new java.util.HashMap<>(headers);
      copy.put(name, value);
      return new Reply(status, body, copy);
    }
  }

  private final HttpServer server;
  private final Map<String, Deque<Reply>> replies = new ConcurrentHashMap<>();
  final List<Recorded> requests = new CopyOnWriteArrayList<>();

  FakeRingoverServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", this::handle);
    server.start();
  }

  /** Base URI that plays the role of {@code https://public-api.ringover.com/v2/}. */
  URI baseUri() {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v2/");
  }

  /** Queues replies for a path; the last one repeats when the queue runs out. */
  FakeRingoverServer on(String path, Reply... scripted) {
    replies.computeIfAbsent(path, p -> new ArrayDeque<>()).addAll(List.of(scripted));
    return this;
  }

  List<Recorded> requestsTo(String path) {
    return requests.stream().filter(r -> r.path().equals(path)).toList();
  }

  private void handle(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    String path = exchange.getRequestURI().getPath();
    requests.add(
        new Recorded(
            exchange.getRequestMethod(),
            path,
            exchange.getRequestURI().getQuery(),
            Map.copyOf(exchange.getRequestHeaders()),
            body));

    Deque<Reply> queue = replies.get(path);
    Reply reply =
        queue == null || queue.isEmpty()
            ? Reply.status(404)
            : queue.size() > 1 ? queue.poll() : queue.peek();
    reply.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
    if (reply.body().length == 0) {
      exchange.sendResponseHeaders(reply.status(), -1);
    } else {
      exchange.sendResponseHeaders(reply.status(), reply.body().length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(reply.body());
      }
    }
    exchange.close();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
