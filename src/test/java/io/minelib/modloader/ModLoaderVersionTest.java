package io.minelib.modloader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModLoaderVersionTest {

    @Test
    void buildsWithAllFields() {
        ModLoaderVersion v = ModLoaderVersion.builder()
                .loader(ModLoader.FABRIC)
                .minecraftVersion("1.21.4")
                .loaderVersion("0.16.10")
                .stable(true)
                .build();

        assertEquals(ModLoader.FABRIC, v.getLoader());
        assertEquals("1.21.4", v.getMinecraftVersion());
        assertEquals("0.16.10", v.getLoaderVersion());
        assertTrue(v.isStable());
    }

    @Test
    void defaultStableIsTrue() {
        ModLoaderVersion v = ModLoaderVersion.builder()
                .loader(ModLoader.FORGE)
                .minecraftVersion("1.21.4")
                .loaderVersion("54.0.1")
                .build();

        assertTrue(v.isStable());
    }

    @Test
    void toStringContainsAllParts() {
        ModLoaderVersion v = ModLoaderVersion.builder()
                .loader(ModLoader.NEOFORGE)
                .minecraftVersion("1.21.4")
                .loaderVersion("21.4.93")
                .build();

        String str = v.toString();
        assertTrue(str.contains("NEOFORGE") || str.contains("neoforge"),
                "toString() should mention the loader");
        assertTrue(str.contains("21.4.93"), "toString() should mention loader version");
        assertTrue(str.contains("1.21.4"),  "toString() should mention MC version");
    }

    @Test
    void throwsWhenLoaderMissing() {
        assertThrows(IllegalStateException.class, () ->
                ModLoaderVersion.builder()
                        .minecraftVersion("1.21.4")
                        .loaderVersion("0.16.10")
                        .build());
    }

    @Test
    void throwsWhenMinecraftVersionBlank() {
        assertThrows(IllegalStateException.class, () ->
                ModLoaderVersion.builder()
                        .loader(ModLoader.FABRIC)
                        .minecraftVersion("")
                        .loaderVersion("0.16.10")
                        .build());
    }

    @Test
    void throwsWhenLoaderVersionMissing() {
        assertThrows(IllegalStateException.class, () ->
                ModLoaderVersion.builder()
                        .loader(ModLoader.FABRIC)
                        .minecraftVersion("1.21.4")
                        .build());
    }

    @Test
    void allThreeLoadersAreDistinct() {
        assertNotEquals(ModLoader.FABRIC, ModLoader.FORGE);
        assertNotEquals(ModLoader.FABRIC, ModLoader.NEOFORGE);
        assertNotEquals(ModLoader.FORGE,  ModLoader.NEOFORGE);
    }
}
