package io.minelib.android;

import io.minelib.download.DownloadManager;
import io.minelib.download.DownloadTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Downloads, loads, and configures the
 * <a href="https://github.com/PojavLauncherTeam/MobileGlues">MobileGlues</a> OpenGL ES
 * driver on Android.
 *
 * <p>MobileGlues is a native shared library ({@code libMobileGlues.so}) that translates
 * desktop OpenGL calls to OpenGL ES 3.x, allowing Minecraft's renderer to run on Android
 * hardware.  It is the Android equivalent of the desktop LWJGL natives.
 *
 * <p>Usage:
 * <pre>{@code
 * MobileGluesConfig config = MobileGluesConfig.builder()
 *     .installDirectory(appFilesDir.resolve("mobileglues"))
 *     .build();
 *
 * MobileGluesDriver driver = new MobileGluesDriver(config, downloadManager);
 * driver.setup();   // Downloads and loads the .so if not already present
 *
 * // Pass the driver to AndroidGameRunner:
 * AndroidGameRunner runner = new AndroidGameRunner(driver);
 * }</pre>
 */
public final class MobileGluesDriver {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobileGluesDriver.class);

    private final MobileGluesConfig config;
    private final DownloadManager downloadManager;

    private volatile boolean loaded = false;

    public MobileGluesDriver(MobileGluesConfig config, DownloadManager downloadManager) {
        this.config = config;
        this.downloadManager = downloadManager;
    }

    /**
     * Ensures the MobileGlues native library is downloaded (if necessary) and loaded into
     * the current process via {@link System#load(String)}.
     *
     * <p>This method is idempotent — calling it multiple times is safe.
     *
     * @throws IOException if the library cannot be downloaded or read
     */
    public synchronized void setup() throws IOException {
        if (loaded) {
            return;
        }

        java.nio.file.Path libPath = config.getLibraryPath();

        if (!Files.exists(libPath)) {
            LOGGER.info("Downloading MobileGlues {} ({}) to {}",
                    config.getVersion(), config.getAbi(), libPath);
            Files.createDirectories(libPath.getParent());
            downloadManager.download(DownloadTask.builder()
                    .url(config.getDownloadUrl())
                    .destination(libPath)
                    .build());
        } else {
            LOGGER.debug("MobileGlues already present at {}", libPath);
        }

        LOGGER.info("Loading MobileGlues from {}", libPath);
        System.load(libPath.toAbsolutePath().toString());
        loaded = true;
        LOGGER.info("MobileGlues loaded successfully");
    }

    /**
     * Returns the JVM system properties that must be set before loading Minecraft so that
     * LWJGL uses MobileGlues as its OpenGL implementation.
     *
     * <p>These properties are applied by {@link AndroidGameRunner} before invoking the
     * game's {@code main} method.
     *
     * @return an ordered map of property name → value
     * @throws IllegalStateException if {@link #setup()} has not been called yet
     */
    public Map<String, String> getSystemProperties() {
        if (!loaded) {
            throw new IllegalStateException(
                    "MobileGluesDriver.setup() must be called before getSystemProperties()");
        }
        String libPath = config.getLibraryPath().toAbsolutePath().toString();

        // LWJGL uses these properties to locate the OpenGL shared library at runtime.
        Map<String, String> props = new LinkedHashMap<>();
        // Tell LWJGL exactly which OpenGL library to use
        props.put("org.lwjgl.opengl.libname", libPath);
        // Disable LWJGL's own native-library extraction (we manage the path ourselves)
        props.put("org.lwjgl.system.SharedLibraryExtractPath",
                config.getInstallDirectory().toAbsolutePath().toString());
        // Suppress LWJGL debug output by default
        props.put("org.lwjgl.util.Debug", "false");
        return props;
    }

    /** Returns {@code true} if the native library has been loaded into this process. */
    public boolean isLoaded() {
        return loaded;
    }
}
