package io.minelib.launch;

import io.minelib.auth.AuthProvider;
import io.minelib.runtime.JavaRuntime;
import io.minelib.version.VersionInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds all the configuration needed to launch a specific Minecraft version.
 *
 * <p>Instances are created with {@link Builder} and passed to
 * {@link GameLauncher#launch(LaunchConfig)}.
 */
public final class LaunchConfig {

    private final VersionInfo version;
    private final AuthProvider authProvider;
    private final JavaRuntime javaRuntime;
    private final Path gameDirectory;
    private final Path assetsDirectory;
    private final Path librariesDirectory;
    private final Path nativesDirectory;
    private final int minMemoryMb;
    private final int maxMemoryMb;
    private final List<String> extraJvmArgs;
    private final List<String> extraGameArgs;
    private final int windowWidth;
    private final int windowHeight;

    private LaunchConfig(Builder builder) {
        this.version = builder.version;
        this.authProvider = builder.authProvider;
        this.javaRuntime = builder.javaRuntime;
        this.gameDirectory = builder.gameDirectory;
        this.assetsDirectory = builder.assetsDirectory != null
                ? builder.assetsDirectory
                : builder.gameDirectory.resolve("assets");
        this.librariesDirectory = builder.librariesDirectory != null
                ? builder.librariesDirectory
                : builder.gameDirectory.resolve("libraries");
        this.nativesDirectory = builder.nativesDirectory != null
                ? builder.nativesDirectory
                : builder.gameDirectory.resolve("versions")
                        .resolve(version.getId())
                        .resolve("natives");
        this.minMemoryMb = builder.minMemoryMb;
        this.maxMemoryMb = builder.maxMemoryMb;
        this.extraJvmArgs = List.copyOf(builder.extraJvmArgs);
        this.extraGameArgs = List.copyOf(builder.extraGameArgs);
        this.windowWidth = builder.windowWidth;
        this.windowHeight = builder.windowHeight;
    }

    public VersionInfo getVersion() { return version; }
    public AuthProvider getAuthProvider() { return authProvider; }
    public JavaRuntime getJavaRuntime() { return javaRuntime; }
    public Path getGameDirectory() { return gameDirectory; }
    public Path getAssetsDirectory() { return assetsDirectory; }
    public Path getLibrariesDirectory() { return librariesDirectory; }
    public Path getNativesDirectory() { return nativesDirectory; }
    public int getMinMemoryMb() { return minMemoryMb; }
    public int getMaxMemoryMb() { return maxMemoryMb; }
    public List<String> getExtraJvmArgs() { return extraJvmArgs; }
    public List<String> getExtraGameArgs() { return extraGameArgs; }
    public int getWindowWidth() { return windowWidth; }
    public int getWindowHeight() { return windowHeight; }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link LaunchConfig}. */
    public static final class Builder {

        private VersionInfo version;
        private AuthProvider authProvider;
        private JavaRuntime javaRuntime;
        private Path gameDirectory;
        private Path assetsDirectory;
        private Path librariesDirectory;
        private Path nativesDirectory;
        private int minMemoryMb = 512;
        private int maxMemoryMb = 2048;
        private final List<String> extraJvmArgs = new ArrayList<>();
        private final List<String> extraGameArgs = new ArrayList<>();
        private int windowWidth = 854;
        private int windowHeight = 480;

        private Builder() {}

        public Builder version(VersionInfo version) { this.version = version; return this; }
        public Builder authProvider(AuthProvider authProvider) { this.authProvider = authProvider; return this; }
        public Builder javaRuntime(JavaRuntime javaRuntime) { this.javaRuntime = javaRuntime; return this; }

        public Builder gameDirectory(Path gameDirectory) { this.gameDirectory = gameDirectory; return this; }
        public Builder assetsDirectory(Path assetsDirectory) { this.assetsDirectory = assetsDirectory; return this; }
        public Builder librariesDirectory(Path librariesDirectory) { this.librariesDirectory = librariesDirectory; return this; }
        public Builder nativesDirectory(Path nativesDirectory) { this.nativesDirectory = nativesDirectory; return this; }

        /** Sets the minimum heap size in megabytes (default: 512). */
        public Builder minMemoryMb(int minMemoryMb) { this.minMemoryMb = minMemoryMb; return this; }
        /** Sets the maximum heap size in megabytes (default: 2048). */
        public Builder maxMemoryMb(int maxMemoryMb) { this.maxMemoryMb = maxMemoryMb; return this; }

        /** Appends additional JVM arguments. */
        public Builder extraJvmArgs(List<String> args) { this.extraJvmArgs.addAll(args); return this; }
        /** Appends additional game arguments. */
        public Builder extraGameArgs(List<String> args) { this.extraGameArgs.addAll(args); return this; }

        /** Sets the initial game window width in pixels (default: 854). */
        public Builder windowWidth(int windowWidth) { this.windowWidth = windowWidth; return this; }
        /** Sets the initial game window height in pixels (default: 480). */
        public Builder windowHeight(int windowHeight) { this.windowHeight = windowHeight; return this; }

        public LaunchConfig build() {
            if (version == null) throw new IllegalStateException("version must be set");
            if (authProvider == null) throw new IllegalStateException("authProvider must be set");
            if (javaRuntime == null) throw new IllegalStateException("javaRuntime must be set");
            if (gameDirectory == null) throw new IllegalStateException("gameDirectory must be set");
            if (maxMemoryMb < minMemoryMb) {
                throw new IllegalStateException("maxMemoryMb must be >= minMemoryMb");
            }
            return new LaunchConfig(this);
        }
    }
}
