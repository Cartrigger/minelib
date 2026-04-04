package io.minelib.modrinth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Low-level client for the
 * <a href="https://docs.modrinth.com/api/">Modrinth API v2</a>.
 *
 * <p>All methods perform synchronous HTTP requests on the calling thread.  For
 * asynchronous use, wrap calls with {@link java.util.concurrent.CompletableFuture}.
 *
 * <p>The Modrinth API requires a descriptive {@code User-Agent} header; minelib sets this
 * to {@value #USER_AGENT}.
 */
public final class ModrinthClient {

    static final String API_BASE   = "https://api.modrinth.com/v2";
    static final String USER_AGENT =
            "io.minelib/minelib (https://github.com/Cartrigger/minelib)";

    private final OkHttpClient httpClient;

    /** Gson configured for Modrinth's snake_case JSON field names. */
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(
                    com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    /**
     * Creates a {@code ModrinthClient} using the provided OkHttp client.
     * Obtain the client from {@link io.minelib.download.DownloadManager#getHttpClient()}
     * to share the connection pool.
     */
    public ModrinthClient(OkHttpClient httpClient) {
        if (httpClient == null) throw new NullPointerException("httpClient must not be null");
        this.httpClient = httpClient;
    }

    // -------------------------------------------------------------------------
    // Project
    // -------------------------------------------------------------------------

    /**
     * Returns the project with the given Modrinth ID or URL slug.
     *
     * @param idOrSlug project ID (e.g. {@code "AANobbMI"}) or slug (e.g. {@code "sodium"})
     * @throws IOException if the request fails or the project is not found (HTTP 404)
     */
    public ModrinthProject getProject(String idOrSlug) throws IOException {
        JsonObject obj = getJson(API_BASE + "/project/" + idOrSlug).getAsJsonObject();
        return parseProject(obj);
    }

    // -------------------------------------------------------------------------
    // Versions
    // -------------------------------------------------------------------------

    /**
     * Returns all versions for the given project that are compatible with the specified
     * Minecraft version and mod loader, newest first.
     *
     * @param projectId        project ID or slug
     * @param minecraftVersion Minecraft version filter (e.g. {@code "1.21.4"}),
     *                         or {@code null} for any
     * @param loaderName       mod loader filter (e.g. {@code "fabric"}),
     *                         or {@code null} for any
     */
    public List<ModrinthVersion> getVersions(String projectId,
                                              String minecraftVersion,
                                              String loaderName) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(API_BASE + "/project/" + projectId + "/version")
                .newBuilder();

        // Modrinth expects JSON-encoded arrays: ?game_versions=["1.21.4"]&loaders=["fabric"]
        if (minecraftVersion != null) {
            urlBuilder.addQueryParameter("game_versions",
                    "[\"" + minecraftVersion + "\"]");
        }
        if (loaderName != null) {
            urlBuilder.addQueryParameter("loaders",
                    "[\"" + loaderName.toLowerCase() + "\"]");
        }

        JsonArray arr = getJson(urlBuilder.build().toString()).getAsJsonArray();
        List<ModrinthVersion> versions = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            versions.add(parseVersion(el.getAsJsonObject()));
        }
        return versions;
    }

    /**
     * Returns a single version by its Modrinth version ID.
     *
     * @param versionId the version ID (e.g. {@code "IIJJKKLL"})
     */
    public ModrinthVersion getVersion(String versionId) throws IOException {
        JsonObject obj = getJson(API_BASE + "/version/" + versionId).getAsJsonObject();
        return parseVersion(obj);
    }

    // -------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------

    /**
     * Searches Modrinth for projects matching {@code query}.
     *
     * <p>Results are returned in relevance order.  Facet filters are applied when
     * {@code minecraftVersion} or {@code loaderName} are non-null.
     *
     * @param query            free-text search term
     * @param minecraftVersion Minecraft version facet, or {@code null}
     * @param loaderName       loader facet (e.g. {@code "fabric"}), or {@code null}
     * @param limit            number of results (1–100)
     */
    public List<ModrinthProject> search(String query,
                                         String minecraftVersion,
                                         String loaderName,
                                         int limit) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(API_BASE + "/search").newBuilder()
                .addQueryParameter("query", query != null ? query : "")
                .addQueryParameter("limit",  String.valueOf(Math.min(100, Math.max(1, limit))));

        // Build facets array: [["versions:1.21.4"],["categories:fabric"]]
        List<String> facets = new ArrayList<>();
        if (minecraftVersion != null) {
            facets.add("[\"versions:" + minecraftVersion + "\"]");
        }
        if (loaderName != null) {
            facets.add("[\"categories:" + loaderName.toLowerCase() + "\"]");
        }
        if (!facets.isEmpty()) {
            urlBuilder.addQueryParameter("facets", "[" + String.join(",", facets) + "]");
        }

        JsonObject root = getJson(urlBuilder.build().toString()).getAsJsonObject();
        JsonArray hits  = root.getAsJsonArray("hits");
        List<ModrinthProject> results = new ArrayList<>(hits.size());
        for (JsonElement el : hits) {
            results.add(parseSearchHit(el.getAsJsonObject()));
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    private JsonElement getJson(String url) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build();
        try (Response resp = httpClient.newCall(req).execute()) {
            if (resp.code() == 404) {
                throw new IOException("Modrinth: not found — " + url);
            }
            if (!resp.isSuccessful()) {
                throw new IOException(
                        "Modrinth: HTTP " + resp.code() + " for " + url);
            }
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("Modrinth: empty response for " + url);
            return GSON.fromJson(body.string(), JsonElement.class);
        }
    }

    // -------------------------------------------------------------------------
    // Parsers — Modrinth returns snake_case JSON; GSON maps it to camelCase fields
    // -------------------------------------------------------------------------

    private static ModrinthProject parseProject(JsonObject o) {
        return new ModrinthProject(
                string(o, "id"),
                string(o, "slug"),
                ModrinthProject.ProjectType.fromApiString(string(o, "project_type")),
                string(o, "title"),
                string(o, "description"),
                string(o, "icon_url"),
                longVal(o, "downloads"),
                longVal(o, "followers"),
                stringList(o, "categories"),
                stringList(o, "game_versions"),
                stringList(o, "loaders"),
                stringList(o, "versions")
        );
    }

    /** Parses a search-result hit (uses {@code project_id} instead of {@code id}). */
    private static ModrinthProject parseSearchHit(JsonObject o) {
        return new ModrinthProject(
                string(o, "project_id"),
                string(o, "slug"),
                ModrinthProject.ProjectType.fromApiString(string(o, "project_type")),
                string(o, "title"),
                string(o, "description"),
                string(o, "icon_url"),
                longVal(o, "downloads"),
                longVal(o, "follows"),          // search hits use "follows" not "followers"
                stringList(o, "categories"),
                stringList(o, "versions"),      // game versions the project supports
                stringList(o, "display_categories"),
                List.of()                       // version IDs not provided in search hits
        );
    }

    private static ModrinthVersion parseVersion(JsonObject o) {
        List<ModrinthFile> files = new ArrayList<>();
        if (o.has("files")) {
            for (JsonElement el : o.getAsJsonArray("files")) {
                files.add(parseFile(el.getAsJsonObject()));
            }
        }
        List<ModrinthDependency> deps = new ArrayList<>();
        if (o.has("dependencies")) {
            for (JsonElement el : o.getAsJsonArray("dependencies")) {
                deps.add(parseDependency(el.getAsJsonObject()));
            }
        }
        return new ModrinthVersion(
                string(o, "id"),
                string(o, "project_id"),
                string(o, "name"),
                string(o, "version_number"),
                string(o, "changelog"),
                stringList(o, "game_versions"),
                ModrinthVersion.VersionType.fromApiString(string(o, "version_type")),
                stringList(o, "loaders"),
                boolVal(o, "featured"),
                files,
                deps
        );
    }

    private static ModrinthFile parseFile(JsonObject o) {
        Map<String, String> hashes = null;
        if (o.has("hashes")) {
            JsonObject h = o.getAsJsonObject("hashes");
            hashes = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : h.entrySet()) {
                hashes.put(e.getKey(), e.getValue().getAsString());
            }
        }
        return new ModrinthFile(
                hashes,
                string(o, "url"),
                string(o, "filename"),
                boolVal(o, "primary"),
                longVal(o, "size")
        );
    }

    private static ModrinthDependency parseDependency(JsonObject o) {
        return new ModrinthDependency(
                string(o, "version_id"),
                string(o, "project_id"),
                string(o, "file_name"),
                ModrinthDependency.Type.fromApiString(string(o, "dependency_type"))
        );
    }

    // -------------------------------------------------------------------------
    // JSON helpers
    // -------------------------------------------------------------------------

    private static String string(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return (el == null || el.isJsonNull()) ? null : el.getAsString();
    }

    private static long longVal(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return (el == null || el.isJsonNull()) ? 0L : el.getAsLong();
    }

    private static boolean boolVal(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el != null && !el.isJsonNull() && el.getAsBoolean();
    }

    private static List<String> stringList(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return List.of();
        List<String> list = new ArrayList<>();
        for (JsonElement el : o.getAsJsonArray(key)) {
            list.add(el.getAsString());
        }
        return list;
    }
}
