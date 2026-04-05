package io.minelib.modrinth;

import java.util.List;

/**
 * A specific release of a Modrinth project, containing one or more downloadable
 * {@link ModrinthFile files} and metadata about compatibility.
 */
public final class ModrinthVersion {

    /** The stability level of a version. */
    public enum VersionType {
        RELEASE, BETA, ALPHA;

        public static VersionType fromApiString(String s) {
            if (s == null) return RELEASE;
            return switch (s.toLowerCase()) {
                case "beta"  -> BETA;
                case "alpha" -> ALPHA;
                default      -> RELEASE;
            };
        }
    }

    private final String id;
    private final String projectId;
    private final String name;
    private final String versionNumber;
    private final String changelog;
    private final List<String> gameVersions;
    private final VersionType versionType;
    private final List<String> loaders;
    private final boolean featured;
    private final List<ModrinthFile> files;
    private final List<ModrinthDependency> dependencies;

    ModrinthVersion(String id, String projectId, String name, String versionNumber,
                    String changelog, List<String> gameVersions, VersionType versionType,
                    List<String> loaders, boolean featured,
                    List<ModrinthFile> files, List<ModrinthDependency> dependencies) {
        this.id           = id;
        this.projectId    = projectId;
        this.name         = name;
        this.versionNumber = versionNumber;
        this.changelog    = changelog;
        this.gameVersions = gameVersions;
        this.versionType  = versionType;
        this.loaders      = loaders;
        this.featured     = featured;
        this.files        = files;
        this.dependencies = dependencies;
    }

    /** Returns the Modrinth version ID (e.g. {@code "IIJJKKLL"}). */
    public String getId() { return id; }

    /** Returns the parent project ID. */
    public String getProjectId() { return projectId; }

    /** Returns the human-readable version name (e.g. {@code "Sodium 0.6.1"}). */
    public String getName() { return name; }

    /** Returns the version string (e.g. {@code "0.6.1+mc1.21.4"}). */
    public String getVersionNumber() { return versionNumber; }

    /** Returns the changelog text, or {@code null} if not provided. */
    public String getChangelog() { return changelog; }

    /** Returns the Minecraft version strings this version supports (e.g. {@code ["1.21.4"]}). */
    public List<String> getGameVersions() { return gameVersions; }

    /** Returns the stability level of this version. */
    public VersionType getVersionType() { return versionType; }

    /** Returns the mod loaders this version supports (e.g. {@code ["fabric", "quilt"]}). */
    public List<String> getLoaders() { return loaders; }

    /** Returns {@code true} if this version is featured on the project page. */
    public boolean isFeatured() { return featured; }

    /** Returns all downloadable files for this version. */
    public List<ModrinthFile> getFiles() { return files; }

    /**
     * Returns the primary file for this version — the one marked as primary by Modrinth —
     * or the first file if no primary is set.
     *
     * @throws IllegalStateException if the version has no files
     */
    public ModrinthFile getPrimaryFile() {
        if (files == null || files.isEmpty()) {
            throw new IllegalStateException("Version " + id + " has no files");
        }
        return files.stream()
                .filter(ModrinthFile::isPrimary)
                .findFirst()
                .orElse(files.get(0));
    }

    /** Returns the dependencies of this version. */
    public List<ModrinthDependency> getDependencies() { return dependencies; }

    @Override
    public String toString() {
        return "ModrinthVersion{id=" + id + ", versionNumber=" + versionNumber
                + ", gameVersions=" + gameVersions + ", loaders=" + loaders + '}';
    }
}
