package io.minelib.version;

import com.google.gson.Gson;
import io.minelib.download.DownloadManager;
import io.minelib.download.DownloadTask;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Manages Minecraft version metadata: listing available versions and downloading the full
 * {@link VersionInfo} descriptor together with the client JAR.
 *
 * <p>Resolved version descriptors are cached under
 * {@code <gameDirectory>/versions/<id>/<id>.json} following the standard Minecraft
 * launcher directory layout.
 */
public final class VersionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(VersionManager.class);

    private final Path gameDirectory;
    private final DownloadManager downloadManager;
    private final Gson gson = new Gson();

    public VersionManager(Path gameDirectory, DownloadManager downloadManager) {
        this.gameDirectory = gameDirectory;
        this.downloadManager = downloadManager;
    }

    /**
     * Fetches the Mojang version manifest and returns all available version entries.
     *
     * @return list of all known Minecraft versions
     * @throws IOException if the manifest cannot be fetched
     */
    public List<VersionManifest.VersionEntry> listVersions() throws IOException {
        LOGGER.debug("Fetching version manifest from {}", VersionManifest.MANIFEST_URL);
        String json = fetchString(VersionManifest.MANIFEST_URL);
        VersionManifest manifest = gson.fromJson(json, VersionManifest.class);
        return manifest.getVersions();
    }

    /**
     * Fetches the Mojang version manifest and returns the latest versions.
     *
     * @return the latest release and snapshot version identifiers
     * @throws IOException if the manifest cannot be fetched
     */
    public VersionManifest.Latest getLatest() throws IOException {
        String json = fetchString(VersionManifest.MANIFEST_URL);
        VersionManifest manifest = gson.fromJson(json, VersionManifest.class);
        return manifest.getLatest();
    }

    /**
     * Downloads (or loads from cache) the full {@link VersionInfo} for the given version ID and
     * also downloads the client JAR if it is not yet present.
     *
     * @param versionId the version identifier, e.g. {@code "1.21"}
     * @return the parsed {@link VersionInfo}
     * @throws IOException if the version does not exist or cannot be downloaded
     */
    public VersionInfo downloadVersion(String versionId) throws IOException {
        Path versionDir = gameDirectory.resolve("versions").resolve(versionId);
        Path versionJson = versionDir.resolve(versionId + ".json");

        VersionInfo versionInfo;

        if (Files.exists(versionJson)) {
            LOGGER.debug("Loading cached version descriptor: {}", versionJson);
            versionInfo = gson.fromJson(Files.readString(versionJson), VersionInfo.class);
        } else {
            LOGGER.info("Downloading version descriptor for {}", versionId);
            VersionManifest.VersionEntry entry = findEntry(versionId);
            Files.createDirectories(versionDir);
            downloadManager.download(DownloadTask.builder()
                    .url(entry.getUrl())
                    .destination(versionJson)
                    .sha1(entry.getSha1())
                    .build());
            versionInfo = gson.fromJson(Files.readString(versionJson), VersionInfo.class);
        }

        // Download the client JAR
        Path clientJar = versionDir.resolve(versionId + ".jar");
        if (!Files.exists(clientJar) && versionInfo.getDownloads() != null
                && versionInfo.getDownloads().getClient() != null) {
            VersionInfo.Artifact client = versionInfo.getDownloads().getClient();
            LOGGER.info("Downloading client JAR for {}", versionId);
            downloadManager.download(DownloadTask.builder()
                    .url(client.getUrl())
                    .destination(clientJar)
                    .sha1(client.getSha1())
                    .size(client.getSize())
                    .build());
        }

        return versionInfo;
    }

    /**
     * Resolves the {@link VersionInfo} from the local cache without downloading anything.
     *
     * @param versionId the version identifier
     * @return the cached {@link VersionInfo}, or {@link Optional#empty()} if not yet downloaded
     * @throws IOException if the cached JSON cannot be read
     */
    public Optional<VersionInfo> getCachedVersion(String versionId) throws IOException {
        Path versionJson = gameDirectory.resolve("versions")
                .resolve(versionId)
                .resolve(versionId + ".json");
        if (!Files.exists(versionJson)) {
            return Optional.empty();
        }
        return Optional.of(gson.fromJson(Files.readString(versionJson), VersionInfo.class));
    }

    /**
     * Returns the path to the client JAR for a given version.
     *
     * @param versionId the version identifier
     * @return path to the {@code <id>.jar} file (may not exist if not yet downloaded)
     */
    public Path getClientJarPath(String versionId) {
        return gameDirectory.resolve("versions").resolve(versionId).resolve(versionId + ".jar");
    }

    private VersionManifest.VersionEntry findEntry(String versionId) throws IOException {
        return listVersions().stream()
                .filter(e -> e.getId().equals(versionId))
                .findFirst()
                .orElseThrow(() -> new IOException("Unknown Minecraft version: " + versionId));
    }

    private String fetchString(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = downloadManager.getHttpClient().newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("Failed to fetch " + url + " (HTTP " + response.code() + ")");
            }
            return body.string();
        }
    }
}
