package io.github.leoleiria.callrecording.app.ui;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The application logo at every size, generated from {@code packaging/icons/logo.png} by {@code
 * script/GenerateIcons.java}.
 *
 * <p>Every window gets the full set, so the operating system can pick the sharpest size for the
 * title bar, the taskbar and the window switcher.
 */
public final class AppIcons {

  private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};
  private static List<Image> icons;

  private AppIcons() {}

  /** All icon sizes, smallest first. */
  public static synchronized List<Image> all() {
    if (icons == null) {
      List<Image> loaded = new ArrayList<>();
      for (int size : SIZES) {
        loaded.add(load(size));
      }
      icons = List.copyOf(loaded);
    }
    return icons;
  }

  /** Sets the logo on a window (title bar and taskbar). */
  public static void applyTo(Stage stage) {
    stage.getIcons().setAll(all());
  }

  /** Sets the logo on a dialog's window and shows it in the dialog header. */
  public static void applyTo(Dialog<?> dialog, double headerSize) {
    Window window = dialog.getDialogPane().getScene().getWindow();
    if (window instanceof Stage stage) {
      applyTo(stage);
    }
    dialog.setGraphic(logo(headerSize));
  }

  /** The logo as a node for screens, rendered from the 256 px image for sharpness. */
  public static ImageView logo(double size) {
    ImageView view = new ImageView(all().get(SIZES.length - 1));
    view.setFitWidth(size);
    view.setFitHeight(size);
    view.setPreserveRatio(true);
    view.setSmooth(true);
    view.getStyleClass().add("app-logo");
    return view;
  }

  private static Image load(int size) {
    String path = "/icons/app-" + size + ".png";
    try (InputStream in =
        Objects.requireNonNull(AppIcons.class.getResourceAsStream(path), "Missing icon " + path)) {
      return new Image(in);
    } catch (java.io.IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
