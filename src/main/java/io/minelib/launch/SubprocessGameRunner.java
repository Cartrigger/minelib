package io.minelib.launch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Desktop {@link GameRunner} implementation that launches Minecraft as an OS child process.
 *
 * <p>This is the default runner on Windows, macOS, and Linux. It delegates to
 * {@link ProcessBuilder}, inheriting the launcher's standard I/O streams so that game
 * output is visible in the launcher's console.
 *
 * <p>This implementation is <strong>not suitable for Android</strong>, where
 * {@link ProcessBuilder#start()} cannot be used to spawn a new JVM process. Android callers
 * should provide their own {@link GameRunner} that loads Minecraft in-process via a custom
 * {@link ClassLoader}, as PojavLauncher does.
 */
public final class SubprocessGameRunner implements GameRunner {

    @Override
    public GameProcess run(List<String> command, Path workingDirectory) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDirectory.toFile());
        pb.inheritIO();
        return GameProcess.ofProcess(pb.start());
    }
}
