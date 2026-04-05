package io.minelib.modrinth;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * High-level interface for querying and installing mods from Modrinth.
 *
 * <p>The standard implementation is {@link ModrinthModManager}.  Callers obtain an instance
 * from {@link io.minelib.MineLib#getModrinthModManager()}.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * ModManager mods = minelib.getModrinthModManager();
 *
 * // Install Sodium for Fabric 1.21.4
 * mods.installMod("sodium", "1.21.4", "fabric", instanceDir.resolve("mods"));
 *
 * // Install Vivecraft for Fabric 1.21.4 (needed for OpenXR / VR support)
 * mods.installMod("vivecraft", "1.21.4", "fabric", instanceDir.resolve("mods"));
 * }</pre>
 */
public interface ModManager {

    /**
     * Looks up a project by its Modrinth ID or slug.
     *
     * @param idOrSlug the project ID (e.g. {@code "AANobbMI"}) or URL slug
     *                 (e.g. {@code "sodium"})
     * @return the project metadata
     * @throws IOException if the network request fails or the project is not found
     */
    ModrinthProject findProject(String idOrSlug) throws IOException;

    /**
     * Returns all versions of a project that are compatible with the given Minecraft
     * version and mod loader, newest first.
     *
     * @param projectId      the Modrinth project ID or slug
     * @param minecraftVersion the target Minecraft version (e.g. {@code "1.21.4"}),
     *                         or {@code null} to return all versions
     * @param loaderName       the mod loader name (e.g. {@code "fabric"}, {@code "forge"},
     *                         {@code "quilt"}, {@code "neoforge"}),
     *                         or {@code null} for any loader
     * @return list of matching versions, may be empty
     * @throws IOException if the network request fails
     */
    List<ModrinthVersion> listVersions(String projectId,
                                       String minecraftVersion,
                                       String loaderName) throws IOException;

    /**
     * Returns the latest stable release for the given project, Minecraft version, and loader.
     * Falls back to beta then alpha if no release is available.
     *
     * @param projectId        the project ID or slug
     * @param minecraftVersion target Minecraft version, or {@code null} for any
     * @param loaderName       mod loader name, or {@code null} for any
     * @return the best matching version
     * @throws IOException              if the network request fails
     * @throws java.util.NoSuchElementException if no compatible version exists
     */
    ModrinthVersion getLatestVersion(String projectId,
                                     String minecraftVersion,
                                     String loaderName) throws IOException;

    /**
     * Downloads and installs the primary file of a specific version into {@code modsDirectory}.
     *
     * <p>The file is verified against its SHA-1 hash before being moved into place.
     * Skips the download if the file already exists with a matching hash.
     *
     * @param version        the version to install
     * @param modsDirectory  the directory to install the mod file into
     *                       (created if it does not exist)
     * @throws IOException if the download or verification fails
     */
    void installMod(ModrinthVersion version, Path modsDirectory) throws IOException;

    /**
     * Resolves the latest compatible version for the given project + filters and installs it.
     *
     * <p>Convenience wrapper for {@link #getLatestVersion} + {@link #installMod}.
     *
     * @param projectIdOrSlug  the project ID or slug (e.g. {@code "sodium"})
     * @param minecraftVersion target Minecraft version (e.g. {@code "1.21.4"})
     * @param loaderName       mod loader name (e.g. {@code "fabric"})
     * @param modsDirectory    destination directory
     * @throws IOException if the version cannot be resolved or the download fails
     */
    void installMod(String projectIdOrSlug,
                    String minecraftVersion,
                    String loaderName,
                    Path modsDirectory) throws IOException;

    /**
     * Searches Modrinth for mods matching the given query.
     *
     * @param query            free-text search string
     * @param minecraftVersion Minecraft version facet filter, or {@code null} for any
     * @param loaderName       mod loader facet filter, or {@code null} for any
     * @param limit            maximum number of results (1–100)
     * @return list of matching projects
     * @throws IOException if the request fails
     */
    List<ModrinthProject> search(String query,
                                  String minecraftVersion,
                                  String loaderName,
                                  int limit) throws IOException;
}
