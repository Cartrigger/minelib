package io.minelib.modrinth;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModrinthFileTest {

    @Test
    void getSha512ReturnsHashWhenPresent() {
        ModrinthFile file = new ModrinthFile(
                Map.of("sha512", "abc123", "sha1", "def456"),
                "https://cdn.modrinth.com/sodium.jar",
                "sodium.jar", true, 1024L);

        assertEquals("abc123", file.getSha512());
        assertEquals("def456", file.getSha1());
    }

    @Test
    void getSha512ReturnsNullWhenHashesNull() {
        ModrinthFile file = new ModrinthFile(null, "https://example.com/a.jar",
                "a.jar", false, 512L);
        assertNull(file.getSha512());
        assertNull(file.getSha1());
    }

    @Test
    void isPrimaryReflectsValue() {
        ModrinthFile primary   = new ModrinthFile(Map.of(), "u", "f", true, 0L);
        ModrinthFile secondary = new ModrinthFile(Map.of(), "u", "f", false, 0L);
        assertTrue(primary.isPrimary());
        assertFalse(secondary.isPrimary());
    }
}

class ModrinthDependencyTest {

    @Test
    void dependencyTypeFromApiString() {
        assertEquals(ModrinthDependency.Type.REQUIRED,
                ModrinthDependency.Type.fromApiString("required"));
        assertEquals(ModrinthDependency.Type.OPTIONAL,
                ModrinthDependency.Type.fromApiString("optional"));
        assertEquals(ModrinthDependency.Type.INCOMPATIBLE,
                ModrinthDependency.Type.fromApiString("incompatible"));
        assertEquals(ModrinthDependency.Type.EMBEDDED,
                ModrinthDependency.Type.fromApiString("embedded"));
        // unknown values → OPTIONAL
        assertEquals(ModrinthDependency.Type.OPTIONAL,
                ModrinthDependency.Type.fromApiString("whatever"));
        assertEquals(ModrinthDependency.Type.OPTIONAL,
                ModrinthDependency.Type.fromApiString(null));
    }
}

class ModrinthVersionTest {

    private static ModrinthFile primaryFile() {
        return new ModrinthFile(
                Map.of("sha1", "aabbcc"),
                "https://cdn.modrinth.com/test.jar",
                "test-mod.jar", true, 2048L);
    }

    @Test
    void getPrimaryFileReturnsPrimaryFlaggedFile() {
        ModrinthFile secondary = new ModrinthFile(Map.of(), "u", "secondary.jar", false, 0L);
        ModrinthFile primary   = primaryFile();

        ModrinthVersion version = new ModrinthVersion(
                "v1", "proj1", "Test Mod", "1.0.0", null,
                List.of("1.21.4"), ModrinthVersion.VersionType.RELEASE,
                List.of("fabric"), true, List.of(secondary, primary), List.of());

        assertSame(primary, version.getPrimaryFile());
    }

    @Test
    void getPrimaryFileFallsBackToFirstWhenNoPrimary() {
        ModrinthFile first  = new ModrinthFile(Map.of(), "u1", "a.jar", false, 0L);
        ModrinthFile second = new ModrinthFile(Map.of(), "u2", "b.jar", false, 0L);

        ModrinthVersion version = new ModrinthVersion(
                "v1", "proj1", "Test", "1.0", null,
                List.of(), ModrinthVersion.VersionType.RELEASE,
                List.of(), false, List.of(first, second), List.of());

        assertSame(first, version.getPrimaryFile());
    }

    @Test
    void getPrimaryFileThrowsWhenNoFiles() {
        ModrinthVersion version = new ModrinthVersion(
                "v1", "p1", "Test", "1.0", null,
                List.of(), ModrinthVersion.VersionType.RELEASE,
                List.of(), false, List.of(), List.of());
        assertThrows(IllegalStateException.class, version::getPrimaryFile);
    }

    @Test
    void versionTypeFromApiString() {
        assertEquals(ModrinthVersion.VersionType.RELEASE,
                ModrinthVersion.VersionType.fromApiString("release"));
        assertEquals(ModrinthVersion.VersionType.BETA,
                ModrinthVersion.VersionType.fromApiString("beta"));
        assertEquals(ModrinthVersion.VersionType.ALPHA,
                ModrinthVersion.VersionType.fromApiString("alpha"));
        assertEquals(ModrinthVersion.VersionType.RELEASE,
                ModrinthVersion.VersionType.fromApiString(null));
    }
}

class ModrinthProjectTest {

    @Test
    void projectTypeFromApiString() {
        assertEquals(ModrinthProject.ProjectType.MOD,
                ModrinthProject.ProjectType.fromApiString("mod"));
        assertEquals(ModrinthProject.ProjectType.MODPACK,
                ModrinthProject.ProjectType.fromApiString("modpack"));
        assertEquals(ModrinthProject.ProjectType.RESOURCEPACK,
                ModrinthProject.ProjectType.fromApiString("resourcepack"));
        assertEquals(ModrinthProject.ProjectType.SHADER,
                ModrinthProject.ProjectType.fromApiString("shader"));
        assertEquals(ModrinthProject.ProjectType.MOD,
                ModrinthProject.ProjectType.fromApiString("unknown"));
        assertEquals(ModrinthProject.ProjectType.MOD,
                ModrinthProject.ProjectType.fromApiString(null));
    }

    @Test
    void projectFieldsRoundtrip() {
        ModrinthProject p = new ModrinthProject(
                "AANobbMI", "sodium", ModrinthProject.ProjectType.MOD,
                "Sodium", "A Minecraft renderer", "https://cdn/icon.png",
                5_000_000L, 12_345L,
                List.of("optimization"), List.of("1.21.4"), List.of("fabric"),
                List.of("ver1", "ver2"));

        assertEquals("AANobbMI", p.getId());
        assertEquals("sodium",   p.getSlug());
        assertEquals("Sodium",   p.getTitle());
        assertEquals(5_000_000L, p.getDownloads());
        assertEquals(List.of("fabric"), p.getLoaders());
    }
}
