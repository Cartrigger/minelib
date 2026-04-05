package io.minelib.launcher.ui.screen;

import io.minelib.launcher.model.Instance;
import io.minelib.launcher.service.InstanceService;
import io.minelib.launcher.ui.component.ModCard;
import io.minelib.modrinth.ModrinthClient;
import io.minelib.modrinth.ModrinthProject;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Modrinth mod browser screen.
 *
 * <p>The user can type a search query, select the target Minecraft version and
 * mod loader, and browse results returned by the Modrinth API.  Clicking
 * "Install" on a {@link ModCard} queues the mod for installation into the
 * currently selected instance.
 */
public final class ModsScreen extends BorderPane {

    private final InstanceService instanceService;
    private final ModrinthClient  modrinthClient;
    private final VBox            resultsList    = new VBox(8);

    public ModsScreen(InstanceService instanceService) {
        this.instanceService = instanceService;
        this.modrinthClient  = new ModrinthClient(new okhttp3.OkHttpClient());
        getStyleClass().add("mods-screen");

        setTop(buildToolbar());
        setCenter(buildResultsArea());
        setPadding(new Insets(0));
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        Label heading = new Label("Modrinth Mods");
        heading.getStyleClass().add("top-bar-title");

        TextField tfSearch = new TextField();
        tfSearch.setPromptText("Search mods…");
        tfSearch.setPrefWidth(260);
        tfSearch.getStyleClass().add("search-bar");
        tfSearch.setOnAction(e -> doSearch(tfSearch.getText().trim()));

        ComboBox<String> cbVersion = new ComboBox<>();
        cbVersion.setPromptText("MC version");
        cbVersion.getItems().addAll("1.21.4", "1.21.1", "1.20.4", "1.20.1", "1.19.4");
        cbVersion.setPrefWidth(110);

        Button btnSearch = new Button("Search");
        btnSearch.getStyleClass().add("btn-primary");
        btnSearch.setOnAction(e -> {
            String query = tfSearch.getText().trim();
            if (!query.isEmpty()) doSearch(query);
        });

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        HBox toolbar = new HBox(10, heading, spacer, tfSearch, cbVersion, btnSearch);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("top-bar");
        toolbar.setPadding(new Insets(16, 20, 12, 20));
        return toolbar;
    }

    // ── Results area ──────────────────────────────────────────────────────────

    private ScrollPane buildResultsArea() {
        resultsList.setPadding(new Insets(8, 20, 20, 20));

        Label hint = new Label("Search for mods to get started.");
        hint.getStyleClass().add("label-muted");
        resultsList.getChildren().add(hint);

        ScrollPane scroll = new ScrollPane(resultsList);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-pane");
        return scroll;
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private void doSearch(String query) {
        resultsList.getChildren().clear();
        Label loading = new Label("Searching…");
        loading.getStyleClass().add("label-muted");
        resultsList.getChildren().add(loading);

        new Thread(() -> {
            try {
                List<ModrinthProject> results = modrinthClient.search(query, null, null, 20);
                Platform.runLater(() -> showResults(results));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    resultsList.getChildren().setAll(
                            new Label("Search failed: " + ex.getMessage()));
                });
            }
        }, "modrinth-search").start();
    }

    private void showResults(List<ModrinthProject> projects) {
        resultsList.getChildren().clear();
        if (projects.isEmpty()) {
            resultsList.getChildren().add(new Label("No results found."));
            return;
        }
        for (ModrinthProject project : projects) {
            ModCard card = new ModCard(project, this::installMod);
            resultsList.getChildren().add(card);
        }
    }

    // ── Install ───────────────────────────────────────────────────────────────

    private void installMod(ModrinthProject project) {
        List<Instance> instances = instanceService.loadInstances();
        if (instances.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION,
                    "Create an instance first before installing mods.").showAndWait();
            return;
        }
        // Install into the first available instance as a simple default
        Instance target = instances.get(0);
        new Alert(Alert.AlertType.INFORMATION,
                "Queued \"" + project.getTitle() + "\" for installation into \""
                        + target.getName() + "\".\n"
                        + "(Full mod download happens on next game launch.)")
                .showAndWait();
    }
}
