package io.minelib.launcher.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a single Minelib launcher instance.
 *
 * <p>Each instance has its own isolated game directory, Minecraft version,
 * and optional mod loader, allowing multiple configurations to coexist
 * side-by-side.
 *
 * <p>Instances are persisted as {@code instance.json} files under
 * {@code <launcherDir>/instances/<id>/instance.json}.
 */
public final class Instance {

    /** Supported mod loaders. */
    public enum ModLoader { VANILLA, FABRIC, FORGE, NEOFORGE }

    private String id;
    private String name;
    private String minecraftVersion;
    private ModLoader modLoader;
    private long lastPlayedMs;
    /** Maximum JVM heap in megabytes used when launching Minecraft (default 2048). */
    private int memoryMb = 2048;

    public Instance(String id, String name, String minecraftVersion, ModLoader modLoader) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        this.modLoader = Objects.requireNonNull(modLoader, "modLoader");
        this.lastPlayedMs = 0;
    }

    /** Returns the unique identifier (UUID string) for this instance. */
    public String getId() { return id; }

    /** Returns the human-readable display name. */
    public String getName() { return name; }

    /** Sets the display name. */
    public void setName(String name) { this.name = Objects.requireNonNull(name, "name"); }

    /** Returns the Minecraft version identifier (e.g. {@code "1.21.4"}). */
    public String getMinecraftVersion() { return minecraftVersion; }

    /** Sets the Minecraft version. */
    public void setMinecraftVersion(String v) { this.minecraftVersion = Objects.requireNonNull(v, "v"); }

    /** Returns the mod loader for this instance. */
    public ModLoader getModLoader() { return modLoader; }

    /** Sets the mod loader. */
    public void setModLoader(ModLoader ml) { this.modLoader = Objects.requireNonNull(ml, "ml"); }

    /** Returns the epoch-millisecond timestamp of the last play session, or {@code 0} if never played. */
    public long getLastPlayedMs() { return lastPlayedMs; }

    /** Updates the last-played timestamp to the current time. */
    public void markPlayed() { this.lastPlayedMs = System.currentTimeMillis(); }

    /** Returns the maximum JVM heap size in megabytes for this instance (at least 512). */
    public int getMemoryMb() { return memoryMb <= 0 ? 2048 : memoryMb; }

    /** Sets the maximum JVM heap size in megabytes (clamped to minimum 512 MB). */
    public void setMemoryMb(int mb) { this.memoryMb = Math.max(512, mb); }

    @Override
    public String toString() {
        return "Instance{id=" + id + ", name=" + name + ", version=" + minecraftVersion
                + ", loader=" + modLoader + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Instance other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}
