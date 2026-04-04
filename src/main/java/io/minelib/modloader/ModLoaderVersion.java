package io.minelib.modloader;

/**
 * Represents a specific installable version of a mod loader for a given Minecraft version.
 */
public final class ModLoaderVersion {

    private final ModLoader loader;
    private final String minecraftVersion;
    private final String loaderVersion;
    private final boolean stable;

    private ModLoaderVersion(Builder builder) {
        this.loader = builder.loader;
        this.minecraftVersion = builder.minecraftVersion;
        this.loaderVersion = builder.loaderVersion;
        this.stable = builder.stable;
    }

    /** Returns the mod loader this version belongs to. */
    public ModLoader getLoader() {
        return loader;
    }

    /** Returns the Minecraft version this loader version targets (e.g. {@code "1.21.4"}). */
    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    /**
     * Returns the loader version string (e.g. {@code "0.16.10"} for Fabric,
     * {@code "51.0.33"} for Forge, {@code "21.4.93"} for NeoForge).
     */
    public String getLoaderVersion() {
        return loaderVersion;
    }

    /** Returns {@code true} if this is a stable (non-beta, non-snapshot) release. */
    public boolean isStable() {
        return stable;
    }

    /**
     * Returns a human-readable version identifier combining all three components,
     * e.g. {@code "FABRIC 0.16.10 for 1.21.4"}.
     */
    @Override
    public String toString() {
        return loader + " " + loaderVersion + " for " + minecraftVersion;
    }

    /** Creates a new builder for {@link ModLoaderVersion}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link ModLoaderVersion}. */
    public static final class Builder {

        private ModLoader loader;
        private String minecraftVersion;
        private String loaderVersion;
        private boolean stable = true;

        private Builder() {}

        public Builder loader(ModLoader loader) { this.loader = loader; return this; }
        public Builder minecraftVersion(String minecraftVersion) { this.minecraftVersion = minecraftVersion; return this; }
        public Builder loaderVersion(String loaderVersion) { this.loaderVersion = loaderVersion; return this; }
        public Builder stable(boolean stable) { this.stable = stable; return this; }

        public ModLoaderVersion build() {
            if (loader == null) throw new IllegalStateException("loader must be set");
            if (minecraftVersion == null || minecraftVersion.isBlank()) throw new IllegalStateException("minecraftVersion must be set");
            if (loaderVersion == null || loaderVersion.isBlank()) throw new IllegalStateException("loaderVersion must be set");
            return new ModLoaderVersion(this);
        }
    }
}
