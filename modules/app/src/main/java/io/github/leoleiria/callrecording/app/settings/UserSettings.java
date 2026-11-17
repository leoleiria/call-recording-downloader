package io.github.leoleiria.callrecording.app.settings;

import io.github.leoleiria.callrecording.app.i18n.SupportedLanguage;
import io.github.leoleiria.callrecording.core.domain.Region;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Preferences remembered between runs: language, region and last folder used.
 *
 * <p>By design, the API key and the phone numbers searched are <strong>never</strong> stored.
 */
public final class UserSettings {

  private static final String LANGUAGE = "language";
  private static final String REGION = "region";
  private static final String LAST_FOLDER = "lastFolder";

  private final Preferences preferences;

  public UserSettings() {
    this(Preferences.userRoot().node("io/github/leoleiria/call-recording-downloader"));
  }

  UserSettings(Preferences preferences) {
    this.preferences = preferences;
  }

  public Optional<SupportedLanguage> language() {
    return Optional.ofNullable(preferences.get(LANGUAGE, null))
        .flatMap(SupportedLanguage::fromCode);
  }

  public void setLanguage(SupportedLanguage language) {
    preferences.put(LANGUAGE, language.code());
  }

  public Region region() {
    try {
      return Region.valueOf(preferences.get(REGION, Region.EUROPE.name()));
    } catch (IllegalArgumentException e) {
      return Region.EUROPE;
    }
  }

  public void setRegion(Region region) {
    preferences.put(REGION, region.name());
  }

  /** Last folder chosen for downloads, if it still exists. */
  public Optional<Path> lastFolder() {
    String value = preferences.get(LAST_FOLDER, null);
    if (value == null) {
      return Optional.empty();
    }
    try {
      Path path = Path.of(value);
      return Files.isDirectory(path) ? Optional.of(path) : Optional.empty();
    } catch (InvalidPathException e) {
      return Optional.empty();
    }
  }

  public void setLastFolder(Path folder) {
    preferences.put(LAST_FOLDER, folder.toAbsolutePath().toString());
  }
}
