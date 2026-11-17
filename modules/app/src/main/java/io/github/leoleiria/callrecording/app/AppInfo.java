package io.github.leoleiria.callrecording.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Properties;

/** Build information and well-known locations. */
public final class AppInfo {

  private static final Properties BUILD = load();

  private AppInfo() {}

  public static String name() {
    return BUILD.getProperty("app.name", "Call Recording Downloader");
  }

  public static String version() {
    return BUILD.getProperty("app.version", "dev");
  }

  public static String repositoryUrl() {
    return BUILD.getProperty("app.url", "https://github.com/leoleiria/call-recording-downloader");
  }

  /** Folder of the log files; must match {@code logback.xml}. */
  public static Path logFolder() {
    String override = System.getProperty("crd.logDir");
    if (override != null && !override.isBlank()) {
      return Path.of(override);
    }
    return Path.of(System.getProperty("user.home"), ".call-recording-downloader", "logs");
  }

  private static Properties load() {
    Properties properties = new Properties();
    try (InputStream in = AppInfo.class.getResourceAsStream("/app.properties")) {
      if (in != null) {
        properties.load(in);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return properties;
  }
}
