package io.github.leoleiria.callrecording.app.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.status.Status;
import io.github.leoleiria.callrecording.core.security.ApiKey;
import io.github.leoleiria.callrecording.core.security.SecretRedactor;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the real logback.xml and checks that API keys never reach the log file. */
class LogRedactionTest {

  private static final String SECRET = "ro-live-0123456789abcdefXYZ";

  @TempDir Path logDir;
  private LoggerContext context;

  @AfterEach
  void tearDown() {
    if (context != null) {
      context.stop();
    }
    System.clearProperty("crd.logDir");
    SecretRedactor.clear();
  }

  @Test
  void productionConfigurationLoadsWithoutErrors() throws Exception {
    configure();

    assertThat(context.getStatusManager().getCopyOfStatusList())
        .noneMatch(status -> status.getLevel() >= Status.WARN);
  }

  @Test
  void apiKeyIsMaskedInMessagesArgumentsAndStackTraces() throws Exception {
    configure();
    ApiKey key = ApiKey.of(SECRET);
    Logger log = context.getLogger("io.github.leoleiria.callrecording.test");

    log.info("raw key in message " + SECRET);
    log.info("raw key as argument {}", SECRET);
    log.info("key object {}", key);
    log.info("header Authorization: {}", "some-unregistered-token-123");
    log.error("failure", new IllegalStateException("exception with " + SECRET));
    context.stop();

    assertThat(context.getStatusManager().getCopyOfStatusList())
        .noneMatch(status -> status.getLevel() >= Status.ERROR);

    String written = Files.readString(logDir.resolve("app.log"), StandardCharsets.UTF_8);
    assertThat(written)
        .doesNotContain(SECRET)
        .doesNotContain("some-unregistered-token-123")
        .contains("raw key as argument ****fXYZ")
        .contains("key object ****fXYZ")
        .contains("Authorization: ****-123")
        .contains("raw key in message")
        .contains("IllegalStateException");
  }

  private void configure() throws Exception {
    System.setProperty("crd.logDir", logDir.toString());
    // A standalone context, separate from the one SLF4J created for the test run.
    context = new LoggerContext();
    context.setMDCAdapter(new LogbackMDCAdapter());
    context.start();
    JoranConfigurator configurator = new JoranConfigurator();
    configurator.setContext(context);
    URL config = LogRedactionTest.class.getResource("/logback.xml");
    assertThat(config).isNotNull();
    try (InputStream in = config.openStream()) {
      configurator.doConfigure(in);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
