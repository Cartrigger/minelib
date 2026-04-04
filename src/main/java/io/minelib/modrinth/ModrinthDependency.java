package io.minelib.modrinth;

/**
 * Describes a dependency that a {@link ModrinthVersion} requires, optionally includes,
 * embeds, or is incompatible with.
 */
public final class ModrinthDependency {

    /** The type of relationship between two versions or projects. */
    public enum Type {
        /** The dependency must be present for the version to function. */
        REQUIRED,
        /** The dependency enhances the version but is not mandatory. */
        OPTIONAL,
        /** The dependency cannot be installed alongside this version. */
        INCOMPATIBLE,
        /** The dependency is bundled inside this version's JAR. */
        EMBEDDED;

        /**
         * Parses a Modrinth API {@code dependency_type} string (e.g. {@code "required"})
         * into a {@link Type}, or returns {@link #OPTIONAL} as a safe fallback.
         */
        public static Type fromApiString(String s) {
            if (s == null) return OPTIONAL;
            return switch (s.toLowerCase()) {
                case "required"      -> REQUIRED;
                case "incompatible"  -> INCOMPATIBLE;
                case "embedded"      -> EMBEDDED;
                default              -> OPTIONAL;
            };
        }
    }

    private final String versionId;
    private final String projectId;
    private final String fileName;
    private final Type   dependencyType;

    ModrinthDependency(String versionId, String projectId, String fileName,
                       Type dependencyType) {
        this.versionId      = versionId;
        this.projectId      = projectId;
        this.fileName       = fileName;
        this.dependencyType = dependencyType;
    }

    /**
     * Returns the specific version ID this dependency pins, or {@code null} if only the
     * project is constrained (any version of the project is acceptable).
     */
    public String getVersionId() { return versionId; }

    /** Returns the Modrinth project ID of the dependency, or {@code null}. */
    public String getProjectId() { return projectId; }

    /** Returns the expected file name for an external dependency, or {@code null}. */
    public String getFileName() { return fileName; }

    /** Returns the dependency relationship type. */
    public Type getDependencyType() { return dependencyType; }

    @Override
    public String toString() {
        return "ModrinthDependency{projectId=" + projectId
                + ", versionId=" + versionId
                + ", type=" + dependencyType + '}';
    }
}
