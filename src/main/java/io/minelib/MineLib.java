package io.minelib;

import io.minelib.asset.AssetManager;
import io.minelib.auth.AuthProvider;
import io.minelib.download.DownloadManager;
import io.minelib.launch.GameLauncher;
import io.minelib.launch.LaunchConfig;
import io.minelib.library.LibraryManager;
import io.minelib.runtime.JavaRuntimeManager;
import io.minelib.version.VersionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Main entry point for the minelib library.
 *
 * <p>This class provides a convenient facade over the individual minelib components,
 * allowing developers to quickly bootstrap a working Minecraft launcher.
 *
 * <pre>{@code
 * MineLib minelib = MineLib.builder()
 *     .gameDirectory(Path.of("/path/to/minecraft"))
 *     .build();
 *
 * minelib.getVersionManager().downloadVersion("1.21");
 * minelib.getLauncher().launch(config);
 * }</pre>
 */
public class MineLib {

    private static final Logger LOGGER = LoggerFactory.getLogger(MineLib.class);

    private final Path gameDirectory;
    private final DownloadManager downloadManager;
    private final VersionManager versionManager;
    private final AssetManager assetManager;
    private final LibraryManager libraryManager;
    private final JavaRuntimeManager javaRuntimeManager;
    private final GameLauncher gameLauncher;

    private MineLib(Builder builder) {
        this.gameDirectory = builder.gameDirectory;
        this.downloadManager = new DownloadManager(builder.maxConcurrentDownloads);
        this.versionManager = new VersionManager(gameDirectory, downloadManager);
        this.assetManager = new AssetManager(gameDirectory, downloadManager);
        this.libraryManager = new LibraryManager(gameDirectory, downloadManager);
        this.javaRuntimeManager = new JavaRuntimeManager(gameDirectory, downloadManager);
        this.gameLauncher = new GameLauncher(libraryManager, javaRuntimeManager);
        LOGGER.info("MineLib initialized with game directory: {}", gameDirectory);
    }

    /** Returns the game (`.minecraft`) directory used by this instance. */
    public Path getGameDirectory() {
        return gameDirectory;
    }

    /** Returns the download manager used for all file downloads. */
    public DownloadManager getDownloadManager() {
        return downloadManager;
    }

    /** Returns the version manager for listing and installing Minecraft versions. */
    public VersionManager getVersionManager() {
        return versionManager;
    }

    /** Returns the asset manager for downloading game assets. */
    public AssetManager getAssetManager() {
        return assetManager;
    }

    /** Returns the library manager for downloading native and classpath libraries. */
    public LibraryManager getLibraryManager() {
        return libraryManager;
    }

    /** Returns the Java runtime manager for provisioning the correct JRE. */
    public JavaRuntimeManager getJavaRuntimeManager() {
        return javaRuntimeManager;
    }

    /** Returns the game launcher used to start Minecraft. */
    public GameLauncher getLauncher() {
        return gameLauncher;
    }

    /**
     * Convenience method that downloads all files required to launch a given version and then
     * starts the game.
     *
     * @param versionId the Minecraft version to install and launch (e.g. {@code "1.21"})
     * @param authProvider an authenticated {@link AuthProvider} supplying the player's session
     * @return the {@link Process} for the running Minecraft instance
     * @throws Exception if any installation or launch step fails
     */
    public Process installAndLaunch(String versionId, AuthProvider authProvider) throws Exception {
        LOGGER.info("Installing version {}", versionId);
        var version = versionManager.downloadVersion(versionId);
        assetManager.downloadAssets(version);
        libraryManager.downloadLibraries(version);
        var runtime = javaRuntimeManager.provisionRuntime(version.getJavaVersion());

        var config = LaunchConfig.builder()
                .version(version)
                .authProvider(authProvider)
                .javaRuntime(runtime)
                .gameDirectory(gameDirectory)
                .build();

        LOGGER.info("Launching Minecraft {}", versionId);
        return gameLauncher.launch(config);
    }

    /** Creates a new {@link Builder} for constructing a {@link MineLib} instance. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link MineLib}. */
    public static final class Builder {

        private Path gameDirectory = Path.of(System.getProperty("user.home"), ".minecraft");
        private int maxConcurrentDownloads = 4;

        private Builder() {}

        /**
         * Sets the game directory (usually {@code ~/.minecraft}).
         *
         * @param gameDirectory path to the game directory
         * @return this builder
         */
        public Builder gameDirectory(Path gameDirectory) {
            this.gameDirectory = gameDirectory;
            return this;
        }

        /**
         * Sets the maximum number of concurrent downloads.
         *
         * @param maxConcurrentDownloads number of parallel downloads
         * @return this builder
         */
        public Builder maxConcurrentDownloads(int maxConcurrentDownloads) {
            if (maxConcurrentDownloads < 1) {
                throw new IllegalArgumentException("maxConcurrentDownloads must be >= 1");
            }
            this.maxConcurrentDownloads = maxConcurrentDownloads;
            return this;
        }

        /** Builds the {@link MineLib} instance. */
        public MineLib build() {
            if (gameDirectory == null) {
                throw new IllegalStateException("gameDirectory must be set");
            }
            return new MineLib(this);
        }
    }
}
