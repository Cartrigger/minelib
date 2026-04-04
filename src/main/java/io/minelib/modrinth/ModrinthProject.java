package io.minelib.modrinth;

import java.util.List;

/**
 * Metadata for a Modrinth project (mod, modpack, resource pack, or shader).
 *
 * <p>Instances are returned by {@link ModrinthClient#getProject(String)} and
 * {@link ModrinthClient#search}.
 */
public final class ModrinthProject {

    /** The type of content this project provides. */
    public enum ProjectType {
        MOD, MODPACK, RESOURCEPACK, SHADER, DATAPACK, PLUGIN;

        public static ProjectType fromApiString(String s) {
            if (s == null) return MOD;
            return switch (s.toLowerCase()) {
                case "modpack"      -> MODPACK;
                case "resourcepack" -> RESOURCEPACK;
                case "shader"       -> SHADER;
                case "datapack"     -> DATAPACK;
                case "plugin"       -> PLUGIN;
                default             -> MOD;
            };
        }
    }

    private final String id;
    private final String slug;
    private final ProjectType projectType;
    private final String title;
    private final String description;
    private final String iconUrl;
    private final long   downloads;
    private final long   followers;
    private final List<String> categories;
    private final List<String> gameVersions;
    private final List<String> loaders;
    private final List<String> versions;

    ModrinthProject(String id, String slug, ProjectType projectType, String title,
                    String description, String iconUrl, long downloads, long followers,
                    List<String> categories, List<String> gameVersions,
                    List<String> loaders, List<String> versions) {
        this.id           = id;
        this.slug         = slug;
        this.projectType  = projectType;
        this.title        = title;
        this.description  = description;
        this.iconUrl      = iconUrl;
        this.downloads    = downloads;
        this.followers    = followers;
        this.categories   = categories;
        this.gameVersions = gameVersions;
        this.loaders      = loaders;
        this.versions     = versions;
    }

    /** Returns the Modrinth project ID (e.g. {@code "AANobbMI"}). */
    public String getId() { return id; }

    /** Returns the URL slug (e.g. {@code "sodium"}). */
    public String getSlug() { return slug; }

    /** Returns the type of content this project provides. */
    public ProjectType getProjectType() { return projectType; }

    /** Returns the human-readable project title. */
    public String getTitle() { return title; }

    /** Returns the short description. */
    public String getDescription() { return description; }

    /** Returns the CDN URL for the project icon, or {@code null} if not set. */
    public String getIconUrl() { return iconUrl; }

    /** Returns the total download count. */
    public long getDownloads() { return downloads; }

    /** Returns the follower count. */
    public long getFollowers() { return followers; }

    /** Returns the categories this project belongs to (e.g. {@code ["optimization"]}). */
    public List<String> getCategories() { return categories; }

    /** Returns all Minecraft versions this project has a release for. */
    public List<String> getGameVersions() { return gameVersions; }

    /** Returns the mod loaders this project supports (e.g. {@code ["fabric", "quilt"]}). */
    public List<String> getLoaders() { return loaders; }

    /**
     * Returns all version IDs for this project in newest-first order.
     * Use {@link ModrinthClient#getVersion(String)} or
     * {@link ModrinthClient#getVersions} to fetch the full version objects.
     */
    public List<String> getVersions() { return versions; }

    @Override
    public String toString() {
        return "ModrinthProject{id=" + id + ", slug=" + slug
                + ", title=" + title + ", type=" + projectType + '}';
    }
}
