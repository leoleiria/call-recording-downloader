package io.github.leoleiria.callrecording.core.security;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes secrets from text before it is written to a log.
 *
 * <p>Two layers of protection are applied:
 *
 * <ol>
 *   <li>every secret registered through {@link #register(String)} (all {@link ApiKey}s are) is
 *       replaced by its masked form wherever it appears;
 *   <li>the value of any {@code Authorization} header, in the common {@code Authorization: value}
 *       or {@code Authorization=value} forms, is masked even if it was never registered.
 * </ol>
 */
public final class SecretRedactor {

  private static final Pattern AUTHORIZATION_HEADER =
      Pattern.compile("(?i)(authorization\\s*[:=]\\s*)([^\\s,;\"'}\\]]+)");

  private static final Set<String> SECRETS = ConcurrentHashMap.newKeySet();

  private SecretRedactor() {}

  /** Registers a secret to be scrubbed from every log message from now on. */
  public static void register(String secret) {
    if (secret != null && !secret.isBlank()) {
      SECRETS.add(secret);
    }
  }

  /** Forgets all registered secrets. Called when the application exits. */
  public static void clear() {
    SECRETS.clear();
  }

  /** Returns {@code text} with every known secret and authorization header value masked. */
  public static String redact(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    String result = text;
    for (String secret : SECRETS) {
      if (result.contains(secret)) {
        result = result.replace(secret, ApiKey.mask(secret));
      }
    }
    Matcher matcher = AUTHORIZATION_HEADER.matcher(result);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String replacement = matcher.group(1) + ApiKey.mask(matcher.group(2));
      matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(out);
    return out.toString();
  }
}
