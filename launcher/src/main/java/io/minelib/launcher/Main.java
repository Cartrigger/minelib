package io.minelib.launcher;

import javafx.application.Application;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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
        try {
            Application.launch(LauncherApp.class, args);
        } catch (Throwable t) {
            // On Windows the native launcher has no console window; write a crash log
            // so the user can find it in ~/.minelib/launcher-crash.log.
            writeCrashLog(t);
            throw t instanceof RuntimeException re ? re : new RuntimeException(t);
        }
    }

    private static void writeCrashLog(Throwable t) {
        try {
            Path logDir = Path.of(System.getProperty("user.home"), ".minelib");
            Files.createDirectories(logDir);
            Path logFile = logDir.resolve("launcher-crash.log");
            try (PrintWriter pw = new PrintWriter(
                    Files.newBufferedWriter(logFile, StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING))) {
                t.printStackTrace(pw);
            }
        } catch (Exception ignored) {
            // If we can't write the log, there's nothing more we can do
        }
    }
}
