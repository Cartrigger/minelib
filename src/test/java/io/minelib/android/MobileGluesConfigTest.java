package io.minelib.android;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MobileGluesConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsWithDefaults() {
        MobileGluesConfig config = MobileGluesConfig.builder()
                .installDirectory(tempDir)
                .build();

        assertEquals(MobileGluesConfig.DEFAULT_VERSION, config.getVersion());
        assertEquals(tempDir, config.getInstallDirectory());
        assertNotNull(config.getAbi());
        assertFalse(config.getAbi().isBlank());
    }

    @Test
    void libraryPathIsUnderInstallDirectory() {
        MobileGluesConfig config = MobileGluesConfig.builder()
                .installDirectory(tempDir)
                .build();

        Path libPath = config.getLibraryPath();
        assertTrue(libPath.startsWith(tempDir));
        assertEquals("libMobileGlues.so", libPath.getFileName().toString());
    }

    @Test
    void downloadUrlContainsVersionAndAbi() {
        MobileGluesConfig config = MobileGluesConfig.builder()
                .installDirectory(tempDir)
                .version("2.0.0")
                .abi("arm64-v8a")
                .build();

        String url = config.getDownloadUrl();
        assertTrue(url.contains("2.0.0"), "URL should contain version");
        assertTrue(url.contains("arm64-v8a"), "URL should contain ABI");
    }

    @Test
    void customDownloadUrlOverridesDefault() {
        String custom = "https://example.com/mylib.so";
        MobileGluesConfig config = MobileGluesConfig.builder()
                .installDirectory(tempDir)
                .customDownloadUrl(custom)
                .build();

        assertEquals(custom, config.getDownloadUrl());
    }

    @Test
    void throwsWhenInstallDirectoryMissing() {
        assertThrows(IllegalStateException.class, () ->
                MobileGluesConfig.builder().build());
    }

    @Test
    void detectAbiReturnsSupportedValue() {
        String abi = MobileGluesConfig.detectAbi();
        assertTrue(
                abi.equals("arm64-v8a") || abi.equals("armeabi-v7a")
                        || abi.equals("x86_64") || abi.equals("x86"),
                "detectAbi() returned unexpected value: " + abi);
    }
}
