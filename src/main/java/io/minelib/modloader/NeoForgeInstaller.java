package io.minelib.modloader;

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
 * Installs <a href="https://neoforged.net/">NeoForge</a> by downloading and running the
 * official installer JAR from the NeoForged Maven repository.
 *
 * <h3>Version discovery</h3>
 * <p>Available versions are fetched from Maven metadata at
 * {@code https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml}.
 * For Minecraft 1.20.1 and earlier, NeoForge used the legacy {@code "forge"} group/artifact
 * under {@code net.neoforged.forge}; from 1.20.2 onwards it moved to
 * {@code net.neoforged:neoforge}. This installer handles only the modern groupId.
 *
 * <h3>Installation</h3>
 * <p>The installer JAR is downloaded and run with {@code --installClient <gameDirectory>}.
 */
final class NeoForgeInstaller extends InstallerJarBasedInstaller {

    private static final Logger LOGGER = LoggerFactory.getLogger(NeoForgeInstaller.class);

    private static final String MAVEN_BASE =
            "https://maven.neoforged.net/releases/net/neoforged/neoforge";
    private static final String MAVEN_META_URL = MAVEN_BASE + "/maven-metadata.xml";

    NeoForgeInstaller(DownloadManager downloadManager, VersionManager versionManager) {
        super(downloadManager, versionManager);
    }

    @Override
    public List<ModLoaderVersion> listVersions(String minecraftVersion) throws IOException {
        String xml = fetchString(MAVEN_META_URL);

        // NeoForge versions follow the scheme: <mcMajor>.<mcMinor>.<patch>
        // e.g. for MC 1.21.4 the versions look like "21.4.x"
        String neoPrefix = deriveNeoPrefix(minecraftVersion);

        List<String> versions = parseXmlVersions(xml, neoPrefix);
        List<ModLoaderVersion> result = new ArrayList<>();
        for (String v : versions) {
            result.add(ModLoaderVersion.builder()
                    .loader(ModLoader.NEOFORGE)
                    .minecraftVersion(minecraftVersion)
                    .loaderVersion(v)
                    .stable(!v.contains("beta") && !v.contains("alpha"))
                    .build());
        }
        return result;
    }

    @Override
    public VersionInfo install(ModLoaderVersion version, Path gameDirectory)
            throws IOException, InterruptedException {

        String neoVersion = version.getLoaderVersion();
        String installerUrl = MAVEN_BASE + "/" + neoVersion
                + "/neoforge-" + neoVersion + "-installer.jar";

        // NeoForge creates a version directory named "neoforge-<version>"
        String expectedVersionId = "neoforge-" + neoVersion;

        LOGGER.info("Installing NeoForge {} for Minecraft {}",
                neoVersion, version.getMinecraftVersion());
        return runInstaller(version, gameDirectory, installerUrl, expectedVersionId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Derives the NeoForge version prefix from the Minecraft version.
     * E.g. {@code "1.21.4"} → {@code "21.4."}, {@code "1.21"} → {@code "21."}.
     */
    static String deriveNeoPrefix(String minecraftVersion) {
        // Strip the leading "1." — NeoForge versions mirror MC minor/patch numbers
        String withoutLeading = minecraftVersion.startsWith("1.")
                ? minecraftVersion.substring(2)
                : minecraftVersion;
        return withoutLeading + ".";
    }

    /**
     * Parses {@code <version>} elements from Maven metadata XML that start with
     * {@code prefix}, returning them newest-first.
     */
    private static List<String> parseXmlVersions(String xml, String prefix) {
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
        java.util.Collections.reverse(result);
        return result;
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
