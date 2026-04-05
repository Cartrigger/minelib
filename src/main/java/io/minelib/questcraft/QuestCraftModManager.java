package io.minelib.questcraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fetches and installs VR mods for QuestCraft from the
 * <a href="https://github.com/QuestCraftPlusPlus/pojlib/blob/QuestCraft-6.0.1/mods.json">
 * pojlib {@code QuestCraft-6.0.1} branch</a>.
 *
 * <h2>mods.json structure</h2>
 * <pre>{@code
 * {
 *   "versions": [
 *     {
 *       "name": "1.21.10",
 *       "coreMods":    [ { "slug": "Vivecraft",  "version": "1.3.4.2", "download_link": "..." }, ... ],
 *       "defaultMods": [ { "slug": "Sodium",     "version": "...",     "download_link": "..." }, ... ]
 *     },
 *     ...
 *   ]
 * }
 * }</pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * QuestCraftModManager mgr = new QuestCraftModManager(downloadManager);
 *
 * List<QuestCraftVersionEntry> versions = mgr.fetchVersionEntries();
 * QuestCraftVersionEntry entry = mgr.findVersion(versions, "1.21.10")
 *         .orElseThrow(() -> new IOException("Unsupported MC version"));
 *
 * // Install only the required VR mods (Vivecraft + Fabric API)
 * mgr.downloadCoreMods(entry, modsDirectory);
 *
 * // OR install everything (core + performance mods)
 * mgr.downloadAllMods(entry, modsDirectory);
 * }</pre>
 */
public final class QuestCraftModManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestCraftModManager.class);

    /**
     * Raw URL of the {@code mods.json} on the {@code QuestCraft-6.0.1} branch of the
     * QuestCraftPlusPlus/pojlib repository.
     */
    public static final String MODS_JSON_URL =
            "https://raw.githubusercontent.com/QuestCraftPlusPlus/pojlib/QuestCraft-6.0.1/mods.json";

    private final DownloadManager downloadManager;
    private final Gson gson = new Gson();

    /**
     * Creates a new {@code QuestCraftModManager}.
     *
     * @param downloadManager the download manager used for fetching the manifest and mod JARs
     */
    public QuestCraftModManager(DownloadManager downloadManager) {
        this.downloadManager = Objects.requireNonNull(downloadManager, "downloadManager");
    }

    // -------------------------------------------------------------------------
    // Manifest fetching and parsing
    // -------------------------------------------------------------------------

    /**
     * Fetches and parses the QuestCraft {@code mods.json} manifest.
     *
     * <p>The returned list is ordered exactly as the JSON; typically newest Minecraft
     * version first.
     *
     * @return list of version entries; never {@code null}, never empty on success
     * @throws IOException if the manifest cannot be fetched or parsed
     */
    public List<QuestCraftVersionEntry> fetchVersionEntries() throws IOException {
        LOGGER.debug("Fetching QuestCraft mods.json from {}", MODS_JSON_URL);
        String json = fetchString(MODS_JSON_URL);
        return parseVersionEntries(json);
    }

    /**
     * Finds the {@link QuestCraftVersionEntry} for the given Minecraft version name.
     *
     * @param entries         list returned by {@link #fetchVersionEntries()}
     * @param minecraftVersion the exact Minecraft version string (e.g. {@code "1.21.10"})
     * @return an {@link Optional} containing the entry, or empty if not found
     */
    public Optional<QuestCraftVersionEntry> findVersion(List<QuestCraftVersionEntry> entries,
                                                        String minecraftVersion) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        return entries.stream().filter(e -> e.getName().equals(minecraftVersion)).findFirst();
    }

    // -------------------------------------------------------------------------
    // Mod downloading
    // -------------------------------------------------------------------------

    /**
     * Downloads only the <em>core</em> mods (Vivecraft + Fabric API) for the given version
     * entry into {@code modsDir}.
     *
     * <p>Each JAR is saved as {@code <slug>-<version>.jar}. Already-present files with the
     * same name are skipped.
     *
     * @param entry   the version entry whose core mods should be downloaded
     * @param modsDir the directory to install mod JARs into; created if it does not exist
     * @throws IOException if any download fails
     */
    public void downloadCoreMods(QuestCraftVersionEntry entry, Path modsDir) throws IOException {
        downloadMods(entry.getCoreMods(), modsDir);
    }

    /**
     * Downloads <em>all</em> mods (core + default) for the given version entry into
     * {@code modsDir}.
     *
     * <p>Each JAR is saved as {@code <slug>-<version>.jar}. Already-present files with the
     * same name are skipped.
     *
     * @param entry   the version entry whose mods should be downloaded
     * @param modsDir the directory to install mod JARs into; created if it does not exist
     * @throws IOException if any download fails
     */
    public void downloadAllMods(QuestCraftVersionEntry entry, Path modsDir) throws IOException {
        List<QuestCraftMod> all = new ArrayList<>(
                entry.getCoreMods().size() + entry.getDefaultMods().size());
        all.addAll(entry.getCoreMods());
        all.addAll(entry.getDefaultMods());
        downloadMods(all, modsDir);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Downloads a list of mods into the given directory.
     * Uses {@link DownloadManager#downloadAll} for parallel downloading.
     */
    private void downloadMods(List<QuestCraftMod> mods, Path modsDir) throws IOException {
        Files.createDirectories(modsDir);
        List<DownloadTask> tasks = new ArrayList<>(mods.size());
        for (QuestCraftMod mod : mods) {
            String fileName = mod.getSlug() + "-" + mod.getVersion() + ".jar";
            Path dest = modsDir.resolve(fileName);
            LOGGER.debug("Queuing mod download: {} -> {}", mod.getDownloadLink(), dest);
            tasks.add(DownloadTask.builder()
                    .url(mod.getDownloadLink())
                    .destination(dest)
                    .build());
        }
        LOGGER.info("Downloading {} QuestCraft mod(s) into {}", tasks.size(), modsDir);
        downloadManager.downloadAll(tasks);
    }

    /**
     * Parses the raw {@code mods.json} string into a list of {@link QuestCraftVersionEntry}.
     */
    List<QuestCraftVersionEntry> parseVersionEntries(String json) throws IOException {
        JsonObject root;
        try {
            root = gson.fromJson(json, JsonObject.class);
        } catch (Exception e) {
            throw new IOException("Failed to parse mods.json: " + e.getMessage(), e);
        }
        if (root == null || !root.has("versions")) {
            throw new IOException("mods.json is missing the 'versions' array");
        }
        JsonArray versionsArray = root.getAsJsonArray("versions");
        List<QuestCraftVersionEntry> entries = new ArrayList<>(versionsArray.size());
        for (JsonElement el : versionsArray) {
            entries.add(parseVersionEntry(el.getAsJsonObject()));
        }
        return entries;
    }

    private QuestCraftVersionEntry parseVersionEntry(JsonObject obj) {
        String name = obj.get("name").getAsString();
        List<QuestCraftMod> coreMods = parseModList(obj.getAsJsonArray("coreMods"));
        List<QuestCraftMod> defaultMods = parseModList(obj.getAsJsonArray("defaultMods"));
        return new QuestCraftVersionEntry(name, coreMods, defaultMods);
    }

    private List<QuestCraftMod> parseModList(JsonArray arr) {
        List<QuestCraftMod> mods = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            String slug = obj.get("slug").getAsString();
            String version = obj.get("version").getAsString();
            String link = obj.get("download_link").getAsString();
            mods.add(new QuestCraftMod(slug, version, link));
        }
        return mods;
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
