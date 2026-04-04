package io.minelib.launch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
 *
 * <h2>Desktop VR (OpenXR / SteamVR)</h2>
 * <p>To launch Minecraft with OpenXR on desktop, pass the environment variables produced by
 * {@link io.minelib.vr.OpenXrSetup#getEnvironmentVariables()} to the two-argument constructor:
 * <pre>{@code
 * OpenXrConfig xrConfig = OpenXrConfig.builder().build();
 * OpenXrSetup  xrSetup  = new OpenXrSetup(xrConfig);
 * GameRunner   runner   = new SubprocessGameRunner(xrSetup.getEnvironmentVariables());
 * }</pre>
 */
public final class SubprocessGameRunner implements GameRunner {

    private final Map<String, String> extraEnvironment;

    /**
     * Creates a runner with no extra environment variables.
     * This is the standard constructor for non-VR desktop launches.
     */
    public SubprocessGameRunner() {
        this.extraEnvironment = Map.of();
    }

    /**
     * Creates a runner that merges the given variables into the child process environment
     * before starting Minecraft.
     *
     * <p>Use this overload for desktop VR launches where OpenXR environment variables such as
     * {@code XR_RUNTIME_JSON} must be visible to the native OpenXR loader inside the child JVM.
     *
     * @param extraEnvironment additional environment variables to inject (must not be null)
     */
    public SubprocessGameRunner(Map<String, String> extraEnvironment) {
        if (extraEnvironment == null) throw new NullPointerException("extraEnvironment must not be null");
        this.extraEnvironment = Map.copyOf(extraEnvironment);
    }

    @Override
    public GameProcess run(List<String> command, Path workingDirectory) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDirectory.toFile());
        pb.inheritIO();
        if (!extraEnvironment.isEmpty()) {
            pb.environment().putAll(extraEnvironment);
        }
        return GameProcess.ofProcess(pb.start());
    }
}
