package io.minelib.launcher.service;

import io.minelib.launcher.model.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InstanceServiceTest {

    @TempDir
    Path tempDir;

    private InstanceService service;

    @BeforeEach
    void setUp() {
        service = new InstanceService(tempDir);
    }

    // ── loadInstances ─────────────────────────────────────────────────────────

    @Test
    void loadInstancesReturnsEmptyListWhenInstancesDirAbsent() {
        // Fresh directory — no "instances" sub-folder yet
        InstanceService fresh = new InstanceService(tempDir.resolve("nonexistent-dir"));
        assertTrue(fresh.loadInstances().isEmpty());
    }

    @Test
    void loadInstancesReturnsUnmodifiableList() throws IOException {
        service.createInstance("A", "1.21", Instance.ModLoader.VANILLA);
        List<Instance> list = service.loadInstances();
        assertThrows(UnsupportedOperationException.class,
                () -> list.add(new Instance("x", "y", "1.0", Instance.ModLoader.VANILLA)));
    }

    @Test
    void loadInstancesIgnoresMalformedJson() throws IOException {
        service.createInstance("Good", "1.21", Instance.ModLoader.VANILLA);

        // Inject a directory with malformed JSON — must not crash the whole load
        Path badDir = tempDir.resolve("instances").resolve("bad-id");
        Files.createDirectories(badDir);
        Files.writeString(badDir.resolve("instance.json"), "not json {{{ broken");

        InstanceService fresh = new InstanceService(tempDir);
        List<Instance> loaded = fresh.loadInstances();
        assertEquals(1, loaded.size(), "malformed entry should be silently skipped");
        assertEquals("Good", loaded.get(0).getName());
    }

    @Test
    void loadInstancesIgnoresDirectoriesWithoutInstanceJson() throws IOException {
        service.createInstance("Real", "1.21", Instance.ModLoader.VANILLA);

        // A directory without instance.json should be ignored
        Files.createDirectories(tempDir.resolve("instances").resolve("ghost-dir"));

        InstanceService fresh = new InstanceService(tempDir);
        assertEquals(1, fresh.loadInstances().size());
    }

    // ── createInstance ────────────────────────────────────────────────────────

    @Test
    void createInstanceReturnsInstanceWithCorrectFields() throws IOException {
        Instance inst = service.createInstance("My World", "1.21.4", Instance.ModLoader.FABRIC);
        assertEquals("My World", inst.getName());
        assertEquals("1.21.4",   inst.getMinecraftVersion());
        assertEquals(Instance.ModLoader.FABRIC, inst.getModLoader());
        assertNotNull(inst.getId());
        assertFalse(inst.getId().isBlank());
    }

    @Test
    void createInstanceWritesInstanceJsonToDisk() throws IOException {
        Instance inst = service.createInstance("Test", "1.20", Instance.ModLoader.VANILLA);
        Path meta = tempDir.resolve("instances").resolve(inst.getId()).resolve("instance.json");
        assertTrue(Files.exists(meta));
        String content = Files.readString(meta);
        assertTrue(content.contains("Test"), "JSON should contain the instance name");
        assertTrue(content.contains("1.20"), "JSON should contain the version");
    }

    @Test
    void createInstanceCreatesMinecraftDirectory() throws IOException {
        Instance inst = service.createInstance("Test", "1.20", Instance.ModLoader.VANILLA);
        Path mc = tempDir.resolve("instances").resolve(inst.getId()).resolve(".minecraft");
        assertTrue(Files.isDirectory(mc));
    }

    @Test
    void createInstanceAppearsInFreshLoad() throws IOException {
        service.createInstance("Alpha", "1.21", Instance.ModLoader.FORGE);
        service.createInstance("Beta",  "1.20", Instance.ModLoader.NEOFORGE);

        InstanceService fresh = new InstanceService(tempDir);
        List<Instance> loaded = fresh.loadInstances();
        assertEquals(2, loaded.size());
        assertTrue(loaded.stream().anyMatch(i -> i.getName().equals("Alpha")));
        assertTrue(loaded.stream().anyMatch(i -> i.getName().equals("Beta")));
    }

    // ── deleteInstance ────────────────────────────────────────────────────────

    @Test
    void deleteInstanceRemovesDirFromDisk() throws IOException {
        Instance inst = service.createInstance("ToDelete", "1.21", Instance.ModLoader.VANILLA);
        Path dir = tempDir.resolve("instances").resolve(inst.getId());
        assertTrue(Files.exists(dir));

        service.deleteInstance(inst);
        assertFalse(Files.exists(dir));
    }

    @Test
    void deleteInstanceRemovesFromInMemoryList() throws IOException {
        Instance inst = service.createInstance("ToDelete", "1.21", Instance.ModLoader.VANILLA);
        service.deleteInstance(inst);

        InstanceService fresh = new InstanceService(tempDir);
        assertFalse(fresh.loadInstances().contains(inst));
    }

    @Test
    void deleteNonExistentInstanceDoesNotThrow() {
        Instance ghost = new Instance("ghost-id", "Ghost", "1.21", Instance.ModLoader.VANILLA);
        assertDoesNotThrow(() -> service.deleteInstance(ghost));
    }

    // ── saveInstance ──────────────────────────────────────────────────────────

    @Test
    void saveInstancePersistsMutation() throws IOException {
        Instance inst = service.createInstance("Original", "1.21", Instance.ModLoader.VANILLA);
        inst.setName("Updated");
        service.saveInstance(inst);

        InstanceService fresh = new InstanceService(tempDir);
        Instance reloaded = fresh.loadInstances().stream()
                .filter(i -> i.getId().equals(inst.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Instance not found after reload"));

        assertEquals("Updated", reloaded.getName());
    }

    // ── getGameDirectory ──────────────────────────────────────────────────────

    @Test
    void getGameDirectoryReturnsCorrectPath() throws IOException {
        Instance inst = service.createInstance("Test", "1.21", Instance.ModLoader.VANILLA);
        Path expected = tempDir.resolve("instances").resolve(inst.getId()).resolve(".minecraft");
        assertEquals(expected, service.getGameDirectory(inst));
    }

    // ── loadInstances sort order ──────────────────────────────────────────────

    @Test
    void loadInstancesSortsByLastPlayedDescending() throws Exception {
        Instance older = service.createInstance("Older", "1.20", Instance.ModLoader.VANILLA);
        Instance newer = service.createInstance("Newer", "1.21", Instance.ModLoader.VANILLA);

        older.markPlayed();
        service.saveInstance(older);
        Thread.sleep(5); // ensure different millisecond timestamps
        newer.markPlayed();
        service.saveInstance(newer);

        InstanceService fresh = new InstanceService(tempDir);
        List<Instance> loaded = fresh.loadInstances();
        assertEquals(2, loaded.size());
        assertEquals("Newer", loaded.get(0).getName(), "most-recently-played should come first");
        assertEquals("Older", loaded.get(1).getName());
    }
}
