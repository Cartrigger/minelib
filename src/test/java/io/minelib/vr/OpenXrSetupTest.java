package io.minelib.vr;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenXrSetupTest {

    @Test
    void constructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new OpenXrSetup(null));
    }

    @Test
    void getConfigRoundtrips() {
        OpenXrConfig config = OpenXrConfig.builder().build();
        OpenXrSetup setup = new OpenXrSetup(config);
        assertSame(config, setup.getConfig());
    }

    // -------------------------------------------------------------------------
    // getSystemProperties
    // -------------------------------------------------------------------------

    @Test
    void systemPropertiesAlwaysIncludeGlesAndEgl() {
        // Even with an empty config, the GLES/EGL defaults must be set
        OpenXrSetup setup = new OpenXrSetup(OpenXrConfig.builder().build());
        Map<String, String> props = setup.getSystemProperties();

        assertTrue(props.containsKey("org.lwjgl.opengles.libname"),
                "org.lwjgl.opengles.libname must always be present");
        assertTrue(props.containsKey("org.lwjgl.egl.libname"),
                "org.lwjgl.egl.libname must always be present");
        assertEquals(OpenXrConfig.SYSTEM_GLES3_PATH, props.get("org.lwjgl.opengles.libname"));
        assertEquals(OpenXrConfig.SYSTEM_EGL_DRI_PATH, props.get("org.lwjgl.egl.libname"));
    }

    @Test
    void systemPropertiesIncludeOpenXrLibnameWhenConfigured() {
        Path loader = Path.of("/data/app/myapp/lib/arm64/libopenxr_loader.so");
        OpenXrConfig config = OpenXrConfig.builder()
                .loaderLibraryPath(loader)
                .build();
        Map<String, String> props = new OpenXrSetup(config).getSystemProperties();

        assertTrue(props.containsKey("org.lwjgl.openxr.libname"));
        assertEquals(loader.toAbsolutePath().toString(), props.get("org.lwjgl.openxr.libname"));
    }

    @Test
    void systemPropertiesUseCustomGlesEglPaths() {
        Path gles = Path.of("/custom/libGLESv3.so");
        Path egl  = Path.of("/custom/libEGL.so");
        OpenXrConfig config = OpenXrConfig.builder()
                .glesLibraryPath(gles)
                .eglLibraryPath(egl)
                .build();
        Map<String, String> props = new OpenXrSetup(config).getSystemProperties();

        assertEquals(gles.toAbsolutePath().toString(), props.get("org.lwjgl.opengles.libname"));
        assertEquals(egl.toAbsolutePath().toString(), props.get("org.lwjgl.egl.libname"));
    }

    @Test
    void systemPropertiesAreImmutable() {
        OpenXrSetup setup = new OpenXrSetup(OpenXrConfig.builder().build());
        Map<String, String> props = setup.getSystemProperties();
        assertThrows(UnsupportedOperationException.class,
                () -> props.put("org.lwjgl.openxr.libname", "hack"));
    }

    // -------------------------------------------------------------------------
    // getEnvironmentVariables
    // -------------------------------------------------------------------------

    @Test
    void envVarsAlwaysIncludeLibGlEs() {
        OpenXrSetup setup = new OpenXrSetup(OpenXrConfig.builder().glesVersion(3).build());
        Map<String, String> env = setup.getEnvironmentVariables();
        assertEquals("3", env.get("LIBGL_ES"));
    }

    @Test
    void envVarsIncludeRendererName() {
        OpenXrSetup setup = new OpenXrSetup(
                OpenXrConfig.builder()
                        .rendererName(OpenXrConfig.RENDERER_MOBILEGLUES)
                        .build());
        assertEquals(OpenXrConfig.RENDERER_MOBILEGLUES,
                setup.getEnvironmentVariables().get("POJLIB_RENDERER"));
    }

    @Test
    void envVarsIncludeXrRuntimeJsonWhenSet() {
        Path runtimeJson = Path.of("/data/local/openxr/runtime.json");
        OpenXrSetup setup = new OpenXrSetup(
                OpenXrConfig.builder().runtimeJsonPath(runtimeJson).build());
        Map<String, String> env = setup.getEnvironmentVariables();
        assertEquals(runtimeJson.toAbsolutePath().toString(), env.get("XR_RUNTIME_JSON"));
    }

    @Test
    void envVarsOmitXrRuntimeJsonWhenNotSet() {
        OpenXrSetup setup = new OpenXrSetup(OpenXrConfig.builder().build());
        assertFalse(setup.getEnvironmentVariables().containsKey("XR_RUNTIME_JSON"));
    }

    @Test
    void envVarsIncludeXrApiLayerPath() {
        Path layer1 = Path.of("/layers/one");
        Path layer2 = Path.of("/layers/two");
        OpenXrSetup setup = new OpenXrSetup(
                OpenXrConfig.builder()
                        .addApiLayerPath(layer1)
                        .addApiLayerPath(layer2)
                        .build());
        String layerPath = setup.getEnvironmentVariables().get("XR_API_LAYER_PATH");
        assertNotNull(layerPath);
        assertTrue(layerPath.contains(layer1.toAbsolutePath().toString()));
        assertTrue(layerPath.contains(layer2.toAbsolutePath().toString()));
    }

    @Test
    void envVarsIncludeValidationLayerWhenEnabled() {
        OpenXrSetup setup = new OpenXrSetup(
                OpenXrConfig.builder().validationLayerEnabled(true).build());
        assertEquals("XR_APILAYER_LUNARG_core_validation",
                setup.getEnvironmentVariables().get("XR_ENABLE_API_LAYERS"));
    }

    @Test
    void envVarsIncludeLdLibraryPathWhenExtraDirsConfigured() {
        Path nativeDir = Path.of("/data/app/myapp/lib/arm64");
        OpenXrSetup setup = new OpenXrSetup(
                OpenXrConfig.builder().addExtraNativeLibDir(nativeDir).build());
        String ldPath = setup.getEnvironmentVariables().get("LD_LIBRARY_PATH");
        assertNotNull(ldPath);
        assertTrue(ldPath.contains(nativeDir.toAbsolutePath().toString()));
    }

    @Test
    void envVarsOmitLdLibraryPathWhenNoExtraDirs() {
        OpenXrSetup setup = new OpenXrSetup(OpenXrConfig.builder().build());
        assertFalse(setup.getEnvironmentVariables().containsKey("LD_LIBRARY_PATH"));
    }

    @Test
    void envVarsAreImmutable() {
        OpenXrSetup setup = new OpenXrSetup(OpenXrConfig.builder().build());
        Map<String, String> env = setup.getEnvironmentVariables();
        assertThrows(UnsupportedOperationException.class,
                () -> env.put("INJECTED", "bad"));
    }

    // -------------------------------------------------------------------------
    // applySystemProperties
    // -------------------------------------------------------------------------

    @Test
    void applySystemPropertiesSetsJvmProperties() {
        Path loader = Path.of("/tmp/libopenxr_loader.so");
        OpenXrSetup setup = new OpenXrSetup(
                OpenXrConfig.builder().loaderLibraryPath(loader).build());
        setup.applySystemProperties();

        assertEquals(loader.toAbsolutePath().toString(),
                System.getProperty("org.lwjgl.openxr.libname"));
        assertEquals(OpenXrConfig.SYSTEM_GLES3_PATH,
                System.getProperty("org.lwjgl.opengles.libname"));
        assertEquals(OpenXrConfig.SYSTEM_EGL_DRI_PATH,
                System.getProperty("org.lwjgl.egl.libname"));
    }

    // -------------------------------------------------------------------------
    // applyAndroidEnvironment — no-op on non-Android JVM
    // -------------------------------------------------------------------------

    @Test
    void applyAndroidEnvironmentIsNoOpOnDesktop() {
        // android.system.Os is absent on desktop JVM; must not throw
        OpenXrSetup setup = new OpenXrSetup(OpenXrConfig.builder().build());
        assertDoesNotThrow(() -> setup.applyAndroidEnvironment());
    }
}
