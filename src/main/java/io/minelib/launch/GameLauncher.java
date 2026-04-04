package io.minelib.launch;

import io.minelib.library.LibraryManager;
import io.minelib.runtime.JavaRuntimeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Launches Minecraft: Java Edition as an OS-level child process.
 *
 * <p>The launcher:
 * <ol>
 *   <li>Builds the full argument list via {@link LaunchArguments}.</li>
 *   <li>Ensures the natives directory exists.</li>
 *   <li>Starts the game process via {@link ProcessBuilder}.</li>
 * </ol>
 *
 * <p>The returned {@link Process} can be monitored or waited on by the calling application.
 * Standard output and standard error are inherited from the parent process by default so
 * that game logs are visible in the launcher's console.
 */
public final class GameLauncher {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameLauncher.class);

    private final LibraryManager libraryManager;
    private final JavaRuntimeManager javaRuntimeManager;

    public GameLauncher(LibraryManager libraryManager, JavaRuntimeManager javaRuntimeManager) {
        this.libraryManager = libraryManager;
        this.javaRuntimeManager = javaRuntimeManager;
    }

    /**
     * Launches Minecraft with the given configuration.
     *
     * @param config the launch configuration
     * @return the running game {@link Process}
     * @throws IOException if the process cannot be started
     */
    public Process launch(LaunchConfig config) throws IOException {
        LaunchArguments launchArguments = new LaunchArguments(config, libraryManager);
        List<String> args = launchArguments.build();

        // Ensure the natives directory exists
        Files.createDirectories(config.getNativesDirectory());

        // Build the full command: java executable + all arguments
        List<String> command = new ArrayList<>();
        command.add(config.getJavaRuntime().getJavaExecutable().toAbsolutePath().toString());
        command.addAll(args);

        LOGGER.info("Launching Minecraft {} with Java {}",
                config.getVersion().getId(), config.getJavaRuntime().getMajorVersion());
        LOGGER.debug("Launch command: {}", command);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(config.getGameDirectory().toFile());
        pb.inheritIO();

        return pb.start();
    }
}
