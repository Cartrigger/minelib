package io.minelib.launcher.ui.screen;

import io.minelib.launcher.service.AuthService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Sign-in screen — offers two paths:
 *
 * <ul>
 *   <li><strong>Microsoft</strong> — starts the OAuth device-code flow and polls
 *       until the user completes sign-in in a browser.</li>
 *   <li><strong>Offline</strong> — creates a local profile with a chosen username
 *       and no Microsoft account.</li>
 * </ul>
 *
 * <p>On success the {@code onSuccess} {@link Runnable} is called on the JavaFX
 * application thread, which the caller uses to navigate to the next screen.
 */
public final class LoginScreen extends VBox {

    private final AuthService authService;
    private final Runnable    onSuccess;

    public LoginScreen(AuthService authService, Runnable onSuccess) {
        super(16);
        this.authService = authService;
        this.onSuccess   = onSuccess;

        setAlignment(Pos.CENTER);
        setPadding(new Insets(40));
        getStyleClass().add("login-screen");

        // ── Title ─────────────────────────────────────────────────────────────
        Label title = new Label("Minelib Launcher");
        title.getStyleClass().add("login-title");

        Label subtitle = new Label("Sign in or play offline to get started");
        subtitle.getStyleClass().add("label-muted");

        // ── Microsoft sign-in ─────────────────────────────────────────────────
        Button btnMicrosoft = new Button("Sign in with Microsoft");
        btnMicrosoft.getStyleClass().add("btn-primary");
        btnMicrosoft.setMaxWidth(320);
        btnMicrosoft.setOnAction(e -> startMicrosoftSignIn(btnMicrosoft));

        Label codeLabel = new Label();
        codeLabel.getStyleClass().add("device-code-label");
        codeLabel.setWrapText(true);
        codeLabel.setMaxWidth(320);

        // ── Separator ─────────────────────────────────────────────────────────
        Label sep = new Label("─── or play offline ───");
        sep.getStyleClass().add("label-muted");
        sep.setStyle("-fx-font-size: 11px;");

        // ── Offline ───────────────────────────────────────────────────────────
        TextField tfUsername = new TextField();
        tfUsername.setPromptText("Username (e.g. Steve)");
        tfUsername.setMaxWidth(320);
        tfUsername.getStyleClass().add("text-field");

        Label errLabel = new Label();
        errLabel.getStyleClass().add("error-label");

        Button btnOffline = new Button("Play Offline");
        btnOffline.getStyleClass().add("btn-secondary");
        btnOffline.setMaxWidth(320);
        btnOffline.setOnAction(e -> {
            String username = tfUsername.getText().trim();
            if (username.isEmpty()) {
                errLabel.setText("Please enter a username.");
                return;
            }
            try {
                authService.createOfflineProfile(username);
                onSuccess.run();
            } catch (IllegalArgumentException ex) {
                errLabel.setText(ex.getMessage());
            }
        });

        getChildren().addAll(title, subtitle, btnMicrosoft, codeLabel, sep,
                tfUsername, errLabel, btnOffline);
    }

    // ── Microsoft device-code flow ────────────────────────────────────────────

    private void startMicrosoftSignIn(Button btn) {
        btn.setDisable(true);
        btn.setText("Waiting for browser…");

        Label codeLabel = (Label) getChildren().get(3);
        codeLabel.setText("Starting sign-in flow…");

        authService.signIn(message -> Platform.runLater(() -> codeLabel.setText(message)))
                .thenAccept(profile -> Platform.runLater(onSuccess))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        btn.setDisable(false);
                        btn.setText("Sign in with Microsoft");
                        codeLabel.setText("Sign-in failed: " + ex.getMessage());
                    });
                    return null;
                });
    }
}
