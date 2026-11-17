package io.github.leoleiria.callrecording.app;

import javafx.application.Application;

/** Entry point. Kept separate from {@link CallRecordingApp} so packaged launchers stay simple. */
public final class Launcher {

  private Launcher() {}

  public static void main(String[] args) {
    Application.launch(CallRecordingApp.class, args);
  }
}
