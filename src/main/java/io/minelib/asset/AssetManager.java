package io.minelib.asset;

import com.google.gson.Gson;
import io.minelib.download.DownloadManager;
import io.minelib.download.DownloadTask;
import io.minelib.version.VersionInfo;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Downloads and manages Minecraft game assets (textures, sounds, etc.).
 *
 * <p>Assets are stored in the standard Minecraft launcher directory layout:
 * <pre>
 * &lt;gameDirectory&gt;/assets/
 *   indexes/&lt;id&gt;.json      — the asset index
 *   objects/&lt;aa&gt;/&lt;hash&gt;   — hash-addressed asset files
 * </pre>
 *
 * <p>Assets are downloaded from Mojang's CDN at
 * {@code https://resources.download.minecraft.net/}.
 */
public final class AssetManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetManager.class);
    private static final String RESOURCES_BASE = "https://resources.download.minecraft.net/";

    private final Path gameDirectory;
    private final DownloadManager downloadManager;
    private final Gson gson = new Gson();

    public AssetManager(Path gameDirectory, DownloadManager downloadManager) {
        this.gameDirectory = gameDirectory;
        this.downloadManager = downloadManager;
    }

    /**
     * Downloads the asset index and all referenced asset objects for the given version.
     *
     * <p>Any assets that are already present (and whose SHA-1 matches) are skipped.
     *
     * @param version the version whose assets should be downloaded
     * @throws IOException if the index or any asset cannot be downloaded
     */
    public void downloadAssets(VersionInfo version) throws IOException {
        VersionInfo.AssetIndex indexInfo = version.getAssetIndex();
        if (indexInfo == null) {
            LOGGER.warn("Version {} has no assetIndex entry, skipping asset download", version.getId());
            return;
        }

        Path indexPath = getIndexPath(indexInfo.getId());
        Files.createDirectories(indexPath.getParent());

        if (!Files.exists(indexPath)) {
            LOGGER.info("Downloading asset index {}", indexInfo.getId());
            downloadManager.download(DownloadTask.builder()
                    .url(indexInfo.getUrl())
                    .destination(indexPath)
                    .sha1(indexInfo.getSha1())
                    .size(indexInfo.getSize())
                    .build());
        }

        AssetIndex index = loadIndex(indexPath);
        downloadAssetObjects(index);
    }

    /**
     * Loads and returns the asset index for a given index identifier.
     *
     * @param indexId the asset index ID (e.g. {@code "17"})
     * @return the parsed {@link AssetIndex}
     * @throws IOException if the index file does not exist or cannot be read
     */
    public AssetIndex loadIndex(String indexId) throws IOException {
        return loadIndex(getIndexPath(indexId));
    }

    /** Returns the path to the asset index JSON for the given ID. */
    public Path getIndexPath(String indexId) {
        return gameDirectory.resolve("assets").resolve("indexes").resolve(indexId + ".json");
    }

    /** Returns the path to a specific asset object given its hash. */
    public Path getObjectPath(String hash) {
        return gameDirectory.resolve("assets").resolve("objects")
                .resolve(hash.substring(0, 2)).resolve(hash);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private AssetIndex loadIndex(Path indexPath) throws IOException {
        return gson.fromJson(Files.readString(indexPath), AssetIndex.class);
    }

    private void downloadAssetObjects(AssetIndex index) throws IOException {
        Map<String, AssetIndex.AssetObject> objects = index.getObjects();
        if (objects == null || objects.isEmpty()) {
            return;
        }

        List<DownloadTask> tasks = new ArrayList<>(objects.size());
        for (AssetIndex.AssetObject obj : objects.values()) {
            String hash = obj.getHash();
            Path dest = getObjectPath(hash);
            if (!Files.exists(dest)) {
                String url = RESOURCES_BASE + obj.getRelativePath();
                tasks.add(DownloadTask.builder()
                        .url(url)
                        .destination(dest)
                        .sha1(hash)
                        .size(obj.getSize())
                        .build());
            }
        }

        if (!tasks.isEmpty()) {
            LOGGER.info("Downloading {} asset object(s)", tasks.size());
            // Ensure all parent directories exist before submitting tasks
            for (DownloadTask task : tasks) {
                Files.createDirectories(task.getDestination().getParent());
            }
            downloadManager.downloadAll(tasks);
        } else {
            LOGGER.debug("All asset objects already present");
        }
    }
}
