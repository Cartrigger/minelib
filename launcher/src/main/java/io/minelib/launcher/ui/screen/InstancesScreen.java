package io.minelib.launcher.ui.screen;

import io.minelib.launcher.model.Instance;
import io.minelib.launcher.service.AuthService;
import io.minelib.launcher.service.InstanceService;
import io.minelib.launcher.ui.component.InstanceCard;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.File;
import java.util.List;

/**
 * Shows the saved launcher instances as a card grid and allows creating new
 * ones or launching existing ones.
 *
 * <h2>Toolbar</h2>
 * <ul>
 *   <li>Heading / instance count</li>
 *   <li>"+ New Instance" button → dialog</li>
 *   <li>"Mods" button → navigates to {@link ModsScreen}</li>
 * </ul>
 *
 * <h2>Content</h2>
 * <p>A wrapping {@link FlowPane} of {@link InstanceCard} components.  If there
 * are no instances a friendly empty-state message is shown instead.
 */
public final class InstancesScreen extends BorderPane {

    private final AuthService     authService;
    private final InstanceService instanceService;
    private final Runnable        onOpenMods;
    private final FlowPane        cardGrid   = new FlowPane(12, 12);
    private final Label           statusBar  = new Label();

    public InstancesScreen(AuthService authService, InstanceService instanceService,
                           Runnable onOpenMods) {
        this.authService     = authService;
        this.instanceService = instanceService;
        this.onOpenMods      = onOpenMods;

        getStyleClass().add("instances-screen");

        statusBar.getStyleClass().add("label-muted");
        statusBar.setVisible(false);
        statusBar.setManaged(false);

        setTop(buildToolbar());
        setCenter(buildCardArea());
        setBottom(statusBar);
        BorderPane.setMargin(statusBar, new Insets(4, 20, 8, 20));
        setPadding(new Insets(0, 0, 0, 0));

        loadInstances();
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        Label heading = new Label("Instances");
        heading.getStyleClass().add("top-bar-title");

        Button btnNew  = new Button("+ New Instance");
        btnNew.getStyleClass().add("btn-primary");
        btnNew.setOnAction(e -> showNewInstanceDialog());

        Button btnMods = new Button("🧩  Mods");
        btnMods.getStyleClass().add("btn-secondary");
        btnMods.setOnAction(e -> onOpenMods.run());

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        HBox toolbar = new HBox(12, heading, spacer, btnMods, btnNew);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("top-bar");
        toolbar.setPadding(new Insets(16, 20, 12, 20));
        return toolbar;
    }

    // ── Card grid ─────────────────────────────────────────────────────────────

    private ScrollPane buildCardArea() {
        cardGrid.setPadding(new Insets(8, 20, 20, 20));
        cardGrid.setPrefWrapLength(Double.MAX_VALUE);

        ScrollPane scroll = new ScrollPane(cardGrid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-pane");
        return scroll;
    }

    private void loadInstances() {
        cardGrid.getChildren().clear();
        List<Instance> instances = instanceService.loadInstances();
        if (instances.isEmpty()) {
            Label empty = new Label("No instances yet \u2014 click \"+ New Instance\" to create one.");
            empty.getStyleClass().add("label-muted");
            cardGrid.getChildren().add(empty);
            return;
        }
        for (Instance inst : instances) {
            InstanceCard card = new InstanceCard(inst,
                    this::launchInstance,
                    this::editInstance,
                    this::openInstanceFolder,
                    this::deleteInstance);
            card.setPrefWidth(220);
            cardGrid.getChildren().add(card);
        }
    }

    // ── New-instance dialog ───────────────────────────────────────────────────

    private void showNewInstanceDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Instance");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField tfName    = new TextField();
        tfName.setPromptText("My 1.21.4 world");
        tfName.getStyleClass().add("text-field");

        TextField tfVersion = new TextField();
        tfVersion.setPromptText("1.21.4");
        tfVersion.getStyleClass().add("text-field");

        ComboBox<Instance.ModLoader> cbLoader = new ComboBox<>();
        cbLoader.getItems().addAll(Instance.ModLoader.values());
        cbLoader.setValue(Instance.ModLoader.VANILLA);
        cbLoader.getStyleClass().add("combo-box");

        VBox content = new VBox(10,
                new Label("Instance name"), tfName,
                new Label("Minecraft version"), tfVersion,
                new Label("Mod loader"), cbLoader);
        content.setPadding(new Insets(16));

        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            String name    = tfName.getText().trim();
            String version = tfVersion.getText().trim();
            if (name.isEmpty() || version.isEmpty()) return;

            new Thread(() -> {
                try {
                    instanceService.createInstance(name, version, cbLoader.getValue());
                    Platform.runLater(this::loadInstances);
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        Alert err = new Alert(Alert.AlertType.ERROR,
                                "Could not create instance: " + ex.getMessage());
                        err.showAndWait();
                    });
                }
            }, "instance-create").start();
        });
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void launchInstance(Instance instance) {
        statusBar.setText("⏳ Launching \"" + instance.getName() + "\" — downloading files if needed…");
        statusBar.setVisible(true);
        statusBar.setManaged(true);

        new Thread(() -> {
            try {
                instanceService.launch(instance, authService.getProfile());
                Platform.runLater(() -> {
                    statusBar.setText("✅ Launched \"" + instance.getName() + "\"");
                    loadInstances();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusBar.setText("❌ Launch failed: " + ex.getMessage());
                    Alert err = new Alert(Alert.AlertType.ERROR,
                            "Launch failed: " + ex.getMessage());
                    err.showAndWait();
                });
            }
        }, "instance-launch").start();
    }

    private void deleteInstance(Instance instance) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + instance.getName() + "\"? This cannot be undone.",
                ButtonType.YES, ButtonType.CANCEL);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                instanceService.deleteInstance(instance);
                loadInstances();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR,
                        "Could not delete: " + ex.getMessage()).showAndWait();
            }
        });
    }

    // ── Edit instance ─────────────────────────────────────────────────────────

    private void editInstance(Instance instance) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Instance — " + instance.getName());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField tfName    = new TextField(instance.getName());
        tfName.getStyleClass().add("text-field");

        TextField tfVersion = new TextField(instance.getMinecraftVersion());
        tfVersion.getStyleClass().add("text-field");

        ComboBox<Instance.ModLoader> cbLoader = new ComboBox<>();
        cbLoader.getItems().addAll(Instance.ModLoader.values());
        cbLoader.setValue(instance.getModLoader());
        cbLoader.getStyleClass().add("combo-box");

        Label memLabel = new Label("Memory: " + instance.getMemoryMb() + " MB");
        Slider memSlider = new Slider(512, 8192, instance.getMemoryMb());
        memSlider.setMajorTickUnit(1024);
        memSlider.setMinorTickCount(3);
        memSlider.setSnapToTicks(false);
        memSlider.setBlockIncrement(256);
        memSlider.valueProperty().addListener((obs, o, n) ->
                memLabel.setText("Memory: " + n.intValue() + " MB"));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.add(new Label("Instance name"),   0, 0); grid.add(tfName,    1, 0);
        grid.add(new Label("Minecraft version"), 0, 1); grid.add(tfVersion, 1, 1);
        grid.add(new Label("Mod loader"),      0, 2); grid.add(cbLoader,  1, 2);
        grid.add(new Label("Max memory"),      0, 3); grid.add(memLabel,  1, 3);
        grid.add(memSlider,                    0, 4, 2, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            String name    = tfName.getText().trim();
            String version = tfVersion.getText().trim();
            if (name.isEmpty() || version.isEmpty()) return;

            instance.setName(name);
            instance.setMinecraftVersion(version);
            instance.setModLoader(cbLoader.getValue());
            instance.setMemoryMb((int) memSlider.getValue());

            new Thread(() -> {
                try {
                    instanceService.saveInstance(instance);
                    Platform.runLater(this::loadInstances);
                } catch (Exception ex) {
                    Platform.runLater(() ->
                            new Alert(Alert.AlertType.ERROR,
                                    "Could not save instance: " + ex.getMessage())
                                    .showAndWait());
                }
            }, "instance-save").start();
        });
    }

    // ── Open folder ───────────────────────────────────────────────────────────

    private void openInstanceFolder(Instance instance) {
        java.nio.file.Path gameDir = instanceService.getGameDirectory(instance);
        try {
            File dir = gameDir.toFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            Desktop.getDesktop().open(dir);
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR,
                    "Cannot open folder: " + ex.getMessage()).showAndWait();
        }
    }
}
