package io.minelib.launcher.ui.component;

import io.minelib.launcher.model.Instance;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.function.Consumer;

/**
 * A card component that displays a single {@link Instance}.
 *
 * <p>The card shows a coloured icon (derived from the version hash), the instance
 * name, Minecraft version, and mod loader badge.  A "▶ Play" button launches the
 * instance; a right-click context menu exposes rename / delete actions.
 */
public final class InstanceCard extends VBox {

    /**
     * Creates an {@code InstanceCard}.
     *
     * @param instance      the instance to display
     * @param onPlay        callback invoked when the play button is clicked
     * @param onDelete      callback invoked when "Delete" is chosen in the context menu
     */
    public InstanceCard(Instance instance, Consumer<Instance> onPlay,
                        Consumer<Instance> onDelete) {
        super(10);
        getStyleClass().add("instance-card");

        // ── Icon ──────────────────────────────────────────────────────────────
        Rectangle icon = new Rectangle(48, 48);
        icon.setArcWidth(10);
        icon.setArcHeight(10);
        icon.setFill(versionColor(instance.getMinecraftVersion()));
        icon.getStyleClass().add("instance-icon");

        Label versionOnIcon = new Label(shortVersion(instance.getMinecraftVersion()));
        versionOnIcon.setStyle("-fx-text-fill: rgba(255,255,255,0.9); -fx-font-size: 11px;"
                + " -fx-font-weight: bold;");

        VBox iconBox = new VBox(icon, versionOnIcon);
        iconBox.setAlignment(Pos.CENTER);
        iconBox.setSpacing(4);

        // ── Labels ────────────────────────────────────────────────────────────
        Label nameLabel = new Label(instance.getName());
        nameLabel.getStyleClass().add("instance-name");
        nameLabel.setWrapText(true);

        Label versionLabel = new Label("Minecraft " + instance.getMinecraftVersion());
        versionLabel.getStyleClass().add("instance-version");

        Label loaderBadge = new Label(instance.getModLoader().name());
        loaderBadge.getStyleClass().add("instance-loader-badge");

        VBox labels = new VBox(4, nameLabel, versionLabel, loaderBadge);

        // ── Play button ───────────────────────────────────────────────────────
        Button playBtn = new Button("▶  Play");
        playBtn.getStyleClass().add("btn-primary");
        playBtn.setMaxWidth(Double.MAX_VALUE);
        playBtn.setOnAction(e -> onPlay.accept(instance));

        // ── Layout ────────────────────────────────────────────────────────────
        HBox header = new HBox(12, iconBox, labels);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(labels, Priority.ALWAYS);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(header, spacer, playBtn);

        // ── Context menu ──────────────────────────────────────────────────────
        ContextMenu ctx = new ContextMenu();
        MenuItem deleteItem = new MenuItem("🗑  Delete");
        deleteItem.setStyle("-fx-text-fill: #f78166;");
        deleteItem.setOnAction(e -> onDelete.accept(instance));
        ctx.getItems().add(deleteItem);
        setOnContextMenuRequested(e -> ctx.show(this, e.getScreenX(), e.getScreenY()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Deterministically maps a Minecraft version string to a fill colour so each
     * version family has a consistent look.
     */
    private static Color versionColor(String version) {
        // Hash the version string to a hue in 0–360
        int hash = version.hashCode();
        double hue = ((hash & 0x7FFF_FFFF) % 360);
        return Color.hsb(hue, 0.55, 0.55);
    }

    /** Returns a short version label for display on the icon (e.g. "1.21"). */
    private static String shortVersion(String version) {
        if (version == null) return "?";
        String[] parts = version.split("\\.");
        if (parts.length >= 2) return parts[0] + "." + parts[1];
        return version;
    }
}
