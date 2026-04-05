package io.minelib.version;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * Represents the full metadata for a single Minecraft version as returned by
 * Mojang's version manifest API.
 *
 * <p>The fields here directly mirror the JSON structure of a version's
 * {@code <id>.json} descriptor file, and are populated by Gson during
 * deserialisation.
 */
public final class VersionInfo {

    private String id;
    private String inheritsFrom;
    private String type;
    private String mainClass;
    private String minecraftArguments;
    private Arguments arguments;
    private AssetIndex assetIndex;
    private String assets;
    private JavaVersionSpec javaVersion;
    private List<Library> libraries;
    private Downloads downloads;

    /** Returns the version identifier (e.g. {@code "1.21"}). */
    public String getId() {
        return id;
    }

    /**
     * Returns the version ID that this profile inherits from, or {@code null} for standalone
     * (vanilla) profiles. Fabric, Forge, and NeoForge profiles set this to the base
     * Minecraft version (e.g. {@code "1.21.4"}).
     */
    public String getInheritsFrom() {
        return inheritsFrom;
    }

    /** Returns the release type: {@code "release"}, {@code "snapshot"}, etc. */
    public String getType() {
        return type;
    }

    /** Returns the main class to invoke when launching the game. */
    public String getMainClass() {
        return mainClass;
    }

    /**
     * Returns the legacy (pre-1.13) flat argument string, or {@code null} for newer versions
     * that use the structured {@link Arguments} format.
     */
    public String getMinecraftArguments() {
        return minecraftArguments;
    }

    /** Returns the structured launch arguments used by Minecraft 1.13+. */
    public Arguments getArguments() {
        return arguments;
    }

    /** Returns metadata about the asset index for this version. */
    public AssetIndex getAssetIndex() {
        return assetIndex;
    }

    /** Returns the asset index identifier (e.g. {@code "17"}). */
    public String getAssets() {
        return assets;
    }

    /**
     * Returns the major Java version required to run this version of Minecraft
     * (e.g. {@code 21} for 1.21+, {@code 17} for 1.18–1.20).
     *
     * <p>Mojang's version JSON encodes this as a nested object:
     * {@code "javaVersion": {"component": "java-runtime-gamma", "majorVersion": 21}}.
     * Returns {@code 8} as a safe fallback when the field is absent (pre-1.7.10 versions
     * that pre-date the structured format).
     */
    public int getJavaVersion() {
        return javaVersion != null ? javaVersion.getMajorVersion() : 8;
    }

    /**
     * Returns the raw {@link JavaVersionSpec} object from the version JSON, or {@code null}
     * for very old versions that pre-date the field.
     */
    public JavaVersionSpec getJavaVersionSpec() {
        return javaVersion;
    }

    /** Returns the list of library dependencies for this version. */
    public List<Library> getLibraries() {
        return libraries;
    }

    /** Returns the client and server download descriptors for this version. */
    public Downloads getDownloads() {
        return downloads;
    }

    // -------------------------------------------------------------------------
    // Nested types
    // -------------------------------------------------------------------------

    /**
     * The {@code javaVersion} object in a Minecraft version JSON descriptor.
     *
     * <p>Example JSON:
     * <pre>{@code
     * "javaVersion": {
     *   "component": "java-runtime-gamma",
     *   "majorVersion": 21
     * }
     * }</pre>
     *
     * <p>This object was introduced in Minecraft 1.7+ and is present in all modern versions.
     * Very old versions (pre-1.7.10) omit it entirely; {@link VersionInfo#getJavaVersion()}
     * returns {@code 8} in that case.
     */
    public static final class JavaVersionSpec {
        private String component;
        private int majorVersion;

        /** Returns the Mojang component name (e.g. {@code "java-runtime-gamma"}). */
        public String getComponent() { return component; }

        /**
         * Returns the required Java major version (e.g. {@code 21} for 1.21+,
         * {@code 17} for 1.18–1.20, {@code 8} for 1.16 and below).
         */
        public int getMajorVersion() { return majorVersion; }
    }

    /** Structured arguments for Minecraft 1.13+ (game and JVM arguments). */
    public static final class Arguments {
        private List<Object> game;
        private List<Object> jvm;

        public List<Object> getGame() { return game; }
        public List<Object> getJvm() { return jvm; }
    }

    /** Asset index descriptor embedded in a version's JSON. */
    public static final class AssetIndex {
        private String id;
        private String sha1;
        private long size;
        private long totalSize;
        private String url;

        public String getId() { return id; }
        public String getSha1() { return sha1; }
        public long getSize() { return size; }
        public long getTotalSize() { return totalSize; }
        public String getUrl() { return url; }
    }

    /** A single library dependency. */
    public static final class Library {
        private String name;
        private LibraryDownloads downloads;
        private List<Rule> rules;
        private Natives natives;
        /**
         * Base Maven repository URL used when the library does not include a full
         * {@link LibraryDownloads} block. Fabric and Forge profiles use this format.
         * The download URL is constructed as:
         * {@code <url><group/artifact/version/artifact-version.jar>}.
         */
        private String url;

        public String getName() { return name; }
        public LibraryDownloads getDownloads() { return downloads; }
        public List<Rule> getRules() { return rules; }
        public Natives getNatives() { return natives; }
        public String getUrl() { return url; }
    }

    /** Download descriptors for a library (artifact + optional classifiers). */
    public static final class LibraryDownloads {
        private Artifact artifact;
        private Map<String, Artifact> classifiers;

        public Artifact getArtifact() { return artifact; }
        public Map<String, Artifact> getClassifiers() { return classifiers; }
    }

    /** A single downloadable artifact with URL, SHA-1, size, and path. */
    public static final class Artifact {
        private String path;
        private String sha1;
        private long size;
        private String url;

        public String getPath() { return path; }
        public String getSha1() { return sha1; }
        public long getSize() { return size; }
        public String getUrl() { return url; }
    }

    /** OS/feature-based rule for conditionally including a library. */
    public static final class Rule {
        private String action;
        private OsCondition os;
        private Map<String, Boolean> features;

        public String getAction() { return action; }
        public OsCondition getOs() { return os; }
        public Map<String, Boolean> getFeatures() { return features; }
    }

    /** Narrows a {@link Rule} to a specific operating system. */
    public static final class OsCondition {
        private String name;
        private String arch;
        private String version;

        public String getName() { return name; }
        public String getArch() { return arch; }
        public String getVersion() { return version; }
    }

    /** Maps OS names to their native classifier suffixes. */
    public static final class Natives {
        private String linux;
        private String osx;
        private String windows;

        public String getLinux() { return linux; }
        public String getOsx() { return osx; }
        public String getWindows() { return windows; }
    }

    /** Top-level download descriptors (client jar, server jar). */
    public static final class Downloads {
        private Artifact client;
        private Artifact server;

        @SerializedName("client_mappings")
        private Artifact clientMappings;

        public Artifact getClient() { return client; }
        public Artifact getServer() { return server; }
        public Artifact getClientMappings() { return clientMappings; }
    }
}
