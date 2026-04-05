package io.minelib.questcraft;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A Minecraft version entry from the QuestCraft {@code mods.json}.
 *
 * <p>Each entry corresponds to a specific Minecraft version (e.g. {@code "1.21.10"}) and
 * contains two lists of mods:
 * <ul>
 *   <li><strong>coreMods</strong> — required mods for VR gameplay (Vivecraft + Fabric API).</li>
 *   <li><strong>defaultMods</strong> — recommended performance and quality-of-life mods
 *       (Sodium, Lithium, etc.).</li>
 * </ul>
 *
 * <p>Instances are immutable and safe for concurrent access.
 */
public final class QuestCraftVersionEntry {

    private final String name;
    private final List<QuestCraftMod> coreMods;
    private final List<QuestCraftMod> defaultMods;

    /**
     * Creates a new version entry.
     *
     * @param name         the Minecraft version name (e.g. {@code "1.21.10"})
     * @param coreMods     required VR mods; must not be {@code null}
     * @param defaultMods  recommended performance mods; must not be {@code null}
     */
    public QuestCraftVersionEntry(String name,
                                  List<QuestCraftMod> coreMods,
                                  List<QuestCraftMod> defaultMods) {
        this.name = Objects.requireNonNull(name, "name");
        this.coreMods = Collections.unmodifiableList(
                Objects.requireNonNull(coreMods, "coreMods"));
        this.defaultMods = Collections.unmodifiableList(
                Objects.requireNonNull(defaultMods, "defaultMods"));
    }

    /**
     * Returns the Minecraft version name (e.g. {@code "1.21.10"}).
     */
    public String getName() { return name; }

    /**
     * Returns the required VR mods (Vivecraft, Fabric API).
     * The list is unmodifiable.
     */
    public List<QuestCraftMod> getCoreMods() { return coreMods; }

    /**
     * Returns the recommended performance mods (Sodium, Lithium, etc.).
     * The list is unmodifiable.
     */
    public List<QuestCraftMod> getDefaultMods() { return defaultMods; }

    @Override
    public String toString() {
        return "QuestCraftVersionEntry{name='" + name + "', coreMods=" + coreMods.size()
                + ", defaultMods=" + defaultMods.size() + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuestCraftVersionEntry other)) return false;
        return name.equals(other.name)
                && coreMods.equals(other.coreMods)
                && defaultMods.equals(other.defaultMods);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, coreMods, defaultMods);
    }
}
