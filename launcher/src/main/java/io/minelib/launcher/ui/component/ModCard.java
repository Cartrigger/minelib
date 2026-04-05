package io.minelib.launcher.ui.component;

import io.minelib.modrinth.ModrinthProject;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.text.DecimalFormat;
import java.util.function.Consumer;

/**
 * A card component that displays a single Modrinth {@link ModrinthProject}.
 *
 * <p>The card shows the project title, description, download count, and an
 * "Install" button.  Clicking the button invokes the provided {@code onInstall}
 * callback with the project.
 */
public final class ModCard extends HBox {

    /**
     * Creates a {@code ModCard}.
     *
     * @param project    the Modrinth project to display
     * @param onInstall  callback invoked when the "Install" button is clicked
     */
    public ModCard(ModrinthProject project, Consumer<ModrinthProject> onInstall) {
        super(14);
        getStyleClass().add("mod-card");
        setAlignment(Pos.CENTER_LEFT);

        // ── Icon placeholder ──────────────────────────────────────────────────
        Label iconPlaceholder = new Label("📦");
        iconPlaceholder.setStyle("-fx-font-size: 28px; -fx-min-width: 40; -fx-min-height: 40;"
                + " -fx-alignment: CENTER;");

        // ── Text content ──────────────────────────────────────────────────────
        Label titleLabel = new Label(project.getTitle());
        titleLabel.getStyleClass().add("mod-title");
        titleLabel.setWrapText(false);

        String descText = project.getDescription() != null
                ? truncate(project.getDescription(), 110)
                : "";
        Label descLabel = new Label(descText);
        descLabel.getStyleClass().add("mod-description");
        descLabel.setWrapText(true);

        Label downloadsLabel = new Label("⬇  " + formatCount(project.getDownloads()) + " downloads");
        downloadsLabel.getStyleClass().add("mod-downloads");

        // Loaders / game versions hint
        String versionHint = "";
        if (!project.getGameVersions().isEmpty()) {
            int sz = project.getGameVersions().size();
            versionHint = project.getGameVersions().get(0);
            if (sz > 1) versionHint += " +" + (sz - 1) + " more";
        }
        Label versionLabel = new Label(versionHint);
        versionLabel.getStyleClass().add("label-muted");

        VBox textBox = new VBox(4, titleLabel, descLabel, downloadsLabel, versionLabel);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        // ── Install button ────────────────────────────────────────────────────
        Button installBtn = new Button("Install");
        installBtn.getStyleClass().add("btn-accent");
        installBtn.setMinWidth(80);
        installBtn.setOnAction(e -> onInstall.accept(project));

        getChildren().addAll(iconPlaceholder, textBox, installBtn);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    private static String formatCount(long count) {
        if (count >= 1_000_000) return new DecimalFormat("0.0M").format(count / 1_000_000.0);
        if (count >= 1_000)     return new DecimalFormat("0.0k").format(count / 1_000.0);
        return String.valueOf(count);
    }
}
