package io.minelib.modrinth;

import io.minelib.download.DownloadManager;
import io.minelib.download.DownloadTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * {@link ModManager} implementation backed by the
 * <a href="https://docs.modrinth.com/api/">Modrinth API v2</a>.
 *
 * <p>All API requests are made via {@link ModrinthClient}; all file downloads are made
 * via {@link DownloadManager} (which verifies SHA-1 and skips already-present files).
 *
 * <p>Instances are normally obtained from {@link io.minelib.MineLib#getModrinthModManager()}.
 */
public final class ModrinthModManager implements ModManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModrinthModManager.class);

    private final ModrinthClient client;
    private final DownloadManager downloadManager;

    /**
     * Creates a new {@code ModrinthModManager}.
     *
     * @param downloadManager the download manager to use for file downloads; share the
     *                        instance from {@link io.minelib.MineLib} to reuse the
     *                        connection pool
     */
    public ModrinthModManager(DownloadManager downloadManager) {
        if (downloadManager == null) throw new NullPointerException("downloadManager must not be null");
        this.downloadManager = downloadManager;
        this.client = new ModrinthClient(downloadManager.getHttpClient());
    }

    // -------------------------------------------------------------------------
    // ModManager implementation
    // -------------------------------------------------------------------------

    @Override
    public ModrinthProject findProject(String idOrSlug) throws IOException {
        LOGGER.debug("Fetching Modrinth project: {}", idOrSlug);
        return client.getProject(idOrSlug);
    }

    @Override
    public List<ModrinthVersion> listVersions(String projectId,
                                               String minecraftVersion,
                                               String loaderName) throws IOException {
        LOGGER.debug("Listing versions for {} (mc={}, loader={})",
                projectId, minecraftVersion, loaderName);
        return client.getVersions(projectId, minecraftVersion, loaderName);
    }

    @Override
    public ModrinthVersion getLatestVersion(String projectId,
                                             String minecraftVersion,
                                             String loaderName) throws IOException {
        List<ModrinthVersion> versions = listVersions(projectId, minecraftVersion, loaderName);
        if (versions.isEmpty()) {
            throw new NoSuchElementException(
                    "No compatible version found for project '" + projectId
                    + "' (mc=" + minecraftVersion + ", loader=" + loaderName + ")");
        }
        // Prefer: RELEASE > BETA > ALPHA; within each tier take the first (newest)
        Comparator<ModrinthVersion.VersionType> tierOrder = Comparator.comparingInt(t -> switch (t) {
            case RELEASE -> 0;
            case BETA    -> 1;
            case ALPHA   -> 2;
        });
        return versions.stream()
                .min(Comparator.comparing(ModrinthVersion::getVersionType, tierOrder))
                .orElse(versions.get(0));
    }

    @Override
    public void installMod(ModrinthVersion version, Path modsDirectory) throws IOException {
        ModrinthFile file = version.getPrimaryFile();

        Files.createDirectories(modsDirectory);
        Path destination = modsDirectory.resolve(file.getFilename());

        LOGGER.info("Installing mod {} v{} -> {}",
                version.getProjectId(), version.getVersionNumber(), destination);

        DownloadTask task = DownloadTask.builder()
                .url(file.getUrl())
                .destination(destination)
                .sha1(file.getSha1())
                .size(file.getSize())
                .build();

        downloadManager.download(task);
        LOGGER.info("Installed: {}", destination.getFileName());
    }

    @Override
    public void installMod(String projectIdOrSlug,
                            String minecraftVersion,
                            String loaderName,
                            Path modsDirectory) throws IOException {
        ModrinthVersion latest = getLatestVersion(projectIdOrSlug, minecraftVersion, loaderName);
        installMod(latest, modsDirectory);
    }

    @Override
    public List<ModrinthProject> search(String query,
                                         String minecraftVersion,
                                         String loaderName,
                                         int limit) throws IOException {
        LOGGER.debug("Searching Modrinth: query='{}', mc={}, loader={}, limit={}",
                query, minecraftVersion, loaderName, limit);
        return client.search(query, minecraftVersion, loaderName, limit);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the underlying {@link ModrinthClient} for direct API access. */
    public ModrinthClient getClient() { return client; }
}
