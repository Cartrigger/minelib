package io.minelib.launcher.android;

import android.content.Context;
import android.util.Log;

import org.tukaani.xz.XZInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts FCL OpenJDK bundles that are bundled inside the APK's assets folder into the
 * app's private internal storage on the first run.
 *
 * <h2>Supported JRE versions</h2>
 * <ul>
 *   <li>JRE 8  – for Minecraft 1.12.2 and older</li>
 *   <li>JRE 17 – for Minecraft 1.17 – 1.20.x</li>
 *   <li>JRE 25 – for Minecraft 1.21+ (requires Java 21)</li>
 * </ul>
 *
 * <h2>Asset layout (created by CI before the APK is built)</h2>
 * <pre>
 * assets/
 *   bundled-jre/
 *     jre8-multiarch.zip    ← FCL multiarch bundle for JRE 8; contains:
 *     jre17-multiarch.zip   ← FCL multiarch bundle for JRE 17; contains:
 *     jre25-multiarch.zip   ← FCL multiarch bundle for JRE 25; contains:
 *       universal.tar.xz    ← arch-independent JRE files
 *       bin-aarch64.tar.xz  ← aarch64 executables / libraries
 *       bin-aarch32.tar.xz  ← armeabi-v7a executables / libraries
 *       bin-amd64.tar.xz    ← x86_64 executables / libraries
 *       bin-i386.tar.xz     ← x86 executables / libraries
 * </pre>
 *
 * <h2>Extraction target (per version)</h2>
 * <pre>
 * &lt;filesDir&gt;/runtime/fcl-jre-&lt;version&gt;/   ← ready-to-use JRE root
 * </pre>
 *
 * <p>Extraction is skipped for a given version if the target directory already contains a
 * {@code release} file, meaning the JRE has already been extracted.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Extract a specific JRE version (called from Application.onCreate())
 * BundledJreExtractor.extractIfNeeded(context, 17);
 * }</pre>
 */
public final class BundledJreExtractor {

    private static final String TAG = "BundledJreExtractor";

    /** JRE major versions bundled in the APK. Must match the asset filenames. */
    static final int[] BUNDLED_JRE_VERSIONS = {8, 17, 25};

    private static final int TAR_BLOCK = 512;

    private BundledJreExtractor() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Extracts the FCL OpenJDK for {@code jreVersion} from APK assets into internal
     * storage, if it has not already been extracted.
     *
     * @param ctx        any Android context (e.g. {@link android.app.Application})
     * @param jreVersion FCL JRE major version to extract ({@code 8}, {@code 17}, or {@code 25})
     * @return path to the extracted JRE root ({@code <filesDir>/runtime/fcl-jre-<version>})
     * @throws IOException if the asset ZIP is missing or extraction fails
     */
    public static Path extractIfNeeded(Context ctx, int jreVersion) throws IOException {
        Path runtimeDir = ctx.getFilesDir().toPath()
                .resolve("runtime")
                .resolve("fcl-jre-" + jreVersion);

        if (Files.isDirectory(runtimeDir) && Files.exists(runtimeDir.resolve("release"))) {
            Log.d(TAG, "JRE " + jreVersion + " already extracted at " + runtimeDir);
            return runtimeDir;
        }

        String assetZip = "bundled-jre/jre" + jreVersion + "-multiarch.zip";

        // Check the asset exists before clearing any partial extraction
        try {
            ctx.getAssets().open(assetZip).close();
        } catch (IOException e) {
            throw new IOException("Bundled JRE " + jreVersion + " asset not found in APK ("
                    + assetZip + "). Was the APK built with the bundled-jre CI step?", e);
        }

        Log.i(TAG, "Extracting bundled FCL JRE " + jreVersion + " from APK assets…");

        // Clean up any partial extraction
        if (Files.isDirectory(runtimeDir)) {
            deleteDirectory(runtimeDir);
        }
        Files.createDirectories(runtimeDir);

        Path tempDir = ctx.getCacheDir().toPath()
                .resolve("fcl-jre-" + jreVersion + "-tmp");
        Files.createDirectories(tempDir);

        try {
            // Step 1: stream the outer multiarch ZIP from assets → temp dir
            //         (produces universal.tar.xz and bin-<arch>.tar.xz, etc.)
            extractZipFromAssets(ctx, assetZip, tempDir);

            // Step 2: extract arch-independent JRE files
            extractTarXz(tempDir.resolve("universal.tar.xz"), runtimeDir);

            // Step 3: extract the arch-specific binaries / shared libraries
            String archSuffix = detectArchSuffix();
            extractTarXz(tempDir.resolve("bin-" + archSuffix + ".tar.xz"), runtimeDir);

            Log.i(TAG, "FCL JRE " + jreVersion + " extraction complete → " + runtimeDir);
            return runtimeDir;

        } catch (IOException | RuntimeException e) {
            // Clean up the partial extraction so the next launch retries cleanly
            try { deleteDirectory(runtimeDir); } catch (IOException ignored) {}
            throw (e instanceof IOException) ? (IOException) e
                    : new IOException("JRE " + jreVersion + " extraction failed", e);
        } finally {
            try { deleteDirectory(tempDir); } catch (IOException ignored) {}
        }
    }

    // ── Arch detection ────────────────────────────────────────────────────────

    /**
     * Returns the FCL bin-archive suffix matching the current device ABI.
     * Defaults to {@code aarch64} (Meta Quest / modern ARM64 Android).
     */
    static String detectArchSuffix() {
        String arch = System.getProperty("os.arch", "aarch64")
                .toLowerCase(java.util.Locale.ROOT);
        if (arch.equals("aarch64") || arch.equals("arm64")) return "aarch64";
        if (arch.startsWith("arm"))                          return "aarch32";
        if (arch.equals("x86_64") || arch.equals("amd64"))  return "amd64";
        if (arch.equals("x86") || arch.equals("i386") || arch.equals("i686")) return "i386";
        return "aarch64";
    }

    // ── ZIP extraction from assets ────────────────────────────────────────────

    /**
     * Streams the given APK asset ZIP and writes each entry to {@code targetDir}.
     * No path-stripping is performed (the multiarch ZIPs store all entries at root level).
     * Path-traversal entries are rejected.
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
                    throw new IOException("ZIP entry escapes target directory: " + name);
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

    // ── Pure-Java TAR+XZ extraction ───────────────────────────────────────────

    /**
     * Decompresses a {@code .tar.xz} archive and extracts all entries to {@code targetDir}
     * using a pure-Java XZ decompressor ({@link XZInputStream}) and a hand-written TAR
     * reader.  No native {@code tar} binary is needed, which is important because Android
     * does not guarantee that {@code tar} is present in {@code ProcessBuilder}'s PATH.
     *
     * <p>Supported TAR entry types:
     * <ul>
     *   <li>Regular files ({@code '0'} / {@code '\0'} / {@code '7'})</li>
     *   <li>Hard links ({@code '1'}) – resolved by copying the already-extracted target</li>
     *   <li>Symbolic links ({@code '2'}) – created with
     *       {@link Files#createSymbolicLink}; skipped with a log warning on failure</li>
     *   <li>Directories ({@code '5'})</li>
     *   <li>GNU long-name ({@code 'L'}) and long-link ({@code 'K'}) meta-entries</li>
     * </ul>
     *
     * @param archive   path to the {@code .tar.xz} file (must exist)
     * @param targetDir directory into which entries are extracted
     * @throws IOException if the archive is missing, corrupt, or extraction fails
     */
    private static void extractTarXz(Path archive, Path targetDir) throws IOException {
        if (!Files.exists(archive)) {
            throw new IOException("Bundled JRE archive not found: " + archive
                    + "  — verify the APK was built with the bundled-jre CI step.");
        }
        Files.createDirectories(targetDir);
        try (InputStream raw = Files.newInputStream(archive);
             XZInputStream xzIn = new XZInputStream(raw)) {
            extractTar(xzIn, targetDir);
        }
    }

    /**
     * Reads a TAR stream and extracts all entries to {@code targetDir}.
     * The TAR format used here is POSIX (ustar) with GNU extensions for long names.
     */
    private static void extractTar(InputStream in, Path targetDir) throws IOException {
        Path canonical = targetDir.toAbsolutePath().normalize();
        byte[] block = new byte[TAR_BLOCK];
        String pendingLongName = null;
        String pendingLongLink = null;

        while (true) {
            if (!readBlock(in, block)) break; // genuine EOF
            if (isZeroBlock(block)) {
                readBlock(in, block); // consume the second mandatory zero block
                break;
            }

            // Parse name and link fields (may be overridden by GNU long-name entries below)
            String name = pendingLongName != null
                    ? pendingLongName : extractCString(block, 0, 100);
            String linkTarget = pendingLongLink != null
                    ? pendingLongLink : extractCString(block, 157, 100);
            pendingLongName = null;
            pendingLongLink = null;

            // Apply POSIX ustar filename prefix if present
            if (isUstar(block)) {
                String prefix = extractCString(block, 345, 155);
                if (!prefix.isEmpty() && !name.isEmpty()) {
                    name = prefix + "/" + name;
                }
            }

            char type = (char) (block[156] == 0 ? '0' : (block[156] & 0xFF));
            long size = parseOctal(block, 124, 12);

            // ── GNU extended meta-entries ─────────────────────────────────────
            if (type == 'L') {
                pendingLongName = readDataAsString(in, size);
                continue; // data already consumed by readDataAsString
            }
            if (type == 'K') {
                pendingLongLink = readDataAsString(in, size);
                continue;
            }

            // ── Normalise path ────────────────────────────────────────────────
            name = name.replace('\\', '/');
            while (name.startsWith("/")) name = name.substring(1);
            if (name.startsWith("./"))   name = name.substring(2);
            if (name.isEmpty() || name.equals(".")) { skipDataAndPad(in, size); continue; }

            Path dest = canonical.resolve(name).normalize();
            if (!dest.startsWith(canonical)) {
                Log.w(TAG, "Skipping path-traversal TAR entry: " + name);
                skipDataAndPad(in, size);
                continue;
            }

            // ── Dispatch on entry type ────────────────────────────────────────
            switch (type) {
                case '0': case '\0': case '7': {    // regular file / contiguous file
                    Files.createDirectories(dest.getParent());
                    try (OutputStream os = Files.newOutputStream(dest,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        copyData(in, os, size);
                    }
                    skipPad(in, size);
                    break;
                }
                case '1': {    // hard link – copy the already-extracted target file
                    String lnk = linkTarget.replace('\\', '/');
                    while (lnk.startsWith("/")) lnk = lnk.substring(1);
                    Path src = canonical.resolve(lnk).normalize();
                    if (src.startsWith(canonical) && Files.isRegularFile(src)) {
                        Files.createDirectories(dest.getParent());
                        Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        Log.w(TAG, "Hard-link target not found, skipping: " + lnk);
                    }
                    // Hard links have size=0 in the archive, no data to skip
                    skipDataAndPad(in, size);
                    break;
                }
                case '2': {    // symbolic link
                    try {
                        Files.createDirectories(dest.getParent());
                        if (Files.exists(dest, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                            Files.delete(dest);
                        }
                        Files.createSymbolicLink(dest, java.nio.file.Paths.get(linkTarget));
                    } catch (Exception e) {
                        Log.w(TAG, "Could not create symlink " + name
                                + " → " + linkTarget + ": " + e.getMessage());
                    }
                    skipDataAndPad(in, size);
                    break;
                }
                case '5': {    // directory
                    Files.createDirectories(dest);
                    skipDataAndPad(in, size);
                    break;
                }
                default:
                    skipDataAndPad(in, size);
                    break;
            }
        }
    }

    // ── TAR helpers ───────────────────────────────────────────────────────────

    /** Reads exactly {@link #TAR_BLOCK} bytes into {@code block}. Returns false on EOF. */
    private static boolean readBlock(InputStream in, byte[] block) throws IOException {
        int total = 0;
        while (total < TAR_BLOCK) {
            int n = in.read(block, total, TAR_BLOCK - total);
            if (n < 0) return total > 0;
            total += n;
        }
        return true;
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte b : block) if (b != 0) return false;
        return true;
    }

    private static boolean isUstar(byte[] block) {
        // ustar magic is at offset 257 (6 bytes: "ustar\0" or "ustar ")
        return block[257] == 'u' && block[258] == 's' && block[259] == 't'
                && block[260] == 'a' && block[261] == 'r';
    }

    /**
     * Extracts a NUL-terminated (or space-padded) string from a fixed-width TAR header field.
     */
    private static String extractCString(byte[] block, int offset, int maxLen) {
        int end = offset;
        int limit = offset + maxLen;
        while (end < limit && block[end] != 0) end++;
        return new String(block, offset, end - offset, StandardCharsets.UTF_8);
    }

    /**
     * Parses an octal-encoded number from a TAR header field.
     * Also handles the GNU base-256 extension (first byte has bit 7 set).
     */
    private static long parseOctal(byte[] block, int offset, int length) {
        if ((block[offset] & 0x80) != 0) {
            // Base-256 (big-endian binary, sign bit of first byte)
            long result = 0;
            for (int i = offset + 1; i < offset + length; i++) {
                result = (result << 8) | (block[i] & 0xFF);
            }
            return result;
        }
        long result = 0;
        for (int i = offset; i < offset + length; i++) {
            char c = (char) (block[i] & 0xFF);
            if (c == 0 || c == ' ') break;
            if (c >= '0' && c <= '7') result = result * 8 + (c - '0');
        }
        return result;
    }

    /**
     * Reads {@code size} bytes from {@code in}, converts to a UTF-8 string (trimming NUL bytes),
     * and skips the TAR padding.
     */
    private static String readDataAsString(InputStream in, long size) throws IOException {
        byte[] buf = new byte[(int) size];
        int total = 0;
        while (total < size) {
            int n = in.read(buf, total, (int) (size - total));
            if (n < 0) break;
            total += n;
        }
        skipPad(in, size);
        // Trim trailing NUL bytes
        int end = total;
        while (end > 0 && buf[end - 1] == 0) end--;
        return new String(buf, 0, end, StandardCharsets.UTF_8);
    }

    /** Copies exactly {@code size} bytes from {@code in} to {@code out}. */
    private static void copyData(InputStream in, OutputStream out, long size) throws IOException {
        byte[] buf = new byte[8192];
        long remaining = size;
        while (remaining > 0) {
            int toRead = (int) Math.min(buf.length, remaining);
            int n = in.read(buf, 0, toRead);
            if (n < 0) throw new IOException("Unexpected end of TAR data stream");
            out.write(buf, 0, n);
            remaining -= n;
        }
    }

    /** Skips {@code size} data bytes plus the TAR padding to the next 512-byte boundary. */
    private static void skipDataAndPad(InputStream in, long size) throws IOException {
        if (size > 0) skipFully(in, size);
        skipPad(in, size);
    }

    /** Skips the padding bytes after a TAR entry of {@code dataSize} bytes. */
    private static void skipPad(InputStream in, long dataSize) throws IOException {
        long rem = dataSize % TAR_BLOCK;
        if (rem > 0) skipFully(in, TAR_BLOCK - rem);
    }

    /** Reliably skips exactly {@code count} bytes from {@code in}. */
    private static void skipFully(InputStream in, long count) throws IOException {
        long remaining = count;
        while (remaining > 0) {
            long n = in.skip(remaining);
            if (n > 0) {
                remaining -= n;
            } else {
                // skip() may return 0; fall back to read() to advance one byte
                if (in.read() < 0) {
                    Log.w(TAG, "Premature EOF while skipping TAR padding ("
                            + remaining + " bytes unread of " + count + " expected)");
                    return;
                }
                remaining--;
            }
        }
    }

    // ── File-system helpers ───────────────────────────────────────────────────

    /** Recursively deletes a directory tree, silently ignoring failures on individual paths. */
    static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }
    }
}

