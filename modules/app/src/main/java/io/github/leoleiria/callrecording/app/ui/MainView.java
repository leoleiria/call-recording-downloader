package io.github.leoleiria.callrecording.app.ui;

import io.github.leoleiria.callrecording.app.AppInfo;
import io.github.leoleiria.callrecording.app.i18n.Messages;
import io.github.leoleiria.callrecording.app.settings.UserSettings;
import io.github.leoleiria.callrecording.core.domain.Connection;
import io.github.leoleiria.callrecording.core.domain.DateRange;
import io.github.leoleiria.callrecording.core.domain.DownloadOption;
import io.github.leoleiria.callrecording.core.domain.PhoneNumber;
import io.github.leoleiria.callrecording.core.domain.Region;
import io.github.leoleiria.callrecording.core.domain.SearchResult;
import io.github.leoleiria.callrecording.core.port.CallGateway;
import io.github.leoleiria.callrecording.core.port.CancellationToken;
import io.github.leoleiria.callrecording.core.security.ApiKey;
import io.github.leoleiria.callrecording.core.transcript.TranscriptTextFormatter;
import io.github.leoleiria.callrecording.core.usecase.DownloadFilesUseCase;
import io.github.leoleiria.callrecording.core.usecase.DownloadReport;
import io.github.leoleiria.callrecording.core.usecase.SearchCallsUseCase;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javafx.application.HostServices;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Main screen: search form, results and download actions. */
public final class MainView {

  private static final Logger LOG = LoggerFactory.getLogger(MainView.class);
  private static final int DEFAULT_DAYS = SearchCallsUseCase.MAX_DAYS_PER_REQUEST;

  private final Messages messages;
  private final UserSettings settings;
  private final ExecutorService background;
  private final HostServices hostServices;
  private final SearchCallsUseCase searchCalls;
  private final DownloadFilesUseCase downloadFiles;
  private final ZoneId zone = ZoneId.systemDefault();

  private final VBox root = new VBox(18);
  private final ComboBox<Region> region = new ComboBox<>();
  private final TextField phone = new TextField();
  private final SecretField apiKey;
  private final DatePicker startDate = new DatePicker();
  private final DatePicker endDate = new DatePicker();
  private final Button search = new Button();
  private final Label callCount = new Label("–");
  private final Label audioCount = new Label("–");
  private final Label voicemailNote = new Label();
  private final Label transcriptionCount = new Label("–");
  private final Button downloadAll = new Button();
  private final Button downloadRecordings = new Button();
  private final Button downloadTranscriptions = new Button();
  private final ProgressBar progress = new ProgressBar(0);
  private final Label status = new Label();
  private final Button cancel = new Button();
  private final Hyperlink openFolder = new Hyperlink();

  private final BooleanProperty busy = new SimpleBooleanProperty();
  private final BooleanProperty hasResult = new SimpleBooleanProperty();
  private SearchResult result;
  private CancellationToken cancellation = CancellationToken.none();

  /**
   * @param onChangeLanguage shows the language screen again
   */
  public MainView(
      Messages messages,
      UserSettings settings,
      CallGateway gateway,
      ExecutorService background,
      HostServices hostServices,
      Runnable onChangeLanguage) {
    this.messages = messages;
    this.settings = settings;
    this.background = background;
    this.hostServices = hostServices;
    this.searchCalls = new SearchCallsUseCase(gateway);
    this.downloadFiles = new DownloadFilesUseCase(gateway);
    this.apiKey = new SecretField(text("form.apiKey.show"), text("form.apiKey.hide"));

    root.getStyleClass().add("page");
    VBox content =
        new VBox(18, header(onChangeLanguage), form(), results(), downloads(), progressPanel());
    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("page-scroll");
    VBox.setVgrow(scroll, Priority.ALWAYS);
    root.getChildren().add(scroll);

    invalidateResultWhenInputChanges();
    setStatus(text("status.ready"), false);
  }

  public Parent root() {
    return root;
  }

  // ---------------------------------------------------------------------------------------------
  // Layout

  private Node header(Runnable onChangeLanguage) {
    Label title = new Label(text("app.title"));
    title.getStyleClass().add("title");
    Label tagline = new Label(text("app.tagline"));
    tagline.getStyleClass().add("subtitle");
    tagline.setWrapText(true);
    VBox titles = new VBox(4, title, tagline);
    HBox.setHgrow(titles, Priority.ALWAYS);
    HBox brand = new HBox(14, AppIcons.logo(56), titles);
    brand.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(brand, Priority.ALWAYS);

    Button language = new Button(text("action.language"));
    language.disableProperty().bind(busy);
    language.setOnAction(e -> onChangeLanguage.run());
    Button about = new Button(text("action.about"));
    about.setOnAction(e -> showAbout());
    // Never truncate these to "Langua..." when the tagline is long.
    language.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
    about.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);

    HBox header = new HBox(8, brand, language, about);
    header.setAlignment(Pos.TOP_LEFT);
    return header;
  }

  private Node form() {
    region.getItems().setAll(Region.values());
    region.setValue(settings.region());
    region.setButtonCell(regionCell());
    region.setCellFactory(list -> regionCell());
    region.setMaxWidth(Double.MAX_VALUE);

    phone.setPromptText("+34666666666");
    apiKey.setPromptText("••••••••••••");
    apiKey.setAccessibleName(text("form.apiKey"));

    LocalDate today = LocalDate.now(zone);
    endDate.setValue(today);
    startDate.setValue(today.minusDays(DEFAULT_DAYS - 1L));
    startDate.setMaxWidth(Double.MAX_VALUE);
    endDate.setMaxWidth(Double.MAX_VALUE);

    search.setText(text("action.search"));
    search.getStyleClass().add("primary");
    search.setDefaultButton(true);
    search.disableProperty().bind(busy);
    search.setOnAction(e -> startSearch());

    GridPane grid = new GridPane();
    grid.getStyleClass().add("card");
    grid.setHgap(12);
    grid.setVgap(10);
    ColumnConstraints labels = new ColumnConstraints();
    labels.setHalignment(HPos.RIGHT);
    ColumnConstraints fields = new ColumnConstraints();
    fields.setHgrow(Priority.ALWAYS);
    grid.getColumnConstraints().addAll(labels, fields);

    int row = 0;
    grid.addRow(row++, label("form.region", region), region);
    grid.addRow(row++, label("form.phone", phone), withHint(phone, "form.phone.hint"));
    grid.addRow(row++, label("form.apiKey", null), withHint(apiKey, "form.apiKey.hint"));
    HBox dates = new HBox(8, startDate, new Label("–"), endDate);
    dates.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(startDate, Priority.ALWAYS);
    HBox.setHgrow(endDate, Priority.ALWAYS);
    grid.addRow(row++, label("form.period", startDate), withHint(dates, "form.period.hint"));
    grid.add(search, 1, row);

    for (Node field : new Node[] {region, phone, apiKey, startDate, endDate}) {
      field.disableProperty().bind(busy);
    }
    return grid;
  }

  private Node results() {
    voicemailNote.getStyleClass().add("hint");
    // Takes space only when there is something to say, so all cards stay aligned.
    voicemailNote.managedProperty().bind(voicemailNote.textProperty().isNotEmpty());
    voicemailNote.visibleProperty().bind(voicemailNote.textProperty().isNotEmpty());
    HBox stats =
        new HBox(
            12,
            stat("result.calls", callCount, null),
            stat("result.recordings", audioCount, voicemailNote),
            stat("result.transcriptions", transcriptionCount, null));
    stats.getStyleClass().add("stats");
    return stats;
  }

  private Node downloads() {
    downloadAll.setText(text("action.downloadAll"));
    downloadRecordings.setText(text("action.downloadRecordings"));
    downloadTranscriptions.setText(text("action.downloadTranscriptions"));
    downloadAll.getStyleClass().add("primary");

    downloadAll.setOnAction(e -> startDownload(DownloadOption.RECORDINGS_AND_TRANSCRIPTIONS));
    downloadRecordings.setOnAction(e -> startDownload(DownloadOption.RECORDINGS_ONLY));
    downloadTranscriptions.setOnAction(e -> startDownload(DownloadOption.TRANSCRIPTIONS_ONLY));
    for (Button button : new Button[] {downloadAll, downloadRecordings, downloadTranscriptions}) {
      button.disableProperty().bind(busy.or(hasResult.not()));
    }

    FlowPane row = new FlowPane(10, 10, downloadAll, downloadRecordings, downloadTranscriptions);
    return row;
  }

  private Node progressPanel() {
    progress.setMaxWidth(Double.MAX_VALUE);
    progress.visibleProperty().bind(busy);
    progress.managedProperty().bind(busy);

    cancel.setText(text("action.cancel"));
    cancel.visibleProperty().bind(busy);
    cancel.managedProperty().bind(busy);
    cancel.setOnAction(e -> cancellation.cancel());

    status.setWrapText(true);
    status.getStyleClass().add("status");

    openFolder.setText(text("action.openFolder"));
    openFolder.setVisible(false);
    openFolder.setManaged(false);

    HBox bar = new HBox(10, progress, cancel);
    bar.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(progress, Priority.ALWAYS);
    return new VBox(8, bar, status, openFolder);
  }

  private Label label(String key, Node target) {
    Label label = new Label(text(key));
    label.getStyleClass().add("form-label");
    label.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
    if (target != null) {
      label.setLabelFor(target);
    }
    return label;
  }

  private VBox withHint(Node field, String hintKey) {
    Label hint = new Label(text(hintKey));
    hint.getStyleClass().add("hint");
    hint.setWrapText(true);
    return new VBox(4, field, hint);
  }

  private VBox stat(String key, Label value, Label note) {
    value.getStyleClass().add("stat-value");
    Label caption = new Label(text(key));
    caption.getStyleClass().add("stat-caption");
    VBox box = note == null ? new VBox(2, value, caption) : new VBox(2, value, caption, note);
    box.getStyleClass().add("stat");
    box.setAlignment(Pos.TOP_CENTER);
    HBox.setHgrow(box, Priority.ALWAYS);
    box.setMaxWidth(Double.MAX_VALUE);
    // Equal widths: all cards start from zero and share the free space evenly.
    box.setPrefWidth(0);
    box.setMinWidth(120);
    return box;
  }

  private ListCell<Region> regionCell() {
    return new ListCell<>() {
      @Override
      protected void updateItem(Region item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : text("region." + item.name()));
      }
    };
  }

  // ---------------------------------------------------------------------------------------------
  // Actions

  private void startSearch() {
    Optional<String> problem = validate();
    if (problem.isPresent()) {
      setStatus(problem.get(), true);
      return;
    }
    PhoneNumber contact = PhoneNumber.parse(phone.getText());
    DateRange range = new DateRange(startDate.getValue(), endDate.getValue());
    Connection connection = connection();
    settings.setRegion(region.getValue());
    clearResult();

    run(
        text("status.searching"),
        token ->
            new Task<SearchResult>() {
              @Override
              protected SearchResult call() {
                return searchCalls.search(
                    connection, contact, range, zone, this::updateProgress, token);
              }
            },
        this::showResult);
  }

  private void startDownload(DownloadOption option) {
    if (result == null) {
      return;
    }
    if (apiKey.getText().isBlank()) {
      setStatus(text("error.apiKey"), true);
      return;
    }
    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle(text("dialog.chooseFolder"));
    settings.lastFolder().map(Path::toFile).ifPresent(chooser::setInitialDirectory);
    File chosen = chooser.showDialog(root.getScene().getWindow());
    if (chosen == null) {
      return;
    }
    Path baseFolder = chosen.toPath();
    settings.setLastFolder(baseFolder);

    SearchResult current = result;
    Connection connection = connection();
    TranscriptTextFormatter formatter =
        new TranscriptTextFormatter(
            text("transcript.caller"), text("transcript.callee"), text("transcript.unknown"));

    run(
        text("status.downloading"),
        token ->
            new Task<DownloadReport>() {
              @Override
              protected DownloadReport call() throws Exception {
                return downloadFiles.download(
                    connection,
                    current,
                    option,
                    baseFolder,
                    zone,
                    formatter,
                    this::updateProgress,
                    token);
              }
            },
        this::showReport);
  }

  /** Runs a task in the background, keeping the UI responsive and cancellable. */
  private <T> void run(
      String runningText,
      java.util.function.Function<CancellationToken, Task<T>> taskFactory,
      Consumer<T> onSuccess) {
    cancellation = new CancellationToken();
    Task<T> task = taskFactory.apply(cancellation);
    progress.progressProperty().bind(task.progressProperty());
    busy.set(true);
    hideOpenFolder();
    setStatus(runningText, false);

    task.setOnSucceeded(
        e -> {
          finish();
          onSuccess.accept(task.getValue());
        });
    task.setOnFailed(
        e -> {
          finish();
          Throwable error = task.getException();
          if (error instanceof CancellationException) {
            setStatus(text("status.cancelled"), false);
          } else {
            LOG.error("Operation failed", error);
            setStatus(ErrorMessages.describe(error, messages), true);
          }
        });
    background.execute(task);
  }

  private void finish() {
    progress.progressProperty().unbind();
    busy.set(false);
  }

  private void showResult(SearchResult found) {
    result = found;
    hasResult.set(found.callCount() > 0);
    callCount.setText(Integer.toString(found.callCount()));
    audioCount.setText(Long.toString(found.audioCount()));
    voicemailNote.setText(
        found.voicemailCount() > 0 ? text("result.voicemails", found.voicemailCount()) : "");
    transcriptionCount.setText(Long.toString(found.transcriptionCount()));
    setStatus(text(found.callCount() == 0 ? "status.noCalls" : "status.searchDone"), false);
  }

  private void showReport(DownloadReport report) {
    StringBuilder message =
        new StringBuilder(
            text(
                "status.downloadDone",
                report.downloaded(),
                report.skipped(),
                report.failures().size()));
    if (report.transcriptionsUnavailable()) {
      message.append('\n').append(text("error.FEATURE_UNAVAILABLE"));
    }
    if (!report.failures().isEmpty()) {
      message.append('\n').append(text("status.seeLog", AppInfo.logFolder()));
    }
    setStatus(message.toString(), !report.failures().isEmpty());
    showOpenFolder(report.folder());
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers

  private Optional<String> validate() {
    if (!PhoneNumber.isValid(phone.getText())) {
      return Optional.of(text("error.phone"));
    }
    if (apiKey.getText() == null || apiKey.getText().isBlank()) {
      return Optional.of(text("error.apiKey"));
    }
    LocalDate start = startDate.getValue();
    LocalDate end = endDate.getValue();
    if (start == null || end == null || end.isBefore(start)) {
      return Optional.of(text("error.dates"));
    }
    if (new DateRange(start, end).isLongerThanOneYear()) {
      return Optional.of(text("error.periodTooLong"));
    }
    return Optional.empty();
  }

  /** Reads the key from the field each time; it is never kept elsewhere. */
  private Connection connection() {
    return new Connection(region.getValue(), ApiKey.of(apiKey.getText()));
  }

  private void invalidateResultWhenInputChanges() {
    region.valueProperty().addListener((obs, a, b) -> clearResult());
    phone.textProperty().addListener((obs, a, b) -> clearResult());
    startDate.valueProperty().addListener((obs, a, b) -> clearResult());
    endDate.valueProperty().addListener((obs, a, b) -> clearResult());
  }

  private void clearResult() {
    if (busy.get()) {
      return;
    }
    result = null;
    hasResult.set(false);
    callCount.setText("–");
    audioCount.setText("–");
    voicemailNote.setText("");
    transcriptionCount.setText("–");
  }

  private void setStatus(String message, boolean error) {
    status.setText(message);
    status.getStyleClass().remove("error");
    if (error) {
      status.getStyleClass().add("error");
    }
  }

  private void showOpenFolder(Path folder) {
    openFolder.setOnAction(e -> hostServices.showDocument(folder.toUri().toString()));
    openFolder.setVisible(true);
    openFolder.setManaged(true);
  }

  private void hideOpenFolder() {
    openFolder.setVisible(false);
    openFolder.setManaged(false);
  }

  private void showAbout() {
    Alert about = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
    about.setTitle(text("about.title"));
    about.setHeaderText(AppInfo.name() + " — " + text("about.version", AppInfo.version()));
    Label license = new Label(text("about.license"));
    license.setWrapText(true);
    Label disclaimer = new Label(text("about.disclaimer"));
    disclaimer.setWrapText(true);
    disclaimer.getStyleClass().add("hint");
    Hyperlink link = new Hyperlink(AppInfo.repositoryUrl());
    link.setOnAction(e -> hostServices.showDocument(AppInfo.repositoryUrl()));
    VBox body = new VBox(10, license, link, disclaimer);
    body.setMaxWidth(440);
    about.getDialogPane().setContent(body);
    about.initOwner(root.getScene().getWindow());
    AppIcons.applyTo(about, 64);
    about.showAndWait();
  }

  private String text(String key, Object... args) {
    return messages.get(key, args);
  }
}
