package io.minelib.launcher.android;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts the FCL OpenJDK that is bundled inside the APK's assets folder into the
 * app's private internal storage on the first run.
 *
 * <h2>Asset layout (created by CI before the APK is built)</h2>
 * <pre>
 * assets/
 *   bundled-jre/
 *     jre17-multiarch.zip   ← FCL multiarch bundle; contains:
 *       universal.tar.xz    ← arch-independent JRE files
 *       bin-aarch64.tar.xz  ← aarch64 executables / libraries
 *       bin-aarch32.tar.xz  ← armeabi-v7a executables / libraries
 *       bin-amd64.tar.xz    ← x86_64 executables / libraries
 *       bin-i386.tar.xz     ← x86 executables / libraries
 *       version             ← version string (informational)
 * </pre>
 *
 * <h2>Extraction target</h2>
 * <pre>
 * &lt;filesDir&gt;/runtime/fcl-jre-17/   ← ready-to-use JRE root
 * </pre>
 *
 * <p>Extraction is skipped if the target directory already contains a {@code release} file,
 * meaning the JRE has already been extracted in a previous run.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Call once from Application.onCreate() before any MineLib use
 * BundledJreExtractor.extractIfNeeded(context);
 * }</pre>
 */
public final class BundledJreExtractor {

    private static final String TAG = "BundledJreExtractor";

    /** JRE version bundled in the APK assets. */
    static final int BUNDLED_JRE_VERSION = 17;

    private static final String ASSET_ZIP = "bundled-jre/jre17-multiarch.zip";

    private BundledJreExtractor() {}

    /**
     * Extracts the bundled FCL OpenJDK from APK assets into internal storage if it has not
     * already been extracted.
     *
     * @param ctx any Android context (e.g. {@link android.app.Application})
     * @return path to the extracted JRE root ({@code <filesDir>/runtime/fcl-jre-17})
     * @throws IOException if the asset is missing or extraction fails
     */
    public static Path extractIfNeeded(Context ctx) throws IOException {
        Path runtimeDir = ctx.getFilesDir().toPath()
                .resolve("runtime")
                .resolve("fcl-jre-" + BUNDLED_JRE_VERSION);

        if (Files.isDirectory(runtimeDir) && Files.exists(runtimeDir.resolve("release"))) {
            Log.d(TAG, "Bundled JRE already extracted at " + runtimeDir);
            return runtimeDir;
        }

        Log.i(TAG, "Extracting bundled FCL JRE " + BUNDLED_JRE_VERSION + " from APK assets…");

        // If a partial extraction exists, clean it up first
        if (Files.isDirectory(runtimeDir)) {
            deleteDirectory(runtimeDir);
        }
        Files.createDirectories(runtimeDir);

        Path cacheDir = ctx.getCacheDir().toPath();
        Path tempDir = cacheDir.resolve("fcl-jre-" + BUNDLED_JRE_VERSION + "-tmp");
        Files.createDirectories(tempDir);

        try {
            // Step 1: extract the outer multiarch ZIP from assets → temp dir
            //         (yields universal.tar.xz, bin-<arch>.tar.xz, etc.)
            extractZipFromAssets(ctx, ASSET_ZIP, tempDir);

            // Step 2: extract universal JRE files (class libraries, configs, …)
            Path universalTar = tempDir.resolve("universal.tar.xz");
            extractTarXz(universalTar, runtimeDir);

            // Step 3: extract the arch-specific binaries / shared libraries
            String archSuffix = detectArchSuffix();
            Path archTar = tempDir.resolve("bin-" + archSuffix + ".tar.xz");
            extractTarXz(archTar, runtimeDir);

            Log.i(TAG, "FCL JRE " + BUNDLED_JRE_VERSION + " extraction complete: " + runtimeDir);
            return runtimeDir;
        } catch (IOException | RuntimeException e) {
            // Clean up a partial extraction so the next launch retries
            if (Files.isDirectory(runtimeDir)) {
                try { deleteDirectory(runtimeDir); } catch (IOException ignored) {}
            }
            throw (e instanceof IOException) ? (IOException) e
                    : new IOException("JRE extraction failed", e);
        } finally {
            if (Files.isDirectory(tempDir)) {
                try { deleteDirectory(tempDir); } catch (IOException ignored) {}
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the FCL bin-archive suffix matching the current device ABI.
     * Defaults to {@code aarch64} (Meta Quest / modern ARM64 Android).
     */
    static String detectArchSuffix() {
        String arch = System.getProperty("os.arch", "aarch64").toLowerCase(java.util.Locale.ROOT);
        if (arch.equals("aarch64") || arch.equals("arm64")) return "aarch64";
        if (arch.startsWith("arm"))                         return "aarch32";
        if (arch.equals("x86_64") || arch.equals("amd64")) return "amd64";
        if (arch.equals("x86") || arch.equals("i386") || arch.equals("i686")) return "i386";
        return "aarch64";
    }

    /**
     * Streams the given APK asset ZIP through a {@link ZipInputStream} and writes each entry
     * to {@code targetDir}.  No path-stripping is performed (the multiarch ZIP stores all
     * entries at the root level).
     *
     * <p>Path-traversal entries (those that would resolve outside {@code targetDir}) are
     * rejected with an {@link IOException}.
     */
    private static void extractZipFromAssets(Context ctx, String assetPath, Path targetDir)
            throws IOException {
        Path canonical = targetDir.toAbsolutePath().normalize();
        try (InputStream raw = ctx.getAssets().open(assetPath);
             ZipInputStream zis = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.isEmpty()) { zis.closeEntry(); continue; }

                Path dest = canonical.resolve(name).normalize();
                if (!dest.startsWith(canonical)) {
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
     * Extracts a {@code .tar.xz} archive to {@code targetDir} using Android's {@code tar}
     * command (available via toybox/busybox on Android 6+).
     */
    private static void extractTarXz(Path archive, Path targetDir) throws IOException {
        if (!Files.exists(archive)) {
            throw new IOException("Bundled JRE archive not found: " + archive
                    + "  — verify that the APK was built with the bundled-jre assets.");
        }
        Files.createDirectories(targetDir);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "tar", "-xJf", archive.toAbsolutePath().toString(),
                    "-C", targetDir.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            int exit = pb.start().waitFor();
            if (exit != 0) {
                throw new IOException("tar -xJf exited with code " + exit + " for " + archive);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("JRE extraction interrupted", e);
        }
    }

    /** Recursively deletes a directory tree. */
    private static void deleteDirectory(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        }
    }
}
