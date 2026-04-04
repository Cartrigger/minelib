package io.minelib;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MineLibTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsWithDefaultGameDirectory() {
        MineLib lib = MineLib.builder().build();
        assertNotNull(lib.getGameDirectory());
        assertNotNull(lib.getDownloadManager());
        assertNotNull(lib.getVersionManager());
        assertNotNull(lib.getAssetManager());
        assertNotNull(lib.getLibraryManager());
        assertNotNull(lib.getJavaRuntimeManager());
        assertNotNull(lib.getLauncher());
    }

    @Test
    void buildsWithCustomGameDirectory() {
        MineLib lib = MineLib.builder()
                .gameDirectory(tempDir)
                .build();

        assertEquals(tempDir, lib.getGameDirectory());
    }

    @Test
    void throwsWhenMaxConcurrentDownloadsIsZero() {
        assertThrows(IllegalArgumentException.class, () ->
                MineLib.builder().maxConcurrentDownloads(0).build());
    }

    @Test
    void throwsWhenMaxConcurrentDownloadsIsNegative() {
        assertThrows(IllegalArgumentException.class, () ->
                MineLib.builder().maxConcurrentDownloads(-1).build());
    }

    @Test
    void acceptsPositiveConcurrentDownloads() {
        assertDoesNotThrow(() ->
                MineLib.builder()
                        .gameDirectory(tempDir)
                        .maxConcurrentDownloads(8)
                        .build());
    }
}
