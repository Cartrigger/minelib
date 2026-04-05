package io.minelib.modloader;

import com.google.gson.Gson;
import io.minelib.download.DownloadManager;
import io.minelib.download.DownloadTask;
import io.minelib.runtime.JavaRuntime;
import io.minelib.runtime.JavaRuntimeManager;
import io.minelib.version.VersionInfo;
import io.minelib.version.VersionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Shared base for {@link ForgeInstaller} and {@link NeoForgeInstaller}.
 *
 * <p>Both Forge and NeoForge are distributed as installer JARs.  The installation
 * process is identical:
 * <ol>
 *   <li>Download the installer JAR.</li>
 *   <li>Run it with {@code --installClient <gameDirectory>} using the correct Java
 *       version.</li>
 *   <li>Find the resulting version JSON that the installer wrote to
 *       {@code <gameDir>/versions/} and parse it as a {@link VersionInfo}.</li>
 * </ol>
 */
abstract class InstallerJarBasedInstaller implements ModLoaderInstaller {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstallerJarBasedInstaller.class);

    protected final DownloadManager downloadManager;
    protected final VersionManager versionManager;
    protected final Gson gson = new Gson();

    InstallerJarBasedInstaller(DownloadManager downloadManager, VersionManager versionManager) {
        this.downloadManager = downloadManager;
        this.versionManager = versionManager;
    }

    /**
     * Downloads the installer JAR for the given version, runs it against
     * {@code gameDirectory}, and returns the resulting {@link VersionInfo}.
     *
     * @param version       the mod loader version to install
     * @param gameDirectory the Minecraft game directory
     * @param installerUrl  the direct download URL of the installer JAR
     * @param expectedVersionId the version directory name the installer will create
     *                          (used to locate the output JSON), or {@code null} to
     *                          auto-detect the newest version directory written by
     *                          the installer
     * @return the installed {@link VersionInfo}
     */
    protected VersionInfo runInstaller(ModLoaderVersion version, Path gameDirectory,
                                       String installerUrl, String expectedVersionId)
            throws IOException, InterruptedException {

        // 1. Download the installer JAR
        Path installerJar = gameDirectory.resolve("installer-" + installerUrl.hashCode() + ".jar");
        LOGGER.info("Downloading {} installer from {}", version.getLoader(), installerUrl);
        downloadManager.download(DownloadTask.builder()
                .url(installerUrl)
                .destination(installerJar)
                .build());

        // 2. Resolve a suitable Java runtime (Forge installers require Java 17+)
        JavaRuntimeManager javaRuntimeManager =
                new JavaRuntimeManager(gameDirectory, downloadManager);
        JavaRuntime runtime = javaRuntimeManager.provisionRuntime(17);

        // 3. Collect existing version directories so we can detect newly created ones
        Path versionsDir = gameDirectory.resolve("versions");
        Files.createDirectories(versionsDir);
        List<Path> existingVersionDirs = listVersionDirs(versionsDir);

        // 4. Run the installer
        List<String> command = new ArrayList<>();
        command.add(runtime.getJavaExecutable().toAbsolutePath().toString());
        command.add("-jar");
        command.add(installerJar.toAbsolutePath().toString());
        command.add("--installClient");
        command.add(gameDirectory.toAbsolutePath().toString());

        LOGGER.info("Running {} installer (this may take a while)", version.getLoader());
        LOGGER.debug("Installer command: {}", command);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(gameDirectory.toFile());
        pb.inheritIO();
        int exitCode = pb.start().waitFor();

        // 5. Clean up the installer JAR
        Files.deleteIfExists(installerJar);

        if (exitCode != 0) {
            throw new IOException(version.getLoader() + " installer exited with code " + exitCode);
        }

        // 6. Locate the version JSON the installer created
        Path versionJson = findNewVersionJson(versionsDir, existingVersionDirs, expectedVersionId);
        if (versionJson == null) {
            throw new IOException("Could not find a version JSON created by the "
                    + version.getLoader() + " installer");
        }
        LOGGER.info("{} installed — version profile: {}", version.getLoader(), versionJson);
        return gson.fromJson(Files.readString(versionJson), VersionInfo.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Lists the immediate child directories of {@code versionsDir}. */
    private static List<Path> listVersionDirs(Path versionsDir) throws IOException {
        try (Stream<Path> stream = Files.list(versionsDir)) {
            return stream.filter(Files::isDirectory).toList();
        }
    }

    /**
     * Returns the newest version JSON that was not present before the installer ran.
     * If {@code expectedVersionId} is known, it is checked first.
     */
    private static Path findNewVersionJson(Path versionsDir, List<Path> existingDirs,
                                            String expectedVersionId) throws IOException {
        // Try the expected directory first
        if (expectedVersionId != null) {
            Path expected = versionsDir.resolve(expectedVersionId)
                    .resolve(expectedVersionId + ".json");
            if (Files.exists(expected)) {
                return expected;
            }
        }

        // Fall back: find the newest directory that wasn't there before
        try (Stream<Path> stream = Files.list(versionsDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(d -> existingDirs.stream().noneMatch(e -> e.equals(d)))
                    .max(Comparator.comparingLong(d -> d.toFile().lastModified()))
                    .map(d -> d.resolve(d.getFileName() + ".json"))
                    .filter(Files::exists)
                    .orElse(null);
        }
    }
}
