package io.minelib.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.minelib.download.DownloadManager;
import io.minelib.download.DownloadTask;
import io.minelib.platform.Platform;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Manages Java runtime provisioning for launching Minecraft.
 *
 * <p>On <strong>desktop</strong> (Windows, macOS, Linux) this class can use the JVM already
 * present on the host system or download a suitable Adoptium (Eclipse Temurin) JRE from
 * the Adoptium API and install it under {@code <gameDirectory>/runtime/}.
 *
 * <p>On <strong>Android</strong> the ART runtime is always already present in the process,
 * so {@link #provisionRuntime(int)} returns a {@link JavaRuntime} wrapping the current ART
 * instance without downloading anything.
 *
 * <p>The {@link #provisionRuntime(int)} method first checks whether a previously
 * installed runtime that satisfies the requested major version is already available under
 * the managed directory before downloading a new one.
 */
public final class JavaRuntimeManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaRuntimeManager.class);

    /**
     * Adoptium (Eclipse Temurin) API endpoint for the latest release of a given feature version.
     * Parameters: {@code featureVersion}, {@code os}, {@code arch}, {@code imageType}.
     */
    private static final String ADOPTIUM_API =
            "https://api.adoptium.net/v3/assets/latest/%d/hotspot?architecture=%s&image_type=jre&os=%s&vendor=eclipse";

    private static final Map<String, String> ADOPTIUM_OS = Map.of(
            "windows", "windows",
            "osx", "mac",
            "linux", "linux"
    );

    private static final Map<String, String> ADOPTIUM_ARCH = Map.of(
            "amd64", "x64",
            "x86_64", "x64",
            "x64", "x64",
            "aarch64", "aarch64",
            "arm64", "aarch64",
            "x86", "x32"
    );

    private final Path gameDirectory;
    private final DownloadManager downloadManager;
    private final Gson gson = new Gson();

    public JavaRuntimeManager(Path gameDirectory, DownloadManager downloadManager) {
        this.gameDirectory = gameDirectory;
        this.downloadManager = downloadManager;
    }

    /**
     * Returns a {@link JavaRuntime} that satisfies the given major Java version requirement.
     *
     * <p>The method first checks the currently running JVM, then looks in the managed
     * {@code <gameDirectory>/runtime/} directory, and finally downloads a new Temurin JRE if
     * nothing suitable is found.
     *
     * @param requiredMajorVersion the minimum Java major version required (e.g. {@code 17})
     * @return a provisioned {@link JavaRuntime}
     * @throws IOException if a suitable runtime cannot be provisioned
     */
    public JavaRuntime provisionRuntime(int requiredMajorVersion) throws IOException {
        // On Android the ART runtime is already running inside the launcher process.
        // We never need to download an external JRE — and doing so would be both pointless
        // (Temurin binaries are ELF executables, not APKs) and wasteful.
        if (Platform.isAndroid()) {
            int currentMajor = Runtime.version().feature();
            String vendor = System.getProperty("java.vendor", "Android Runtime");
            Path javaHome = Path.of(System.getProperty("java.home", "/system"));
            LOGGER.debug("Android detected — using current ART runtime (Java {})", currentMajor);
            return new JavaRuntime(currentMajor, vendor, javaHome);
        }

        // 1. Check current JVM
        int currentMajor = Runtime.version().feature();
        if (currentMajor >= requiredMajorVersion) {
            Path javaHome = Path.of(System.getProperty("java.home"));
            LOGGER.debug("Using current JVM (Java {}) at {}", currentMajor, javaHome);
            String vendor = System.getProperty("java.vendor", "Unknown");
            return new JavaRuntime(currentMajor, vendor, javaHome);
        }

        // 2. Check managed runtime directory
        Path runtimeDir = gameDirectory.resolve("runtime").resolve("java-" + requiredMajorVersion);
        if (Files.isDirectory(runtimeDir)) {
            LOGGER.debug("Using managed runtime at {}", runtimeDir);
            return new JavaRuntime(requiredMajorVersion, "Eclipse Temurin", runtimeDir);
        }

        // 3. Download a new runtime
        LOGGER.info("Downloading Java {} JRE from Adoptium", requiredMajorVersion);
        return downloadRuntime(requiredMajorVersion, runtimeDir);
    }

    /**
     * Downloads and installs a Temurin JRE for the requested version.
     *
     * @param majorVersion the Java major version to download
     * @param targetDir    the directory to install the JRE into
     * @return the installed {@link JavaRuntime}
     * @throws IOException if the download or extraction fails
     */
    private JavaRuntime downloadRuntime(int majorVersion, Path targetDir) throws IOException {
        String os = detectAdoptiumOs();
        String arch = detectAdoptiumArch();
        String apiUrl = String.format(ADOPTIUM_API, majorVersion, arch, os);

        LOGGER.debug("Fetching Adoptium release metadata from {}", apiUrl);
        String json = fetchString(apiUrl);
        JsonArray releases = gson.fromJson(json, JsonArray.class);
        if (releases == null || releases.isEmpty()) {
            throw new IOException("No Adoptium JRE release found for Java " + majorVersion
                    + " on " + os + "/" + arch);
        }

        JsonObject release = releases.get(0).getAsJsonObject();
        JsonObject binary = release.getAsJsonObject("binary");
        JsonObject packageInfo = binary.getAsJsonObject("package");
        String downloadUrl = packageInfo.get("link").getAsString();
        String sha256 = packageInfo.get("checksum").getAsString();
        long size = packageInfo.get("size").getAsLong();
        String archiveName = packageInfo.get("name").getAsString();

        Files.createDirectories(targetDir.getParent());
        Path archivePath = targetDir.getParent().resolve(archiveName);

        LOGGER.info("Downloading JRE archive: {}", archiveName);
        downloadManager.download(DownloadTask.builder()
                .url(downloadUrl)
                .destination(archivePath)
                .sha256(sha256)
                .size(size)
                .build());

        LOGGER.info("Extracting JRE to {}", targetDir);
        extractArchive(archivePath, targetDir);
        Files.deleteIfExists(archivePath);

        return new JavaRuntime(majorVersion, "Eclipse Temurin", targetDir);
    }

    /**
     * Extracts a downloaded JRE archive (tar.gz or zip) into the target directory.
     * The top-level directory inside the archive is stripped so that the JRE root lands
     * directly at {@code targetDir}.
     */
    private void extractArchive(Path archive, Path targetDir) throws IOException {
        String name = archive.getFileName().toString().toLowerCase(Locale.ROOT);
        Files.createDirectories(targetDir);

        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            // Use the system tar command for reliable extraction of tar.gz archives
            ProcessBuilder pb = new ProcessBuilder(
                    "tar", "-xzf", archive.toAbsolutePath().toString(),
                    "--strip-components=1",
                    "-C", targetDir.toAbsolutePath().toString());
            pb.inheritIO();
            try {
                int exit = pb.start().waitFor();
                if (exit != 0) {
                    throw new IOException("tar extraction failed with exit code " + exit);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("JRE extraction was interrupted", e);
            }
        } else if (name.endsWith(".zip")) {
            extractZip(archive, targetDir);
        } else {
            throw new IOException("Unsupported archive format: " + archive.getFileName());
        }
    }

    /** Extracts a zip archive, stripping the first path component from every entry. */
    private void extractZip(Path archive, Path targetDir) throws IOException {
        Path canonicalTarget = targetDir.toAbsolutePath().normalize();
        try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                // Strip the top-level directory component
                int sep = entryName.indexOf('/');
                if (sep < 0) {
                    zis.closeEntry();
                    continue;
                }
                String relative = entryName.substring(sep + 1);
                if (relative.isEmpty()) {
                    zis.closeEntry();
                    continue;
                }
                Path dest = canonicalTarget.resolve(relative).normalize();
                if (!dest.startsWith(canonicalTarget)) {
                    throw new IOException("Zip entry escapes target directory: " + entryName);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(zis, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
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

    private static String detectAdoptiumOs() {
        if (Platform.isWindows()) return "windows";
        if (Platform.isMac()) return "mac";
        return "linux";
    }

    private static String detectAdoptiumArch() {
        String raw = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return ADOPTIUM_ARCH.getOrDefault(raw, "x64");
    }
}
