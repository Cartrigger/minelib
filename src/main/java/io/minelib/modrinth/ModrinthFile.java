package io.minelib.modrinth;

import java.util.Map;

/**
 * A single downloadable file within a {@link ModrinthVersion}.
 *
 * <p>Each version may have one or more files; the primary file is identified by
 * {@link #isPrimary()}.  SHA-512 and SHA-1 hashes are provided for integrity verification.
 */
public final class ModrinthFile {

    private final Map<String, String> hashes;
    private final String url;
    private final String filename;
    private final boolean primary;
    private final long size;

    ModrinthFile(Map<String, String> hashes, String url, String filename,
                 boolean primary, long size) {
        this.hashes   = hashes;
        this.url      = url;
        this.filename = filename;
        this.primary  = primary;
        this.size     = size;
    }

    /**
     * Returns the hash map for this file.  Keys are hash algorithm names
     * (e.g. {@code "sha512"}, {@code "sha1"}); values are lowercase hex strings.
     */
    public Map<String, String> getHashes() { return hashes; }

    /** Returns the SHA-512 hex digest, or {@code null} if not present. */
    public String getSha512() {
        return hashes != null ? hashes.get("sha512") : null;
    }

    /** Returns the SHA-1 hex digest, or {@code null} if not present. */
    public String getSha1() {
        return hashes != null ? hashes.get("sha1") : null;
    }

    /** Returns the CDN download URL for this file. */
    public String getUrl() { return url; }

    /** Returns the file name (e.g. {@code "sodium-fabric-0.6.1+mc1.21.4.jar"}). */
    public String getFilename() { return filename; }

    /** Returns {@code true} if this is the primary (recommended) download for the version. */
    public boolean isPrimary() { return primary; }

    /** Returns the file size in bytes. */
    public long getSize() { return size; }

    @Override
    public String toString() {
        return "ModrinthFile{filename=" + filename + ", primary=" + primary
                + ", size=" + size + '}';
    }
}
