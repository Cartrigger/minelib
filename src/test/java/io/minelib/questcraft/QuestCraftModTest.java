package io.minelib.questcraft;

import com.google.gson.Gson;
import io.minelib.download.DownloadManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the QuestCraft model classes and {@link QuestCraftModManager} JSON parsing.
 */
class QuestCraftModTest {

    // -------------------------------------------------------------------------
    // QuestCraftMod tests
    // -------------------------------------------------------------------------

    @Test
    void modGettersReturnConstructorValues() {
        QuestCraftMod mod = new QuestCraftMod("Vivecraft", "1.3.4.2",
                "https://example.com/Vivecraft.jar");
        assertEquals("Vivecraft", mod.getSlug());
        assertEquals("1.3.4.2", mod.getVersion());
        assertEquals("https://example.com/Vivecraft.jar", mod.getDownloadLink());
    }

    @Test
    void modToStringContainsSlugAndVersion() {
        QuestCraftMod mod = new QuestCraftMod("Sodium", "mc1.21-0.7.3", "https://example.com");
        String s = mod.toString();
        assertTrue(s.contains("Sodium"), "toString should contain slug");
        assertTrue(s.contains("mc1.21-0.7.3"), "toString should contain version");
    }

    @Test
    void modEquality() {
        QuestCraftMod a = new QuestCraftMod("Sodium", "1.0", "https://a.example.com");
        QuestCraftMod b = new QuestCraftMod("Sodium", "1.0", "https://a.example.com");
        QuestCraftMod c = new QuestCraftMod("Lithium", "1.0", "https://a.example.com");
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void modRejectsNullArguments() {
        assertThrows(NullPointerException.class,
                () -> new QuestCraftMod(null, "1.0", "https://example.com"));
        assertThrows(NullPointerException.class,
                () -> new QuestCraftMod("Slug", null, "https://example.com"));
        assertThrows(NullPointerException.class,
                () -> new QuestCraftMod("Slug", "1.0", null));
    }

    // -------------------------------------------------------------------------
    // QuestCraftVersionEntry tests
    // -------------------------------------------------------------------------

    @Test
    void versionEntryGettersReturnConstructorValues() {
        QuestCraftMod core = new QuestCraftMod("Vivecraft", "1.0", "https://example.com");
        QuestCraftMod opt = new QuestCraftMod("Sodium", "1.0", "https://example.com");
        QuestCraftVersionEntry entry = new QuestCraftVersionEntry(
                "1.21.10", List.of(core), List.of(opt));

        assertEquals("1.21.10", entry.getName());
        assertEquals(1, entry.getCoreMods().size());
        assertEquals(1, entry.getDefaultMods().size());
        assertEquals(core, entry.getCoreMods().get(0));
        assertEquals(opt, entry.getDefaultMods().get(0));
    }

    @Test
    void versionEntryListsAreUnmodifiable() {
        QuestCraftVersionEntry entry = new QuestCraftVersionEntry(
                "1.21.10", List.of(), List.of());
        assertThrows(UnsupportedOperationException.class,
                () -> entry.getCoreMods().add(
                        new QuestCraftMod("X", "1", "https://example.com")));
        assertThrows(UnsupportedOperationException.class,
                () -> entry.getDefaultMods().add(
                        new QuestCraftMod("X", "1", "https://example.com")));
    }

    @Test
    void versionEntryToStringContainsName() {
        QuestCraftVersionEntry entry = new QuestCraftVersionEntry(
                "1.21.10", List.of(), List.of());
        assertTrue(entry.toString().contains("1.21.10"));
    }

    // -------------------------------------------------------------------------
    // QuestCraftModManager JSON parsing tests
    // -------------------------------------------------------------------------

    private static final String SAMPLE_JSON = """
            {
              "versions": [
                {
                  "name": "1.21.10",
                  "coreMods": [
                    {
                      "slug": "Vivecraft",
                      "version": "1.3.4.2",
                      "download_link": "https://github.com/QuestCraftPlusPlus/VivecraftMod/releases/download/v6.0.1-1.21.10/Vivecraft.jar"
                    },
                    {
                      "slug": "Fabric-API",
                      "version": "0.138.4+1.21.10",
                      "download_link": "https://cdn.modrinth.com/data/P7dR8mSH/versions/tV4Gc0Zo/fabric-api-0.138.4+1.21.10.jar"
                    }
                  ],
                  "defaultMods": [
                    {
                      "slug": "Sodium",
                      "version": "mc1.21.10-0.7.3-fabric",
                      "download_link": "https://cdn.modrinth.com/data/AANobbMI/versions/sFfidWgd/sodium-fabric-0.7.3+mc1.21.10.jar"
                    }
                  ]
                },
                {
                  "name": "1.21.8",
                  "coreMods": [
                    {
                      "slug": "Vivecraft",
                      "version": "1.3.4",
                      "download_link": "https://github.com/QuestCraftPlusPlus/VivecraftMod/releases/download/1.21.8/vivecraft.jar"
                    }
                  ],
                  "defaultMods": []
                }
              ]
            }
            """;

    private QuestCraftModManager createManager() {
        return new QuestCraftModManager(mock(DownloadManager.class));
    }

    @Test
    void parseVersionEntriesReturnsAllVersions() throws IOException {
        QuestCraftModManager mgr = createManager();
        List<QuestCraftVersionEntry> entries = mgr.parseVersionEntries(SAMPLE_JSON);

        assertEquals(2, entries.size());
        assertEquals("1.21.10", entries.get(0).getName());
        assertEquals("1.21.8", entries.get(1).getName());
    }

    @Test
    void parseVersionEntriesPopulatesCoreMods() throws IOException {
        QuestCraftModManager mgr = createManager();
        List<QuestCraftVersionEntry> entries = mgr.parseVersionEntries(SAMPLE_JSON);
        QuestCraftVersionEntry first = entries.get(0);

        assertEquals(2, first.getCoreMods().size());
        assertEquals("Vivecraft", first.getCoreMods().get(0).getSlug());
        assertEquals("1.3.4.2", first.getCoreMods().get(0).getVersion());
        assertEquals("Fabric-API", first.getCoreMods().get(1).getSlug());
    }

    @Test
    void parseVersionEntriesPopulatesDefaultMods() throws IOException {
        QuestCraftModManager mgr = createManager();
        List<QuestCraftVersionEntry> entries = mgr.parseVersionEntries(SAMPLE_JSON);

        assertEquals(1, entries.get(0).getDefaultMods().size());
        assertEquals("Sodium", entries.get(0).getDefaultMods().get(0).getSlug());
        assertEquals(0, entries.get(1).getDefaultMods().size());
    }

    @Test
    void findVersionReturnsCorrectEntry() throws IOException {
        QuestCraftModManager mgr = createManager();
        List<QuestCraftVersionEntry> entries = mgr.parseVersionEntries(SAMPLE_JSON);

        var found = mgr.findVersion(entries, "1.21.8");
        assertTrue(found.isPresent());
        assertEquals("1.21.8", found.get().getName());
    }

    @Test
    void findVersionReturnsEmptyForUnknownVersion() throws IOException {
        QuestCraftModManager mgr = createManager();
        List<QuestCraftVersionEntry> entries = mgr.parseVersionEntries(SAMPLE_JSON);

        assertTrue(mgr.findVersion(entries, "9.99.99").isEmpty());
    }

    @Test
    void parseVersionEntriesThrowsOnMissingVersionsKey() {
        QuestCraftModManager mgr = createManager();
        assertThrows(IOException.class,
                () -> mgr.parseVersionEntries("{\"other\": []}"));
    }

    @Test
    void parseVersionEntriesThrowsOnInvalidJson() {
        QuestCraftModManager mgr = createManager();
        assertThrows(IOException.class,
                () -> mgr.parseVersionEntries("not json at all"));
    }
}
