package io.github.leoleiria.callrecording.core.domain;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Reference to a downloadable audio file of a call.
 *
 * @param kind recording or voicemail
 * @param url where the API says the file lives
 */
public record MediaRef(MediaKind kind, URI url) {

  private static final Set<String> AUDIO_EXTENSIONS =
      Set.of("mp3", "wav", "m4a", "ogg", "aac", "mpeg", "mpg", "webm");

  public MediaRef {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(url, "url");
  }

  /** File extension taken from the URL path, {@code mp3} when unknown. */
  public String fileExtension() {
    String path = url.getPath();
    if (path != null) {
      int dot = path.lastIndexOf('.');
      int slash = path.lastIndexOf('/');
      if (dot > slash && dot < path.length() - 1) {
        String extension = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (AUDIO_EXTENSIONS.contains(extension)) {
          return extension;
        }
      }
    }
    return "mp3";
  }
}
