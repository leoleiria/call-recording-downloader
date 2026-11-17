package io.github.leoleiria.callrecording.app.ui;

import io.github.leoleiria.callrecording.app.i18n.Messages;
import io.github.leoleiria.callrecording.app.i18n.SupportedLanguage;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** First screen: pick one of the 24 EU languages. */
public final class LanguageView {

  private final VBox root = new VBox(16);

  /**
   * @param messages texts in the pre-selected language
   * @param preselected language highlighted initially
   * @param onChosen called with the language the user confirmed
   */
  public LanguageView(
      Messages messages, SupportedLanguage preselected, Consumer<SupportedLanguage> onChosen) {
    root.getStyleClass().add("page");
    root.setAlignment(Pos.TOP_CENTER);

    Label title = new Label(messages.get("app.title"));
    title.getStyleClass().add("title");
    Label subtitle = new Label(messages.get("language.title"));
    subtitle.getStyleClass().add("subtitle");

    ListView<SupportedLanguage> list = new ListView<>();
    list.getItems().setAll(SupportedLanguage.values());
    list.setCellFactory(
        view ->
            new ListCell<>() {
              @Override
              protected void updateItem(SupportedLanguage item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.nativeName());
              }
            });
    list.getSelectionModel().select(preselected);
    // Languages near the top are already visible; scrolling would cut off the first entry.
    if (preselected.ordinal() > 12) {
      list.scrollTo(preselected);
    }
    list.setMaxWidth(360);
    VBox.setVgrow(list, Priority.ALWAYS);

    Button confirm = new Button(messages.get("language.continue"));
    confirm.setDefaultButton(true);
    confirm.getStyleClass().add("primary");
    confirm.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
    confirm.setOnAction(e -> onChosen.accept(list.getSelectionModel().getSelectedItem()));
    list.setOnMouseClicked(
        e -> {
          if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
            confirm.fire();
          }
        });

    root.getChildren().addAll(AppIcons.logo(88), title, subtitle, list, confirm);
  }

  public Parent root() {
    return root;
  }
}
