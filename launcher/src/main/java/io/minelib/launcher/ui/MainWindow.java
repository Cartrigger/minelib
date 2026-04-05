package io.minelib.launcher.ui;

import io.minelib.launcher.service.AuthService;
import io.minelib.launcher.service.InstanceService;
import io.minelib.launcher.ui.screen.InstancesScreen;
import io.minelib.launcher.ui.screen.LoginScreen;
import io.minelib.launcher.ui.screen.ModsScreen;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import okhttp3.OkHttpClient;

import java.nio.file.Path;

/**
 * The primary window of the Minelib launcher.
 *
 * <p>Layout: a narrow sidebar on the left (nav rail) + a content {@link StackPane}
 * on the right that swaps between screens.
 *
 * <pre>
 * ┌──────┬────────────────────────────────────┐
 * │ Nav  │                                    │
 * │ rail │      Current screen                │
 * │      │                                    │
 * └──────┴────────────────────────────────────┘
 * </pre>
 */
public final class MainWindow {

    private static final double WINDOW_WIDTH  = 960;
    private static final double WINDOW_HEIGHT = 620;

    private final Stage stage;
    private final AuthService authService;
    private final InstanceService instanceService;
    private final StackPane contentArea = new StackPane();

    public MainWindow(Stage stage) {
        this.stage = stage;

        // Services shared across all screens
        Path launcherDir = Path.of(System.getProperty("user.home"), ".minelib");
        OkHttpClient http = new OkHttpClient();
        this.authService     = new AuthService(launcherDir, http);
        this.instanceService = new InstanceService(launcherDir);
    }

    /** Builds and displays the primary stage. */
    public void show() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setLeft(buildSidebar());
        root.setCenter(contentArea);

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        String css = getClass().getResource("/io/minelib/launcher/launcher.css") != null
                ? getClass().getResource("/io/minelib/launcher/launcher.css").toExternalForm()
                : null;
        if (css != null) scene.getStylesheets().add(css);

        stage.setTitle("Minelib Launcher");
        stage.setMinWidth(720);
        stage.setMinHeight(480);
        stage.setScene(scene);
        stage.show();

        // Navigate to the appropriate first screen
        if (authService.loadSavedProfile() != null) {
            showInstancesScreen();
        } else {
            showLoginScreen();
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /** Shows the sign-in / offline-mode screen. */
    public void showLoginScreen() {
        navigate(new LoginScreen(authService, this::showInstancesScreen));
    }

    /** Shows the instances list screen. */
    public void showInstancesScreen() {
        navigate(new InstancesScreen(authService, instanceService, this::showModsScreen));
    }

    /** Shows the Modrinth mod-browser screen. */
    public void showModsScreen() {
        navigate(new ModsScreen(instanceService));
    }

    private void navigate(Node screen) {
        contentArea.getChildren().setAll(screen);
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private VBox buildSidebar() {
        Button btnInstances = navButton("⊞  Instances", this::showInstancesScreen);
        Button btnMods      = navButton("🧩  Mods",      this::showModsScreen);

        VBox sidebar = new VBox(8, btnInstances, btnMods);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(16, 8, 16, 8));
        sidebar.setPrefWidth(160);
        return sidebar;
    }

    private Button navButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> action.run());
        return btn;
    }
}
