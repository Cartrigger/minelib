package io.minelib.android;

import io.minelib.launch.GameProcess;
import io.minelib.launch.GameRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Android {@link GameRunner} that loads Minecraft in-process via a custom
 * {@link ClassLoader}, using MobileGlues for OpenGL ES rendering.
 *
 * <p>Unlike the desktop {@link io.minelib.launch.SubprocessGameRunner}, this runner does
 * <em>not</em> spawn a child process.  Instead it:
 * <ol>
 *   <li>Calls {@link MobileGluesDriver#setup()} to download and load the native
 *       OpenGL ES library.</li>
 *   <li>Parses the command list (produced by
 *       {@link io.minelib.launch.LaunchArguments#build()}) to extract JVM system
 *       properties, the classpath, the main-class name, and the game arguments.</li>
 *   <li>Applies all system properties (including MobileGlues LWJGL properties) via
 *       {@link System#setProperty(String, String)}.</li>
 *   <li>Creates a {@link URLClassLoader} from the classpath entries.</li>
 *   <li>Invokes the game's {@code main(String[])} method on a dedicated daemon thread
 *       and returns a {@link GameProcess#ofThread(Thread)} handle.</li>
 * </ol>
 *
 * <h3>ClassLoader note</h3>
 * <p>{@link URLClassLoader} works in JVM-based Android environments such as the one
 * provided by PojavLauncher.  On stock ART without a modified JVM, Java bytecode cannot
 * be loaded directly — in that case, supply a subclass that overrides
 * {@link #createClassLoader(List)} to use a DEX-aware loader.
 *
 * <h3>Example (Android Activity)</h3>
 * <pre>{@code
 * MobileGluesConfig mglConfig = MobileGluesConfig.builder()
 *     .installDirectory(getFilesDir().toPath().resolve("mobileglues"))
 *     .build();
 * MobileGluesDriver driver = new MobileGluesDriver(mglConfig, downloadManager);
 *
 * MineLib minelib = MineLib.builder()
 *     .gameDirectory(getFilesDir().toPath().resolve(".minecraft"))
 *     .gameRunner(new AndroidGameRunner(driver))
 *     .build();
 * }</pre>
 */
public class AndroidGameRunner implements GameRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AndroidGameRunner.class);

    private final MobileGluesDriver mobileGluesDriver;

    public AndroidGameRunner(MobileGluesDriver mobileGluesDriver) {
        this.mobileGluesDriver = mobileGluesDriver;
    }

    @Override
    public GameProcess run(List<String> command, Path workingDirectory) throws IOException {
        // 1. Set up MobileGlues (download + load native library)
        mobileGluesDriver.setup();

        // 2. Parse the command list
        ParsedCommand parsed = ParsedCommand.from(command);
        if (parsed.getMainClass() == null) {
            throw new IOException("Could not find main class in launch command");
        }

        // 3. Apply JVM system properties from -D flags
        parsed.getSystemProperties().forEach(System::setProperty);

        // 4. Apply MobileGlues LWJGL properties (overrides any conflicting value above)
        mobileGluesDriver.getSystemProperties().forEach(System::setProperty);

        // 5. Set the working directory as the game dir property
        System.setProperty("user.dir", workingDirectory.toAbsolutePath().toString());

        // 6. Build ClassLoader from classpath entries
        ClassLoader classLoader = createClassLoader(parsed.getClasspathEntries());

        // 7. Launch the game on a dedicated thread
        String mainClassName = parsed.getMainClass();
        String[] gameArgs = parsed.getGameArgs().toArray(String[]::new);

        Thread gameThread = new Thread(() -> {
            Thread.currentThread().setContextClassLoader(classLoader);
            try {
                Class<?> mainClass = classLoader.loadClass(mainClassName);
                mainClass.getMethod("main", String[].class).invoke(null, (Object) gameArgs);
            } catch (java.lang.reflect.InvocationTargetException e) {
                LOGGER.error("Minecraft exited with an exception", e.getCause());
            } catch (ReflectiveOperationException e) {
                LOGGER.error("Failed to invoke Minecraft main class '{}'", mainClassName, e);
            }
        }, "minecraft-main");

        gameThread.setDaemon(false);
        gameThread.start();

        LOGGER.info("Minecraft launched in-process on thread '{}'", gameThread.getName());
        return GameProcess.ofThread(gameThread);
    }

    /**
     * Creates the {@link ClassLoader} used to load Minecraft's classes.
     *
     * <p>The default implementation uses {@link URLClassLoader}.  Subclasses may override
     * this method to use a DEX-aware loader on stock Android ART.
     *
     * @param classpathEntries absolute paths to every JAR on the classpath
     * @return a new {@link ClassLoader} that can load classes from those JARs
     * @throws IOException if any URL cannot be constructed
     */
    protected ClassLoader createClassLoader(List<Path> classpathEntries) throws IOException {
        URL[] urls = new URL[classpathEntries.size()];
        for (int i = 0; i < classpathEntries.size(); i++) {
            urls[i] = classpathEntries.get(i).toUri().toURL();
        }
        return new URLClassLoader(urls, getClass().getClassLoader());
    }

    // -------------------------------------------------------------------------
    // Command parser
    // -------------------------------------------------------------------------

    /**
     * Parses a command list of the form:
     * {@code [javaExec, ...jvmArgs, mainClass, ...gameArgs]}
     * into its constituent parts.
     */
    static final class ParsedCommand {

        private final Map<String, String> systemProperties;
        private final List<Path> classpathEntries;
        private final String mainClass;
        private final List<String> gameArgs;

        private ParsedCommand(Map<String, String> systemProperties,
                              List<Path> classpathEntries,
                              String mainClass,
                              List<String> gameArgs) {
            this.systemProperties = systemProperties;
            this.classpathEntries = classpathEntries;
            this.mainClass = mainClass;
            this.gameArgs = gameArgs;
        }

        Map<String, String> getSystemProperties() { return systemProperties; }
        List<Path> getClasspathEntries() { return classpathEntries; }
        String getMainClass() { return mainClass; }
        List<String> getGameArgs() { return gameArgs; }

        /**
         * Parses a full launch command (as produced by
         * {@link io.minelib.launch.LaunchArguments#build()}) into its logical parts.
         *
         * @param command the full launch command (index 0 is the Java executable)
         */
        static ParsedCommand from(List<String> command) {
            Map<String, String> sysProps = new LinkedHashMap<>();
            List<Path> classpath = new ArrayList<>();
            String mainClass = null;
            List<String> gameArgs = new ArrayList<>();

            boolean inGameArgs = false;
            // i = 1 to skip the java executable at index 0
            for (int i = 1; i < command.size(); i++) {
                String arg = command.get(i);

                if (inGameArgs) {
                    gameArgs.add(arg);
                    continue;
                }

                if (arg.startsWith("-D")) {
                    // System property: -Dkey=value
                    String kv = arg.substring(2);
                    int eq = kv.indexOf('=');
                    if (eq >= 0) {
                        sysProps.put(kv.substring(0, eq), kv.substring(eq + 1));
                    }
                } else if (arg.equals("-cp") || arg.equals("--classpath")
                        || arg.equals("-classpath")) {
                    // Next token is the classpath string
                    if (i + 1 < command.size()) {
                        String cp = command.get(++i);
                        for (String entry : cp.split(java.io.File.pathSeparator)) {
                            if (!entry.isBlank()) {
                                classpath.add(Path.of(entry));
                            }
                        }
                    }
                } else if (!arg.startsWith("-")) {
                    // First non-flag argument is the main class
                    mainClass = arg;
                    inGameArgs = true;
                }
                // Flags like -Xms, -Xmx, -XX:... are intentionally skipped:
                // they don't apply when running in-process under ART.
            }

            return new ParsedCommand(sysProps, classpath, mainClass, gameArgs);
        }
    }
}
