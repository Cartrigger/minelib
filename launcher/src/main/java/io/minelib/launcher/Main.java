package io.minelib.launcher;

import javafx.application.Application;

/**
 * Thin entry-point wrapper for the Minelib launcher.
 *
 * <p>JavaFX requires that the class passed to {@link Application#launch} be on the
 * module-path (or accessible without module restriction). A non-JavaFX {@code main}
 * in this class avoids the common "JavaFX runtime components are missing" error when
 * the fat JAR is executed with a plain {@code java -jar}.
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        Application.launch(LauncherApp.class, args);
    }
}
