package io.minelib.launcher.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InstanceTest {

    // ── Constructor ───────────────────────────────────────────────────────────

    @Test
    void constructorSetsAllFields() {
        Instance inst = new Instance("abc-123", "My World", "1.21.4", Instance.ModLoader.FABRIC);
        assertEquals("abc-123", inst.getId());
        assertEquals("My World", inst.getName());
        assertEquals("1.21.4", inst.getMinecraftVersion());
        assertEquals(Instance.ModLoader.FABRIC, inst.getModLoader());
        assertEquals(0L, inst.getLastPlayedMs());
    }

    @Test
    void constructorRejectsNullId() {
        assertThrows(NullPointerException.class,
                () -> new Instance(null, "name", "1.21", Instance.ModLoader.VANILLA));
    }

    @Test
    void constructorRejectsNullName() {
        assertThrows(NullPointerException.class,
                () -> new Instance("id", null, "1.21", Instance.ModLoader.VANILLA));
    }

    @Test
    void constructorRejectsNullVersion() {
        assertThrows(NullPointerException.class,
                () -> new Instance("id", "name", null, Instance.ModLoader.VANILLA));
    }

    @Test
    void constructorRejectsNullModLoader() {
        assertThrows(NullPointerException.class,
                () -> new Instance("id", "name", "1.21", null));
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    @Test
    void setNameUpdatesName() {
        Instance inst = new Instance("id", "old", "1.21", Instance.ModLoader.VANILLA);
        inst.setName("new name");
        assertEquals("new name", inst.getName());
    }

    @Test
    void setNameRejectsNull() {
        Instance inst = new Instance("id", "name", "1.21", Instance.ModLoader.VANILLA);
        assertThrows(NullPointerException.class, () -> inst.setName(null));
    }

    @Test
    void setMinecraftVersionUpdatesVersion() {
        Instance inst = new Instance("id", "name", "1.20", Instance.ModLoader.VANILLA);
        inst.setMinecraftVersion("1.21.4");
        assertEquals("1.21.4", inst.getMinecraftVersion());
    }

    @Test
    void setMinecraftVersionRejectsNull() {
        Instance inst = new Instance("id", "name", "1.21", Instance.ModLoader.VANILLA);
        assertThrows(NullPointerException.class, () -> inst.setMinecraftVersion(null));
    }

    @Test
    void setModLoaderUpdatesLoader() {
        Instance inst = new Instance("id", "name", "1.21", Instance.ModLoader.VANILLA);
        inst.setModLoader(Instance.ModLoader.FORGE);
        assertEquals(Instance.ModLoader.FORGE, inst.getModLoader());
    }

    @Test
    void setModLoaderRejectsNull() {
        Instance inst = new Instance("id", "name", "1.21", Instance.ModLoader.VANILLA);
        assertThrows(NullPointerException.class, () -> inst.setModLoader(null));
    }

    // ── markPlayed ────────────────────────────────────────────────────────────

    @Test
    void markPlayedSetsLastPlayedNearCurrentTime() {
        Instance inst = new Instance("id", "name", "1.21", Instance.ModLoader.VANILLA);
        assertEquals(0L, inst.getLastPlayedMs());

        long before = System.currentTimeMillis();
        inst.markPlayed();
        long after = System.currentTimeMillis();

        assertTrue(inst.getLastPlayedMs() >= before, "lastPlayedMs should be >= before");
        assertTrue(inst.getLastPlayedMs() <= after,  "lastPlayedMs should be <= after");
    }

    @Test
    void markPlayedCanBeCalledMultipleTimes() {
        Instance inst = new Instance("id", "name", "1.21", Instance.ModLoader.VANILLA);
        inst.markPlayed();
        long first = inst.getLastPlayedMs();
        inst.markPlayed();
        assertTrue(inst.getLastPlayedMs() >= first);
    }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    @Test
    void equalInstancesHaveSameId() {
        Instance a = new Instance("same-id", "Name A", "1.21", Instance.ModLoader.VANILLA);
        Instance b = new Instance("same-id", "Name B", "1.20", Instance.ModLoader.FABRIC);
        assertEquals(a, b);
    }

    @Test
    void instancesWithDifferentIdsAreNotEqual() {
        Instance a = new Instance("id-1", "Name", "1.21", Instance.ModLoader.VANILLA);
        Instance b = new Instance("id-2", "Name", "1.21", Instance.ModLoader.VANILLA);
        assertNotEquals(a, b);
    }

    @Test
    void equalInstancesHaveEqualHashCode() {
        Instance a = new Instance("same-id", "A", "1.21", Instance.ModLoader.VANILLA);
        Instance b = new Instance("same-id", "B", "1.20", Instance.ModLoader.FABRIC);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void instanceNotEqualToNull() {
        Instance inst = new Instance("id", "name", "1.21", Instance.ModLoader.VANILLA);
        assertNotEquals(null, inst);
    }

    @Test
    void instanceNotEqualToArbitraryObject() {
        Instance inst = new Instance("id", "name", "1.21", Instance.ModLoader.VANILLA);
        assertNotEquals("some string", inst);
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    void toStringContainsIdNameAndVersion() {
        Instance inst = new Instance("my-id", "My Instance", "1.21.4", Instance.ModLoader.NEOFORGE);
        String s = inst.toString();
        assertTrue(s.contains("my-id"),      "toString should contain id");
        assertTrue(s.contains("My Instance"), "toString should contain name");
        assertTrue(s.contains("1.21.4"),      "toString should contain version");
    }
}
