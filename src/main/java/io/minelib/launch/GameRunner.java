package io.minelib.launch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Strategy interface for starting a Minecraft instance.
 *
 * <p>minelib ships with two implementations:
 * <ul>
 *   <li>{@link SubprocessGameRunner} — for <strong>desktop</strong> (Windows, macOS, Linux).
 *       Spawns Minecraft as an OS child process via {@link ProcessBuilder}.</li>
 *   <li>Android callers should implement this interface themselves, loading Minecraft's
 *       {@code main} class via a custom {@link ClassLoader} on a background thread and
 *       wrapping the thread with {@link GameProcess#ofThread(Thread)}.  This matches the
 *       approach used by PojavLauncher.</li>
 * </ul>
 *
 * <p>Implementations are passed to {@link io.minelib.MineLib.Builder#gameRunner(GameRunner)}
 * so that the rest of the library remains fully platform-agnostic.
 */
@FunctionalInterface
public interface GameRunner {

    /**
     * Starts the game and returns a handle to the running instance.
     *
     * @param command          the full command line: Java executable followed by all JVM and
     *                         game arguments, as produced by {@link LaunchArguments#build()}
     * @param workingDirectory the game's working directory (the {@code .minecraft} folder)
     * @return a {@link GameProcess} representing the running game
     * @throws IOException if the game cannot be started
     */
    GameProcess run(List<String> command, Path workingDirectory) throws IOException;
}
