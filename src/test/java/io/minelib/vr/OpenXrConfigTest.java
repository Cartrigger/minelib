package io.minelib.vr;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenXrConfigTest {

    @Test
    void builderDefaultsAreCorrect() {
        OpenXrConfig config = OpenXrConfig.builder().build();

        assertNull(config.getLoaderLibraryPath());
        assertNull(config.getRuntimeJsonPath());
        assertTrue(config.getApiLayerPaths().isEmpty());
        assertNull(config.getLoaderDebugLevel());
        assertFalse(config.isValidationLayerEnabled());
        assertNull(config.getGlesLibraryPath());
        assertNull(config.getEglLibraryPath());
        assertEquals(3, config.getGlesVersion());
        assertEquals(OpenXrConfig.RENDERER_MOBILEGLUES, config.getRendererName());
        assertTrue(config.getExtraNativeLibDirs().isEmpty());
    }

    @Test
    void builderSetsAllFields() {
        Path loader   = Path.of("/data/app/myapp/lib/arm64/libopenxr_loader.so");
        Path runtime  = Path.of("/data/local/openxr/runtime.json");
        Path layer    = Path.of("/data/local/openxr/layers");
        Path nativeDir = Path.of("/data/app/myapp/lib/arm64");

        OpenXrConfig config = OpenXrConfig.builder()
                .loaderLibraryPath(loader)
                .runtimeJsonPath(runtime)
                .addApiLayerPath(layer)
                .loaderDebugLevel("warn")
                .validationLayerEnabled(true)
                .glesVersion(2)
                .rendererName(OpenXrConfig.RENDERER_LIGHTTHINWRAPPER)
                .addExtraNativeLibDir(nativeDir)
                .build();

        assertEquals(loader,    config.getLoaderLibraryPath());
        assertEquals(runtime,   config.getRuntimeJsonPath());
        assertEquals(List.of(layer), config.getApiLayerPaths());
        assertEquals("warn",    config.getLoaderDebugLevel());
        assertTrue(config.isValidationLayerEnabled());
        assertEquals(2,         config.getGlesVersion());
        assertEquals(OpenXrConfig.RENDERER_LIGHTTHINWRAPPER, config.getRendererName());
        assertEquals(List.of(nativeDir), config.getExtraNativeLibDirs());
    }

    @Test
    void forMetaQuestSetsLoaderInNativeLibDir() {
        Path nativeLibDir = Path.of("/data/app/com.myapp/lib/arm64");
        OpenXrConfig config = OpenXrConfig.forMetaQuest(nativeLibDir);

        assertEquals(nativeLibDir.resolve("libopenxr_loader.so"),
                config.getLoaderLibraryPath());
        assertEquals(List.of(nativeLibDir), config.getExtraNativeLibDirs());
        assertEquals(OpenXrConfig.RENDERER_MOBILEGLUES, config.getRendererName());
        assertNull(config.getRuntimeJsonPath(),
                "Meta Quest does not need XR_RUNTIME_JSON");
    }

    @Test
    void forSteamVrSetsLoaderPath() {
        Path loader = Path.of("/usr/lib/libopenxr_loader.so");
        OpenXrConfig config = OpenXrConfig.forSteamVr(loader);

        assertEquals(loader, config.getLoaderLibraryPath());
        assertNull(config.getRuntimeJsonPath(),
                "SteamVR sets XR_RUNTIME_JSON globally; no need to override");
    }

    @Test
    void apiLayerPathsAreUnmodifiable() {
        OpenXrConfig config = OpenXrConfig.builder()
                .addApiLayerPath(Path.of("/tmp/layers"))
                .build();
        assertThrows(UnsupportedOperationException.class,
                () -> config.getApiLayerPaths().add(Path.of("/other")));
    }

    @Test
    void extraNativeLibDirsAreUnmodifiable() {
        OpenXrConfig config = OpenXrConfig.builder()
                .addExtraNativeLibDir(Path.of("/native"))
                .build();
        assertThrows(UnsupportedOperationException.class,
                () -> config.getExtraNativeLibDirs().add(Path.of("/other")));
    }
}
