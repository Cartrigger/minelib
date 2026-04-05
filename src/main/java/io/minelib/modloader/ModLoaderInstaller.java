package io.minelib.modloader;

import io.minelib.download.DownloadManager;
import io.minelib.version.VersionInfo;
import io.minelib.version.VersionManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Installs a specific mod loader into a Minecraft game directory.
 *
 * <p>Use {@link #forLoader(ModLoader, DownloadManager, VersionManager)} to obtain an
 * implementation, or call {@link io.minelib.MineLib#getModLoaderInstaller(ModLoader)} for
 * a pre-configured instance.
 *
 * <p>The typical usage pattern is:
 * <pre>{@code
 * ModLoaderInstaller installer = minelib.getModLoaderInstaller(ModLoader.FABRIC);
 *
 * // List available versions
 * List<ModLoaderVersion> versions = installer.listVersions("1.21.4");
 * ModLoaderVersion latest = versions.get(0);
 *
 * // Install and get the launch-ready VersionInfo
 * VersionInfo profile = installer.install(latest, minelib.getGameDirectory());
 * }</pre>
 */
public interface ModLoaderInstaller {

    /**
     * Lists all available versions of this mod loader for the given Minecraft version,
     * ordered from newest to oldest.
     *
     * @param minecraftVersion the Minecraft version to query (e.g. {@code "1.21.4"})
     * @return list of available loader versions
     * @throws IOException if the version list cannot be fetched
     */
    List<ModLoaderVersion> listVersions(String minecraftVersion) throws IOException;

    /**
     * Installs the mod loader described by {@code version} into {@code gameDirectory} and
     * returns a {@link VersionInfo} that can be passed directly to
     * {@link io.minelib.launch.GameLauncher#launch}.
     *
     * <p>For Fabric, this downloads the loader profile and its libraries.
     * For Forge and NeoForge, this downloads and runs the official installer JAR.
     *
     * @param version       the mod loader version to install
     * @param gameDirectory the Minecraft game directory (e.g. {@code ~/.minecraft})
     * @return a launch-ready {@link VersionInfo} for the installed profile
     * @throws IOException          if any file cannot be downloaded or written
     * @throws InterruptedException if the installer subprocess is interrupted
     */
    VersionInfo install(ModLoaderVersion version, Path gameDirectory)
            throws IOException, InterruptedException;

    /**
     * Returns the correct {@link ModLoaderInstaller} implementation for the given
     * {@link ModLoader}.
     *
     * @param loader          the mod loader
     * @param downloadManager the download manager to use for file downloads
     * @param versionManager  the version manager for resolving vanilla profiles
     * @return the installer
     */
    static ModLoaderInstaller forLoader(ModLoader loader, DownloadManager downloadManager,
                                        VersionManager versionManager) {
        return switch (loader) {
            case FABRIC   -> new FabricInstaller(downloadManager, versionManager);
            case FORGE    -> new ForgeInstaller(downloadManager, versionManager);
            case NEOFORGE -> new NeoForgeInstaller(downloadManager, versionManager);
        };
    }
}
