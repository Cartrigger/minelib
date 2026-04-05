package io.minelib.questcraft;

import java.util.Objects;

/**
 * A single mod entry from the QuestCraft {@code mods.json}.
 *
 * <p>Each entry carries a human-readable slug (e.g. {@code "Vivecraft"}), a version string,
 * and a direct download URL pointing to the mod JAR file.
 *
 * <p>Instances are immutable and safe for concurrent access.
 */
public final class QuestCraftMod {

    private final String slug;
    private final String version;
    private final String downloadLink;

    /**
     * Creates a new mod descriptor.
     *
     * @param slug         human-readable identifier (e.g. {@code "Vivecraft"})
     * @param version      version string (e.g. {@code "1.3.4.2"})
     * @param downloadLink direct URL to the mod JAR
     */
    public QuestCraftMod(String slug, String version, String downloadLink) {
        this.slug = Objects.requireNonNull(slug, "slug");
        this.version = Objects.requireNonNull(version, "version");
        this.downloadLink = Objects.requireNonNull(downloadLink, "downloadLink");
    }

    /** Returns the human-readable mod identifier (e.g. {@code "Vivecraft"}). */
    public String getSlug() { return slug; }

    /** Returns the mod version string (e.g. {@code "1.3.4.2"}). */
    public String getVersion() { return version; }

    /** Returns the direct URL to the mod JAR file. */
    public String getDownloadLink() { return downloadLink; }

    @Override
    public String toString() {
        return "QuestCraftMod{slug='" + slug + "', version='" + version + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuestCraftMod other)) return false;
        return slug.equals(other.slug)
                && version.equals(other.version)
                && downloadLink.equals(other.downloadLink);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slug, version, downloadLink);
    }
}
