package io.minelib.launcher.ui.screen;

import io.minelib.launcher.service.AuthService;
import javafx.application.Platform;
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
 * <p>The outer {@link VBox} acts as a full-screen container (class
 * {@code login-container}); an inner card VBox (class {@code login-card}) holds the
 * actual form content and floats in the centre with a drop-shadow.
 *
 * <p>On success the {@code onSuccess} {@link Runnable} is called on the JavaFX
 * application thread, which the caller uses to navigate to the next screen.
 */
public final class LoginScreen extends VBox {

    private final AuthService authService;
    private final Runnable    onSuccess;
    private final Label       codeLabel = new Label();
    private final Label       errLabel  = new Label();
    private final VBox        codeBox;

    public LoginScreen(AuthService authService, Runnable onSuccess) {
        super(0);
        this.authService = authService;
        this.onSuccess   = onSuccess;

        setAlignment(Pos.CENTER);
        setFillWidth(true);
        getStyleClass().add("login-container");

        // ── Code box (hidden until Microsoft sign-in is started) ──────────────
        codeLabel.getStyleClass().add("login-code");
        codeLabel.setWrapText(true);
        codeLabel.setMaxWidth(Double.MAX_VALUE);

        codeBox = new VBox(codeLabel);
        codeBox.getStyleClass().add("login-code-box");
        codeBox.setMaxWidth(Double.MAX_VALUE);
        codeBox.setVisible(false);
        codeBox.setManaged(false);

        // ── Card ──────────────────────────────────────────────────────────────
        VBox card = buildCard();
        card.setMaxWidth(440);

        getChildren().add(card);
    }

    private VBox buildCard() {
        // Title
        Label title = new Label("Minelib Launcher");
        title.getStyleClass().add("login-title");
        title.setMaxWidth(Double.MAX_VALUE);

        Label subtitle = new Label("Sign in or play offline to get started");
        subtitle.getStyleClass().add("login-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(Double.MAX_VALUE);

        // Microsoft sign-in button
        Button btnMicrosoft = new Button("Sign in with Microsoft");
        btnMicrosoft.getStyleClass().add("btn-accent");
        btnMicrosoft.setMaxWidth(Double.MAX_VALUE);
        btnMicrosoft.setOnAction(e -> startMicrosoftSignIn(btnMicrosoft));

        // Divider
        Label sep = new Label("─── or play offline ───");
        sep.getStyleClass().add("login-divider");
        sep.setMaxWidth(Double.MAX_VALUE);

        // Offline username
        TextField tfUsername = new TextField();
        tfUsername.setPromptText("Username (e.g. Steve)");
        tfUsername.setMaxWidth(Double.MAX_VALUE);
        tfUsername.getStyleClass().add("text-field");

        errLabel.getStyleClass().add("label-error");

        Button btnOffline = new Button("Play Offline");
        btnOffline.getStyleClass().add("btn-secondary");
        btnOffline.setMaxWidth(Double.MAX_VALUE);
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

        VBox card = new VBox(16, title, subtitle, btnMicrosoft, codeBox, sep,
                tfUsername, errLabel, btnOffline);
        card.getStyleClass().add("login-card");
        return card;
    }

    // ── Microsoft device-code flow ────────────────────────────────────────────

    private void startMicrosoftSignIn(Button btn) {
        btn.setDisable(true);
        btn.setText("Waiting for browser…");

        codeLabel.setText("Starting sign-in flow…");
        codeBox.setVisible(true);
        codeBox.setManaged(true);

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
