package io.minelib.launch;

import io.minelib.library.LibraryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Launches Minecraft: Java Edition using a pluggable {@link GameRunner}.
 *
 * <p>The launcher:
 * <ol>
 *   <li>Builds the full argument list via {@link LaunchArguments}.</li>
 *   <li>Ensures the natives directory exists.</li>
 *   <li>Delegates the actual process / thread start to the {@link GameRunner}.</li>
 * </ol>
 *
 * <p>On <strong>desktop</strong> the default runner is {@link SubprocessGameRunner}, which
 * spawns Minecraft as an OS child process. On <strong>Android</strong> callers must supply
 * a custom {@link GameRunner} that loads Minecraft in-process via a custom
 * {@link ClassLoader} (as PojavLauncher does) and wraps the result with
 * {@link GameProcess#ofThread(Thread)}.
 */
public final class GameLauncher {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameLauncher.class);

    private final LibraryManager libraryManager;
    private final GameRunner gameRunner;

    /**
     * Creates a new {@code GameLauncher}.
     *
     * @param libraryManager the library manager used to build the classpath
     * @param gameRunner     the runner responsible for starting the game process or thread
     */
    public GameLauncher(LibraryManager libraryManager, GameRunner gameRunner) {
        this.libraryManager = libraryManager;
        this.gameRunner = gameRunner;
    }

    /**
     * Launches Minecraft with the given configuration and returns a handle to the running
     * instance.
     *
     * @param config the launch configuration
     * @return a {@link GameProcess} representing the running game
     * @throws IOException if the game cannot be started
     */
    public GameProcess launch(LaunchConfig config) throws IOException {
        LaunchArguments launchArguments = new LaunchArguments(config, libraryManager);
        List<String> args = launchArguments.build();

        // Ensure the natives directory exists (no-op if already present)
        Files.createDirectories(config.getNativesDirectory());

        // Build the full command: java executable + all arguments
        List<String> command = new ArrayList<>();
        command.add(config.getJavaRuntime().getJavaExecutable().toAbsolutePath().toString());
        command.addAll(args);

        LOGGER.info("Launching Minecraft {} with Java {}",
                config.getVersion().getId(), config.getJavaRuntime().getMajorVersion());
        LOGGER.debug("Launch command: {}", command);

        return gameRunner.run(command, config.getGameDirectory());
    }
}
