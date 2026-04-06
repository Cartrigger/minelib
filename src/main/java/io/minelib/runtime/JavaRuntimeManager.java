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
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Manages Java runtime provisioning for launching Minecraft.
 *
 * <p>On <strong>desktop</strong> (Windows, macOS, Linux) this class can use the JVM already
 * present on the host system or download a suitable Adoptium (Eclipse Temurin) JRE from
 * the Adoptium API and install it under {@code <gameDirectory>/runtime/}.
 *
 * <p>On <strong>Android</strong> (including Meta Quest VR headsets) this class downloads and
 * installs a pre-built OpenJDK from the
 * <a href="https://github.com/FCL-Team/Android-OpenJDK-Build">FCL-Team/Android-OpenJDK-Build</a>
 * CI artifacts via <a href="https://nightly.link">nightly.link</a>.  Supported major versions
 * are {@value #FCL_SUPPORTED_VERSIONS_STR}.  The runtime is installed once into
 * {@code <gameDirectory>/runtime/fcl-jre-<version>/} and reused on subsequent launches.
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

    /**
     * nightly.link URL template for the FCL Android JDK multiarch artifact.
     * Parameters: {@code majorVersion} (twice).
     *
     * <p>The downloaded ZIP contains:
     * {@code universal.tar.xz}, {@code bin-aarch64.tar.xz}, {@code bin-aarch32.tar.xz},
     * {@code bin-amd64.tar.xz}, {@code bin-i386.tar.xz}, and {@code version}.
     */
    private static final String FCL_NIGHTLY_URL =
            "https://nightly.link/FCL-Team/Android-OpenJDK-Build/workflows/build.yml"
            + "/Build_JRE_%d/jre%d-multiarch.zip";

    /** FCL OpenJDK major versions that have a corresponding {@code Build_JRE_*} branch. */
    private static final Set<Integer> FCL_SUPPORTED_VERSIONS = Set.of(17, 25);

    /** Human-readable list of supported FCL versions, used in error messages and Javadoc. */
    static final String FCL_SUPPORTED_VERSIONS_STR = "17, 25";

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
        // On Android, provision the FCL OpenJDK for the required version.
        // We cannot use Adoptium binaries (they are ELF executables targeting desktop Linux)
        // and the bare ART runtime cannot execute Java bytecode outside the launcher process.
        if (Platform.isAndroid()) {
            return provisionAndroidFclRuntime(requiredMajorVersion);
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

    // -------------------------------------------------------------------------
    // Android / FCL JDK provisioning
    // -------------------------------------------------------------------------

    /**
     * Provisions an FCL OpenJDK for the given required major version on Android.
     *
     * <p>The method selects the smallest supported FCL version that is
     * {@code >= requiredMajorVersion}, then either returns the already-installed runtime or
     * downloads it from nightly.link.
     *
     * @param requiredMajorVersion minimum Java major version required
     * @return the installed {@link JavaRuntime}
     * @throws IOException if no suitable FCL version exists or the download fails
     */
    private JavaRuntime provisionAndroidFclRuntime(int requiredMajorVersion) throws IOException {
        int fclVersion = selectFclVersion(requiredMajorVersion);
        Path runtimeDir = gameDirectory.resolve("runtime").resolve("fcl-jre-" + fclVersion);
        if (Files.isDirectory(runtimeDir) && Files.exists(runtimeDir.resolve("release"))) {
            LOGGER.debug("Using cached FCL OpenJDK {} at {}", fclVersion, runtimeDir);
            return new JavaRuntime(fclVersion, "FCL OpenJDK", runtimeDir);
        }
        LOGGER.info("Downloading FCL OpenJDK {} for Android from nightly.link", fclVersion);
        return downloadFclRuntime(fclVersion, runtimeDir);
    }

    /**
     * Selects the smallest FCL-supported major version that satisfies {@code required}.
     *
     * @throws IOException if no supported version satisfies {@code required}
     */
    static int selectFclVersion(int required) throws IOException {
        return FCL_SUPPORTED_VERSIONS.stream()
                .filter(v -> v >= required)
                .min(Integer::compare)
                .orElseThrow(() -> new IOException(
                        "No FCL OpenJDK available for Java " + required
                        + ". Supported versions: " + FCL_SUPPORTED_VERSIONS_STR));
    }

    /**
     * Downloads the FCL OpenJDK multiarch ZIP from nightly.link, extracts the
     * {@code universal.tar.xz} and the architecture-specific {@code bin-*.tar.xz} into
     * {@code targetDir}, then returns a {@link JavaRuntime} pointing to that directory.
     *
     * <p>The architecture is detected automatically from the JVM's {@code os.arch} system
     * property; defaults to {@code aarch64} (suitable for Meta Quest).
     */
    private JavaRuntime downloadFclRuntime(int version, Path targetDir) throws IOException {
        String archSuffix = detectFclArchSuffix();
        String url = String.format(FCL_NIGHTLY_URL, version, version);

        Path parent = targetDir.getParent();
        Files.createDirectories(parent);
        Path zipPath = parent.resolve("fcl-jre-" + version + "-multiarch.zip");
        Path tempDir = parent.resolve("fcl-jre-" + version + "-tmp");

        try {
            downloadManager.download(DownloadTask.builder()
                    .url(url)
                    .destination(zipPath)
                    .build());

            Files.createDirectories(tempDir);
            extractZipNoStrip(zipPath, tempDir);
            Files.deleteIfExists(zipPath);

            Files.createDirectories(targetDir);
            extractTarXz(tempDir.resolve("universal.tar.xz"), targetDir);
            extractTarXz(tempDir.resolve("bin-" + archSuffix + ".tar.xz"), targetDir);

            return new JavaRuntime(version, "FCL OpenJDK", targetDir);
        } finally {
            Files.deleteIfExists(zipPath);
            if (Files.isDirectory(tempDir)) {
                deleteDirectory(tempDir);
            }
        }
    }

    /**
     * Returns the FCL bin-archive suffix for the current Android device ABI.
     *
     * <p>Mapping:
     * <ul>
     *   <li>{@code aarch64} / {@code arm64} → {@code aarch64} (Meta Quest, most modern Android)</li>
     *   <li>{@code arm*}                    → {@code aarch32}</li>
     *   <li>{@code x86_64} / {@code amd64}  → {@code amd64}</li>
     *   <li>{@code x86} / {@code i386}      → {@code i386}</li>
     * </ul>
     *
     * @return one of {@code aarch64}, {@code aarch32}, {@code amd64}, {@code i386}
     */
    static String detectFclArchSuffix() {
        String raw = System.getProperty("os.arch", "aarch64").toLowerCase(Locale.ROOT);
        if (raw.equals("aarch64") || raw.equals("arm64")) return "aarch64";
        if (raw.startsWith("arm")) return "aarch32";
        if (raw.equals("x86_64") || raw.equals("amd64")) return "amd64";
        if (raw.equals("x86") || raw.equals("i386") || raw.equals("i686")) return "i386";
        return "aarch64"; // Safe default for Meta Quest
    }

    /**
     * Extracts a ZIP archive into {@code targetDir} <em>without</em> stripping any leading
     * path component.  Used for the nightly.link multiarch ZIPs, which store all files at
     * the root level of the archive.
     *
     * <p>Path-traversal entries (entries that resolve outside {@code targetDir}) are rejected.
     */
    private static void extractZipNoStrip(Path archive, Path targetDir) throws IOException {
        Path canonicalTarget = targetDir.toAbsolutePath().normalize();
        try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.isEmpty()) {
                    zis.closeEntry();
                    continue;
                }
                Path dest = canonicalTarget.resolve(name).normalize();
                if (!dest.startsWith(canonicalTarget)) {
                    throw new IOException("Zip entry escapes target directory: " + name);
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

    /**
     * Extracts a {@code .tar.xz} archive into {@code targetDir} using the system {@code tar}
     * command.  Files are extracted without stripping any leading path component.
     *
     * <p>Android (toybox ≥ Android 6.0) and desktop Linux both provide a {@code tar}
     * implementation that supports XZ compression via the {@code -J} flag.
     *
     * @throws IOException if the {@code tar} process exits with a non-zero code or is
     *                     interrupted, or if the archive file does not exist
     */
    private static void extractTarXz(Path archive, Path targetDir) throws IOException {
        if (!Files.exists(archive)) {
            throw new IOException("FCL JDK archive not found: " + archive
                    + " — the requested architecture may not be supported by this build");
        }
        Files.createDirectories(targetDir);
        ProcessBuilder pb = new ProcessBuilder(
                "tar", "-xJf", archive.toAbsolutePath().toString(),
                "-C", targetDir.toAbsolutePath().toString());
        pb.inheritIO();
        try {
            int exit = pb.start().waitFor();
            if (exit != 0) {
                throw new IOException(
                        "tar -xJf exited with code " + exit + " for " + archive);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Extraction of " + archive + " was interrupted", e);
        }
    }

    /** Recursively deletes a directory tree, silently ignoring individual failures. */
    private static void deleteDirectory(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
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
