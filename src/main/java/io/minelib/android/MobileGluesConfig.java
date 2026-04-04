package io.minelib.android;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Configuration for the MobileGlues OpenGL ES driver.
 *
 * <p><a href="https://github.com/PojavLauncherTeam/MobileGlues">MobileGlues</a> is an
 * OpenGL → OpenGL ES translation layer used by PojavLauncher and QuestCraft to run
 * Minecraft's OpenGL rendering on Android.  It is distributed as a native shared library
 * ({@code libMobileGlues.so}) per ABI.
 *
 * <p>Instances are created with {@link Builder}:
 * <pre>{@code
 * MobileGluesConfig config = MobileGluesConfig.builder()
 *     .version("1.0.0")
 *     .installDirectory(appFilesDir.resolve("mobileglues"))
 *     .build();
 * }</pre>
 */
public final class MobileGluesConfig {

    /**
     * Default MobileGlues release version.  Update when a newer release is available.
     */
    public static final String DEFAULT_VERSION = "1.0.0";

    /**
     * GitHub Releases download URL template.
     * Parameters: {@code version}, {@code abi} (e.g. {@code arm64-v8a}).
     */
    static final String GITHUB_URL_TEMPLATE =
            "https://github.com/PojavLauncherTeam/MobileGlues/releases/download/v%s/libMobileGlues_%s.so";

    private final String version;
    private final String abi;
    private final Path installDirectory;
    private final String customDownloadUrl;

    private MobileGluesConfig(Builder builder) {
        this.version = builder.version;
        this.abi = builder.abi != null ? builder.abi : detectAbi();
        this.installDirectory = builder.installDirectory;
        this.customDownloadUrl = builder.customDownloadUrl;
    }

    /** Returns the MobileGlues release version string (e.g. {@code "1.0.0"}). */
    public String getVersion() {
        return version;
    }

    /** Returns the Android ABI for which the library is downloaded (e.g. {@code "arm64-v8a"}). */
    public String getAbi() {
        return abi;
    }

    /** Returns the directory where {@code libMobileGlues.so} will be stored. */
    public Path getInstallDirectory() {
        return installDirectory;
    }

    /**
     * Returns the full path to the native library file:
     * {@code <installDirectory>/libMobileGlues.so}.
     */
    public Path getLibraryPath() {
        return installDirectory.resolve("libMobileGlues.so");
    }

    /**
     * Returns the download URL for the native library.
     * If a {@link Builder#customDownloadUrl(String) custom URL} was set it is returned as-is;
     * otherwise the URL is constructed from the GitHub Releases template.
     */
    public String getDownloadUrl() {
        if (customDownloadUrl != null) {
            return customDownloadUrl;
        }
        return String.format(GITHUB_URL_TEMPLATE, version, abi);
    }

    /** Creates a new {@link Builder}. */
    public static Builder builder() {
        return new Builder();
    }

    // -------------------------------------------------------------------------
    // ABI detection
    // -------------------------------------------------------------------------

    /**
     * Detects the primary ABI of the current Android device.
     *
     * <p>Tries {@code android.os.Build.SUPPORTED_ABIS} via reflection first (Android-only),
     * then falls back to mapping {@code os.arch}.
     */
    static String detectAbi() {
        // Try Android's Build.SUPPORTED_ABIS first
        try {
            Class<?> build = Class.forName("android.os.Build");
            String[] abis = (String[]) build.getField("SUPPORTED_ABIS").get(null);
            if (abis != null && abis.length > 0) {
                return abis[0]; // Primary ABI is first
            }
        } catch (ReflectiveOperationException ignored) {
            // Not Android; fall through
        }

        // Map os.arch → ABI name
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.contains("aarch64") || arch.contains("arm64")) return "arm64-v8a";
        if (arch.contains("arm"))                                  return "armeabi-v7a";
        if (arch.contains("x86_64") || arch.contains("amd64"))    return "x86_64";
        if (arch.contains("x86") || arch.contains("i686"))        return "x86";
        return "arm64-v8a"; // Sensible default for modern Android devices
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /** Builder for {@link MobileGluesConfig}. */
    public static final class Builder {

        private String version = DEFAULT_VERSION;
        private String abi;
        private Path installDirectory;
        private String customDownloadUrl;

        private Builder() {}

        /** Sets the MobileGlues release version (default: {@value #DEFAULT_VERSION}). */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * Overrides ABI auto-detection.  Normally the ABI is detected automatically from
         * {@code android.os.Build.SUPPORTED_ABIS}.
         */
        public Builder abi(String abi) {
            this.abi = abi;
            return this;
        }

        /**
         * Sets the directory where the native library file will be stored.
         * This should be an app-writable path, e.g. {@code context.getFilesDir()}.
         */
        public Builder installDirectory(Path installDirectory) {
            this.installDirectory = installDirectory;
            return this;
        }

        /**
         * Overrides the download URL entirely.  Useful when using a custom or pre-built
         * MobileGlues binary.
         */
        public Builder customDownloadUrl(String url) {
            this.customDownloadUrl = url;
            return this;
        }

        public MobileGluesConfig build() {
            if (installDirectory == null) {
                throw new IllegalStateException("installDirectory must be set");
            }
            return new MobileGluesConfig(this);
        }
    }
}
