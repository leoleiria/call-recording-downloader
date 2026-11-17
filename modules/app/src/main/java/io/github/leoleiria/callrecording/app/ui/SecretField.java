package io.github.leoleiria.callrecording.app.ui;

import javafx.beans.property.StringProperty;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

/**
 * Text field for secrets: masked while typing or pasting, with a button to reveal it.
 *
 * <p>The value is hidden by default and every time the view is created. Copying the value out of
 * the masked field is not possible, matching {@link PasswordField}.
 */
public final class SecretField extends HBox {

  private final PasswordField masked = new PasswordField();
  private final TextField plain = new TextField();
  private final ToggleButton reveal = new ToggleButton();

  /**
   * @param showLabel text of the button while the value is hidden
   * @param hideLabel text of the button while the value is visible
   */
  public SecretField(String showLabel, String hideLabel) {
    getStyleClass().add("secret-field");
    setSpacing(6);

    plain.textProperty().bindBidirectional(masked.textProperty());
    plain.setVisible(false);
    plain.setManaged(false);

    StackPane fields = new StackPane(masked, plain);
    HBox.setHgrow(fields, Priority.ALWAYS);

    reveal.setText(showLabel);
    reveal.setFocusTraversable(false);
    reveal
        .selectedProperty()
        .addListener(
            (obs, wasShown, show) -> {
              plain.setVisible(show);
              plain.setManaged(show);
              masked.setVisible(!show);
              masked.setManaged(!show);
              reveal.setText(show ? hideLabel : showLabel);
              (show ? plain : masked).requestFocus();
              (show ? plain : masked).end();
            });

    getChildren().addAll(fields, reveal);
  }

  public StringProperty textProperty() {
    return masked.textProperty();
  }

  public String getText() {
    return masked.getText();
  }

  public void setPromptText(String prompt) {
    masked.setPromptText(prompt);
    plain.setPromptText(prompt);
  }

  /** Accessible name announced by screen readers. */
  public void setAccessibleName(String name) {
    masked.setAccessibleText(name);
    plain.setAccessibleText(name);
  }

  /** Whether the value is currently visible. */
  public boolean isRevealed() {
    return reveal.isSelected();
  }
}
