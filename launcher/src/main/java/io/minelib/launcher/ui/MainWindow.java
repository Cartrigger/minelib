package io.minelib.launcher.ui;

import io.minelib.auth.PlayerProfile;
import io.minelib.launcher.service.AuthService;
import io.minelib.launcher.service.InstanceService;
import io.minelib.launcher.ui.screen.InstancesScreen;
import io.minelib.launcher.ui.screen.LoginScreen;
import io.minelib.launcher.ui.screen.ModsScreen;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import okhttp3.OkHttpClient;

import java.nio.file.Path;

/**
 * The primary window of the Minelib launcher.
 *
 * <p>Layout: a themed sidebar on the left (logo + nav + user panel) and a
 * content {@link StackPane} on the right that swaps between screens.
 *
 * <pre>
 * ┌──────────────┬────────────────────────────────┐
 * │  Minelib     │                                │
 * │  ──────────  │      Current screen            │
 * │  ⊞ Instances │                                │
 * │  🧩 Mods     │                                │
 * │              │                                │
 * │  [username]  │                                │
 * └──────────────┴────────────────────────────────┘
 * </pre>
 */
public final class MainWindow {

    private static final double WINDOW_WIDTH  = 1020;
    private static final double WINDOW_HEIGHT = 660;

    private final Stage stage;
    private final AuthService authService;
    private final InstanceService instanceService;
    private final StackPane contentArea = new StackPane();

    // Nav buttons kept as fields so we can toggle the active CSS class
    private Button btnInstances;
    private Button btnMods;

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
        var cssUrl = getClass().getResource("/io/minelib/launcher/launcher.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());

        stage.setTitle("Minelib Launcher");
        stage.setMinWidth(760);
        stage.setMinHeight(520);
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
        setActiveNav(null);
        navigate(new LoginScreen(authService, this::showInstancesScreen));
    }

    /** Shows the instances list screen. */
    public void showInstancesScreen() {
        setActiveNav(btnInstances);
        navigate(new InstancesScreen(authService, instanceService, this::showModsScreen));
    }

    /** Shows the Modrinth mod-browser screen. */
    public void showModsScreen() {
        setActiveNav(btnMods);
        navigate(new ModsScreen(instanceService));
    }

    private void navigate(Node screen) {
        contentArea.getChildren().setAll(screen);
    }

    /**
     * Highlights {@code active} in the sidebar and removes the highlight from
     * all other nav buttons. Pass {@code null} to clear all highlights (e.g.
     * when showing the login screen).
     */
    private void setActiveNav(Button active) {
        for (Button btn : new Button[]{btnInstances, btnMods}) {
            if (btn == null) continue;
            btn.getStyleClass().remove("active");
            if (btn == active) btn.getStyleClass().add("active");
        }
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private VBox buildSidebar() {
        // ── Logo ─────────────────────────────────────────────────────────────
        Label logo = new Label("Minelib");
        logo.getStyleClass().add("sidebar-logo");
        logo.setMaxWidth(Double.MAX_VALUE);

        // ── Section header ────────────────────────────────────────────────────
        Label section = new Label("GAME");
        section.getStyleClass().add("sidebar-section");

        // ── Nav buttons ───────────────────────────────────────────────────────
        btnInstances = navButton("⊞  Instances", this::showInstancesScreen);
        btnMods      = navButton("🧩  Mods",      this::showModsScreen);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // ── User panel ────────────────────────────────────────────────────────
        PlayerProfile profile = authService.loadSavedProfile();
        String username = (profile != null) ? profile.getUsername() : "Not signed in";
        String accountType = (profile != null) ? "Microsoft account" : "—";

        Label usernameLabel = new Label(username);
        usernameLabel.getStyleClass().add("sidebar-username");

        Label subLabel = new Label(accountType);
        subLabel.getStyleClass().add("sidebar-user-sub");

        VBox userPane = new VBox(2, usernameLabel, subLabel);
        userPane.getStyleClass().add("sidebar-user-pane");
        userPane.setMaxWidth(Double.MAX_VALUE);

        // ── Assemble ──────────────────────────────────────────────────────────
        VBox sidebar = new VBox(logo, section, btnInstances, btnMods, spacer, userPane);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(220);
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return sidebar;
    }

    private Button navButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.getStyleClass().add("sidebar-nav-btn");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> action.run());
        return btn;
    }
}
