package io.minelib.version;

import java.util.List;

/**
 * Represents the top-level Mojang version manifest at
 * {@code https://launchermeta.mojang.com/mc/game/version_manifest_v2.json}.
 *
 * <p>The manifest lists every available Minecraft version together with a URL from which its
 * full version descriptor ({@link VersionInfo}) can be downloaded.
 */
public final class VersionManifest {

    /** URL of the official Mojang version manifest (v2). */
    public static final String MANIFEST_URL =
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    private Latest latest;
    private List<VersionEntry> versions;

    /** Returns the identifiers of the latest release and snapshot. */
    public Latest getLatest() {
        return latest;
    }

    /** Returns the full list of available versions, newest first. */
    public List<VersionEntry> getVersions() {
        return versions;
    }

    // -------------------------------------------------------------------------
    // Nested types
    // -------------------------------------------------------------------------

    /** Identifiers of the currently recommended release and snapshot versions. */
    public static final class Latest {
        private String release;
        private String snapshot;

        public String getRelease() { return release; }
        public String getSnapshot() { return snapshot; }
    }

    /**
     * Summary entry for a single Minecraft version as it appears in the manifest list.
     * Use {@link VersionManager#downloadVersion(String)} to resolve the full
     * {@link VersionInfo}.
     */
    public static final class VersionEntry {
        private String id;
        private String type;
        private String url;
        private String time;
        private String releaseTime;
        private String sha1;

        public String getId() { return id; }
        /** Returns the release type: {@code "release"}, {@code "snapshot"}, {@code "old_beta"}, etc. */
        public String getType() { return type; }
        /** Returns the URL from which the full {@link VersionInfo} JSON can be downloaded. */
        public String getUrl() { return url; }
        public String getTime() { return time; }
        public String getReleaseTime() { return releaseTime; }
        public String getSha1() { return sha1; }
    }
}
