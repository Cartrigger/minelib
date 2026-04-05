package io.minelib.launcher;

import io.minelib.launcher.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * JavaFX {@link Application} entry-point for the Minelib launcher.
 *
 * <p>{@link Main} delegates here so that the JavaFX toolkit is initialised
 * correctly even when the launcher is run from a fat JAR on the unnamed module
 * path (avoids the "JavaFX runtime components are missing" error).
 */
public final class LauncherApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        new MainWindow(primaryStage).show();
    }
}
