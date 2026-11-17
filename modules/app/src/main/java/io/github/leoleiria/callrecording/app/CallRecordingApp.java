package io.github.leoleiria.callrecording.app;

import io.github.leoleiria.callrecording.app.i18n.Messages;
import io.github.leoleiria.callrecording.app.i18n.SupportedLanguage;
import io.github.leoleiria.callrecording.app.settings.UserSettings;
import io.github.leoleiria.callrecording.app.ui.AppIcons;
import io.github.leoleiria.callrecording.app.ui.LanguageView;
import io.github.leoleiria.callrecording.app.ui.MainView;
import io.github.leoleiria.callrecording.core.port.CallGateway;
import io.github.leoleiria.callrecording.core.security.SecretRedactor;
import io.github.leoleiria.callrecording.ringover.RingoverCallGateway;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JavaFX application: asks for the language on first launch, then shows the main screen. */
public final class CallRecordingApp extends Application {

  private static final Logger LOG = LoggerFactory.getLogger(CallRecordingApp.class);

  private final UserSettings settings = new UserSettings();
  private final CallGateway gateway = RingoverCallGateway.create();
  private final ExecutorService background =
      Executors.newSingleThreadExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "background-work");
            thread.setDaemon(true);
            return thread;
          });

  private Stage stage;

  /** Creates the application. Called by JavaFX. */
  public CallRecordingApp() {}

  @Override
  public void start(Stage primaryStage) {
    this.stage = primaryStage;
    LOG.info("Starting {} {}", AppInfo.name(), AppInfo.version());
    AppIcons.applyTo(stage);
    stage.setMinWidth(640);
    stage.setMinHeight(560);
    settings
        .language()
        .ifPresentOrElse(
            this::showMain, () -> showLanguageChoice(SupportedLanguage.systemDefault()));
    stage.show();
  }

  private void showLanguageChoice(SupportedLanguage preselected) {
    Messages messages = Messages.forLanguage(preselected);
    LanguageView view =
        new LanguageView(
            messages,
            preselected,
            chosen -> {
              settings.setLanguage(chosen);
              showMain(chosen);
            });
    show(view.root(), messages);
  }

  private void showMain(SupportedLanguage language) {
    Locale.setDefault(language.locale());
    Messages messages = Messages.forLanguage(language);
    MainView view =
        new MainView(
            messages,
            settings,
            gateway,
            background,
            getHostServices(),
            () -> showLanguageChoice(language));
    show(view.root(), messages);
  }

  private void show(Parent root, Messages messages) {
    Scene scene = new Scene(root, 760, 720);
    scene
        .getStylesheets()
        .add(
            Objects.requireNonNull(getClass().getResource("/css/app.css"), "app.css")
                .toExternalForm());
    stage.setTitle(messages.get("app.title"));
    stage.setScene(scene);
  }

  @Override
  public void stop() {
    background.shutdownNow();
    SecretRedactor.clear();
    LOG.info("Stopped");
  }
}
