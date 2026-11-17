package io.github.leoleiria.callrecording.app.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Translated user-interface texts, loaded from {@code i18n/messages_<code>.properties}.
 *
 * <p>Texts are formatted with {@link MessageFormat}; translations must therefore use the
 * typographic apostrophe (’) instead of {@code '}. A unit test enforces this.
 */
public final class Messages {

  /** Base name of the resource bundles. */
  public static final String BUNDLE = "i18n.messages";

  private final ResourceBundle bundle;
  private final Locale locale;

  private Messages(ResourceBundle bundle, Locale locale) {
    this.bundle = bundle;
    this.locale = locale;
  }

  public static Messages forLanguage(SupportedLanguage language) {
    // Every supported language has its own bundle file, so the lookup never falls back to the
    // operating system language. (A custom ResourceBundle.Control is not allowed in a module.)
    Locale locale = language.locale();
    return new Messages(ResourceBundle.getBundle(BUNDLE, locale), locale);
  }

  /** Returns the text for {@code key}, filling {@code {0}}, {@code {1}}... with {@code args}. */
  public String get(String key, Object... args) {
    String pattern;
    try {
      pattern = bundle.getString(key);
    } catch (MissingResourceException e) {
      return "!" + key + "!";
    }
    return new MessageFormat(pattern, locale).format(args);
  }

  public Locale locale() {
    return locale;
  }
}
