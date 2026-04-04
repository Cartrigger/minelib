package io.minelib.modloader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.minelib.download.DownloadManager;
import io.minelib.download.DownloadTask;
import io.minelib.library.LibraryManager;
import io.minelib.version.VersionInfo;
import io.minelib.version.VersionManager;
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

/**
 * Installs Fabric mod loader using the
 * <a href="https://meta.fabricmc.net/">Fabric Meta API</a>.
 *
 * <p>No separate installer JAR is needed. The installer:
 * <ol>
 *   <li>Queries {@code https://meta.fabricmc.net/v2/versions/loader/{mcVersion}} to list
 *       available loader versions.</li>
 *   <li>Downloads the version profile JSON from
 *       {@code .../loader/{mcVersion}/{loaderVersion}/profile/json}.</li>
 *   <li>Downloads all libraries listed in that profile from their respective Maven
 *       repositories.</li>
 *   <li>Merges the Fabric profile with the vanilla {@link VersionInfo} (inheritsFrom) and
 *       saves the result under {@code versions/<id>/<id>.json}.</li>
 * </ol>
 */
final class FabricInstaller implements ModLoaderInstaller {

    private static final Logger LOGGER = LoggerFactory.getLogger(FabricInstaller.class);

    private static final String META_BASE = "https://meta.fabricmc.net/v2";
    private static final String LOADER_LIST_URL = META_BASE + "/versions/loader/%s";
    private static final String PROFILE_URL = META_BASE + "/versions/loader/%s/%s/profile/json";

    private final DownloadManager downloadManager;
    private final VersionManager versionManager;
    private final Gson gson = new Gson();

    FabricInstaller(DownloadManager downloadManager, VersionManager versionManager) {
        this.downloadManager = downloadManager;
        this.versionManager = versionManager;
    }

    @Override
    public List<ModLoaderVersion> listVersions(String minecraftVersion) throws IOException {
        String url = String.format(LOADER_LIST_URL, minecraftVersion);
        String json = fetchString(url);
        JsonArray array = gson.fromJson(json, JsonArray.class);

        List<ModLoaderVersion> versions = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject loaderObj = element.getAsJsonObject()
                    .getAsJsonObject("loader");
            String loaderVersion = loaderObj.get("version").getAsString();
            boolean stable = loaderObj.get("stable").getAsBoolean();
            versions.add(ModLoaderVersion.builder()
                    .loader(ModLoader.FABRIC)
                    .minecraftVersion(minecraftVersion)
                    .loaderVersion(loaderVersion)
                    .stable(stable)
                    .build());
        }
        return versions;
    }

    @Override
    public VersionInfo install(ModLoaderVersion version, Path gameDirectory)
            throws IOException, InterruptedException {
        String mcVersion = version.getMinecraftVersion();
        String loaderVersion = version.getLoaderVersion();

        LOGGER.info("Installing Fabric {} for Minecraft {}", loaderVersion, mcVersion);

        // 1. Download the Fabric profile JSON
        String profileUrl = String.format(PROFILE_URL, mcVersion, loaderVersion);
        String profileJson = fetchString(profileUrl);
        VersionInfo fabricProfile = gson.fromJson(profileJson, VersionInfo.class);

        // 2. Ensure the vanilla base version is installed (Fabric's inheritsFrom)
        String baseVersion = fabricProfile.getInheritsFrom() != null
                ? fabricProfile.getInheritsFrom()
                : mcVersion;
        LOGGER.debug("Ensuring vanilla base version {} is installed", baseVersion);
        VersionInfo vanillaVersion = versionManager.downloadVersion(baseVersion);

        // 3. Merge the profiles: Fabric overrides mainClass and adds to libraries/arguments
        VersionInfo merged = mergeProfiles(vanillaVersion, fabricProfile);

        // 4. Save the merged profile
        Path versionDir = gameDirectory.resolve("versions").resolve(merged.getId());
        Files.createDirectories(versionDir);
        Path versionJson = versionDir.resolve(merged.getId() + ".json");
        Files.writeString(versionJson, gson.toJson(fabricProfile));
        LOGGER.debug("Saved Fabric profile to {}", versionJson);

        // 5. Download Fabric libraries
        if (fabricProfile.getLibraries() != null) {
            List<DownloadTask> tasks = new ArrayList<>();
            for (VersionInfo.Library library : fabricProfile.getLibraries()) {
                if (library.getName() == null || library.getUrl() == null) continue;
                String relPath = mavenPathFromName(library.getName());
                String base = library.getUrl().endsWith("/")
                        ? library.getUrl() : library.getUrl() + "/";
                String libUrl = base + relPath;
                Path dest = gameDirectory.resolve("libraries").resolve(relPath);
                if (!Files.exists(dest)) {
                    Files.createDirectories(dest.getParent());
                    tasks.add(DownloadTask.builder()
                            .url(libUrl)
                            .destination(dest)
                            .build());
                }
            }
            if (!tasks.isEmpty()) {
                LOGGER.info("Downloading {} Fabric librar{}", tasks.size(),
                        tasks.size() == 1 ? "y" : "ies");
                downloadManager.downloadAll(tasks);
            }
        }

        LOGGER.info("Fabric {} installed successfully as version '{}'",
                loaderVersion, merged.getId());
        return merged;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a merged {@link VersionInfo} where:
     * <ul>
     *   <li>The ID is taken from the Fabric profile.</li>
     *   <li>The main class is taken from the Fabric profile (overrides vanilla).</li>
     *   <li>Libraries are the union of vanilla + Fabric (Fabric first, so it can
     *       override older versions of shared libraries).</li>
     *   <li>All other fields are taken from vanilla unless overridden by Fabric.</li>
     * </ul>
     *
     * <p>The merged result is a complete, self-contained {@link VersionInfo} that can be
     * passed to {@link io.minelib.launch.GameLauncher#launch} without further processing.
     */
    private VersionInfo mergeProfiles(VersionInfo vanilla, VersionInfo fabric) {
        // Re-serialise vanilla, overlay Fabric fields, and deserialise the merged result.
        JsonObject vanillaJson = gson.toJsonTree(vanilla).getAsJsonObject();
        JsonObject fabricJson = gson.toJsonTree(fabric).getAsJsonObject();

        // Fields that Fabric defines override vanilla
        for (String key : List.of("id", "mainClass", "type", "releaseTime", "time")) {
            if (fabricJson.has(key) && !fabricJson.get(key).isJsonNull()) {
                vanillaJson.addProperty(key, fabricJson.get(key).getAsString());
            }
        }

        // Libraries: prepend Fabric's libraries so they take precedence on classpath
        JsonArray mergedLibs = new JsonArray();
        if (fabricJson.has("libraries")) {
            mergedLibs.addAll(fabricJson.getAsJsonArray("libraries"));
        }
        if (vanillaJson.has("libraries")) {
            mergedLibs.addAll(vanillaJson.getAsJsonArray("libraries"));
        }
        vanillaJson.add("libraries", mergedLibs);

        // Arguments: merge game and JVM arg lists
        if (fabricJson.has("arguments")) {
            JsonObject fabricArgs = fabricJson.getAsJsonObject("arguments");
            JsonObject vanillaArgs = vanillaJson.has("arguments")
                    ? vanillaJson.getAsJsonObject("arguments")
                    : new JsonObject();
            for (String argType : List.of("game", "jvm")) {
                if (fabricArgs.has(argType)) {
                    JsonArray merged = new JsonArray();
                    if (vanillaArgs.has(argType)) {
                        merged.addAll(vanillaArgs.getAsJsonArray(argType));
                    }
                    merged.addAll(fabricArgs.getAsJsonArray(argType));
                    vanillaArgs.add(argType, merged);
                }
            }
            vanillaJson.add("arguments", vanillaArgs);
        }

        // Remove inheritsFrom — the merged profile is self-contained
        vanillaJson.remove("inheritsFrom");

        return gson.fromJson(vanillaJson, VersionInfo.class);
    }

    /** Converts a Maven {@code group:artifact:version} name to a relative file path. */
    private static String mavenPathFromName(String name) {
        String[] parts = name.split(":");
        if (parts.length < 3) return name.replace(':', '/') + ".jar";
        return LibraryManager.mavenCoordinatesToPath(parts[0], parts[1], parts[2],
                parts.length >= 4 ? parts[3] : null);
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
