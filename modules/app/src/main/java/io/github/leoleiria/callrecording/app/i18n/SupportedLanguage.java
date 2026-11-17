package io.github.leoleiria.callrecording.app.i18n;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** The 24 official languages of the European Union, each named in its own language. */
public enum SupportedLanguage {
  BULGARIAN("bg", "Български"),
  CZECH("cs", "Čeština"),
  DANISH("da", "Dansk"),
  GERMAN("de", "Deutsch"),
  GREEK("el", "Ελληνικά"),
  ENGLISH("en", "English"),
  SPANISH("es", "Español"),
  ESTONIAN("et", "Eesti"),
  FINNISH("fi", "Suomi"),
  FRENCH("fr", "Français"),
  IRISH("ga", "Gaeilge"),
  CROATIAN("hr", "Hrvatski"),
  HUNGARIAN("hu", "Magyar"),
  ITALIAN("it", "Italiano"),
  LITHUANIAN("lt", "Lietuvių"),
  LATVIAN("lv", "Latviešu"),
  MALTESE("mt", "Malti"),
  DUTCH("nl", "Nederlands"),
  POLISH("pl", "Polski"),
  PORTUGUESE("pt", "Português"),
  ROMANIAN("ro", "Română"),
  SLOVAK("sk", "Slovenčina"),
  SLOVENIAN("sl", "Slovenščina"),
  SWEDISH("sv", "Svenska");

  private final String code;
  private final String nativeName;

  SupportedLanguage(String code, String nativeName) {
    this.code = code;
    this.nativeName = nativeName;
  }

  /** ISO 639-1 code, also the suffix of the message bundle. */
  public String code() {
    return code;
  }

  /** Name of the language written in that language. */
  public String nativeName() {
    return nativeName;
  }

  public Locale locale() {
    return Locale.of(code);
  }

  public static Optional<SupportedLanguage> fromCode(String code) {
    return Arrays.stream(values()).filter(l -> l.code.equalsIgnoreCase(code)).findFirst();
  }

  /** The operating system language if supported, English otherwise. */
  public static SupportedLanguage systemDefault() {
    return fromCode(Locale.getDefault().getLanguage()).orElse(ENGLISH);
  }
}
