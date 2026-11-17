package io.github.leoleiria.callrecording.app.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.leoleiria.callrecording.core.port.GatewayException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Guards the 24 translations against missing keys and formatting mistakes. */
class TranslationsTest {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

  @ParameterizedTest
  @EnumSource(SupportedLanguage.class)
  void everyLanguageHasExactlyTheBaseKeys(SupportedLanguage language) throws IOException {
    Properties base = load("messages.properties");
    Properties translation = load("messages_" + language.code() + ".properties");

    assertThat(translation.stringPropertyNames())
        .as("keys of %s", language)
        .containsExactlyInAnyOrderElementsOf(base.stringPropertyNames());
  }

  @ParameterizedTest
  @EnumSource(SupportedLanguage.class)
  void textsAreValidMessageFormatsWithTheSamePlaceholders(SupportedLanguage language)
      throws IOException {
    Properties base = load("messages.properties");
    Properties translation = load("messages_" + language.code() + ".properties");

    for (String key : base.stringPropertyNames()) {
      String text = translation.getProperty(key);
      assertThat(text).as("%s in %s", key, language).isNotBlank().doesNotContain("'");
      new MessageFormat(text);
      assertThat(placeholders(text))
          .as("placeholders of %s in %s", key, language)
          .isEqualTo(placeholders(base.getProperty(key)));
    }
  }

  @ParameterizedTest
  @EnumSource(SupportedLanguage.class)
  void bundleLoadsThroughMessages(SupportedLanguage language) {
    Messages messages = Messages.forLanguage(language);

    assertThat(messages.get("app.title")).isEqualTo("Call Recording Downloader");
    assertThat(messages.get("status.downloadDone", 1, 2, 3)).contains("1", "2", "3");
  }

  @Test
  void nonEnglishLanguagesAreActuallyTranslated() {
    String english = Messages.forLanguage(SupportedLanguage.ENGLISH).get("action.search");
    for (SupportedLanguage language : SupportedLanguage.values()) {
      if (language != SupportedLanguage.ENGLISH) {
        assertThat(Messages.forLanguage(language).get("action.search"))
            .as("action.search in %s", language)
            .isNotEqualTo(english);
      }
    }
  }

  @Test
  void everyGatewayErrorHasAMessage() throws IOException {
    Properties base = load("messages.properties");
    for (GatewayException.Reason reason : GatewayException.Reason.values()) {
      assertThat(base.getProperty("error." + reason.name())).as("error.%s", reason).isNotBlank();
    }
  }

  @Test
  void languagesAreTheTwentyFourOfficialEuLanguages() {
    assertThat(SupportedLanguage.values()).hasSize(24);
  }

  private static Properties load(String name) throws IOException {
    try (InputStream in = TranslationsTest.class.getResourceAsStream("/i18n/" + name)) {
      assertThat(in).as(name).isNotNull();
      Properties properties = new Properties();
      properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
      return properties;
    }
  }

  private static Set<String> placeholders(String text) {
    Set<String> found = new TreeSet<>();
    Matcher matcher = PLACEHOLDER.matcher(text);
    while (matcher.find()) {
      found.add(matcher.group(1));
    }
    return found;
  }
}
