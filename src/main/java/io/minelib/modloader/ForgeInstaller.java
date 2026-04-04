package io.minelib.modloader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.minelib.download.DownloadManager;
import io.minelib.version.VersionInfo;
import io.minelib.version.VersionManager;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Installs <a href="https://minecraftforge.net/">Forge</a> (MinecraftForge) by downloading
 * and running the official installer JAR.
 *
 * <h2>Version discovery</h2>
 * <p>Available versions are fetched from the Forge promotions JSON at
 * {@code https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json}.
 * Full release lists for a given Minecraft version are fetched from the Maven metadata at
 * {@code https://files.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml}.
 *
 * <h2>Installation</h2>
 * <p>The installer JAR is downloaded from Forge's Maven repository and run with
 * {@code --installClient <gameDirectory>}.  The resulting version profile is then parsed
 * and returned.
 */
final class ForgeInstaller extends InstallerJarBasedInstaller {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForgeInstaller.class);

    /** Forge Maven base URL. */
    private static final String MAVEN_BASE =
            "https://maven.minecraftforge.net/net/minecraftforge/forge";

    /** Forge promotions API (latest/recommended per MC version). */
    private static final String PROMOTIONS_URL =
            "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";

    ForgeInstaller(DownloadManager downloadManager, VersionManager versionManager) {
        super(downloadManager, versionManager);
    }

    @Override
    public List<ModLoaderVersion> listVersions(String minecraftVersion) throws IOException {
        // Fetch the Maven metadata XML and parse <version> elements
        String metaUrl = MAVEN_BASE + "/maven-metadata.xml";
        String xml = fetchString(metaUrl);
        List<String> forgeVersions = parseXmlVersions(xml, minecraftVersion);

        // Also resolve the promoted "latest" and "recommended" versions so callers can
        // prefer them
        List<String> promoted = fetchPromoted(minecraftVersion);

        List<ModLoaderVersion> result = new ArrayList<>();
        for (String fv : forgeVersions) {
            // fv is the full Maven version like "1.21.4-54.0.1"; loader part is after the dash
            int sep = fv.indexOf('-');
            String loaderVersion = sep >= 0 ? fv.substring(sep + 1) : fv;
            boolean stable = promoted.contains(loaderVersion);
            result.add(ModLoaderVersion.builder()
                    .loader(ModLoader.FORGE)
                    .minecraftVersion(minecraftVersion)
                    .loaderVersion(loaderVersion)
                    .stable(stable)
                    .build());
        }
        return result;
    }

    @Override
    public VersionInfo install(ModLoaderVersion version, Path gameDirectory)
            throws IOException, InterruptedException {

        String mcVersion = version.getMinecraftVersion();
        String forgeVersion = version.getLoaderVersion();
        // Maven artifact version combines both: "1.21.4-54.0.1"
        String mavenVersion = mcVersion + "-" + forgeVersion;

        String installerUrl = MAVEN_BASE + "/" + mavenVersion
                + "/forge-" + mavenVersion + "-installer.jar";

        // Forge creates a version directory named e.g. "1.21.4-forge-54.0.1"
        String expectedVersionId = mcVersion + "-forge-" + forgeVersion;

        LOGGER.info("Installing Forge {} for Minecraft {}", forgeVersion, mcVersion);
        return runInstaller(version, gameDirectory, installerUrl, expectedVersionId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Parses {@code <version>} elements from Forge Maven metadata XML that begin with
     * {@code <minecraftVersion>-}, returning them newest-first.
     */
    private static List<String> parseXmlVersions(String xml, String mcVersion) {
        String prefix = mcVersion + "-";
        List<String> result = new ArrayList<>();
        int start = 0;
        while (true) {
            int open = xml.indexOf("<version>", start);
            if (open < 0) break;
            int close = xml.indexOf("</version>", open);
            if (close < 0) break;
            String v = xml.substring(open + "<version>".length(), close).trim();
            if (v.startsWith(prefix)) {
                result.add(v);
            }
            start = close + 1;
        }
        // Reverse so newest is first
        java.util.Collections.reverse(result);
        return result;
    }

    /** Fetches the Forge promotions JSON and returns the promoted loader versions for the
     *  given Minecraft version. */
    private List<String> fetchPromoted(String mcVersion) throws IOException {
        try {
            String json = fetchString(PROMOTIONS_URL);
            JsonObject root = gson.fromJson(json, JsonObject.class);
            JsonObject promos = root.getAsJsonObject("promos");
            List<String> result = new ArrayList<>();
            for (String key : List.of(mcVersion + "-latest", mcVersion + "-recommended")) {
                JsonElement el = promos.get(key);
                if (el != null && !el.isJsonNull()) {
                    result.add(el.getAsString());
                }
            }
            return result;
        } catch (Exception e) {
            LOGGER.debug("Could not fetch Forge promotions: {}", e.getMessage());
            return List.of();
        }
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
