package io.minelib.vr;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Translates an {@link OpenXrConfig} into the environment variables and JVM system
 * properties that the OpenXR loader, LWJGL, and Vivecraft need before the game starts.
 *
 * <h2>How QuestCraft sets up OpenXR (from Pojlib source)</h2>
 * <p>Pojlib's {@code JREUtils.setJavaEnvironment} uses {@code android.system.Os.setenv()}
 * to set env vars <em>in the running process</em> — this works because {@code Os.setenv}
 * calls the POSIX {@code setenv(3)} function at the C level, which modifies the process
 * environment even after JVM startup, and all subsequent {@code dlopen} calls pick it up.
 * It also injects JVM args: {@code -Dorg.lwjgl.opengl.libname}, {@code org.lwjgl.opengles.libname},
 * and {@code org.lwjgl.egl.libname}.
 *
 * <h2>Desktop vs Android</h2>
 * <ul>
 *   <li><strong>Android (in-process)</strong>: call {@link #applySystemProperties()} +
 *       {@link #applyAndroidEnvironment()} before launching the game thread.</li>
 *   <li><strong>Desktop (subprocess)</strong>: pass {@link #getEnvironmentVariables()} to
 *       {@link io.minelib.launch.SubprocessGameRunner#SubprocessGameRunner(Map)} so the
 *       env vars reach the child JVM before it initializes the native OpenXR loader.</li>
 * </ul>
 */
public final class OpenXrSetup {

    // Environment variable names
    private static final String ENV_RUNTIME_JSON  = "XR_RUNTIME_JSON";
    private static final String ENV_API_LAYER_PATH = "XR_API_LAYER_PATH";
    private static final String ENV_LOADER_DEBUG  = "XR_LOADER_DEBUG";
    private static final String ENV_ENABLE_LAYERS = "XR_ENABLE_API_LAYERS";
    private static final String ENV_LIBGL_ES      = "LIBGL_ES";
    private static final String ENV_RENDERER      = "POJLIB_RENDERER";
    private static final String ENV_LD_LIBRARY_PATH = "LD_LIBRARY_PATH";
    private static final String VALIDATION_LAYER  = "XR_APILAYER_LUNARG_core_validation";

    // System property names (read by LWJGL at runtime)
    private static final String PROP_OPENXR_LIBNAME  = "org.lwjgl.openxr.libname";
    private static final String PROP_OPENGLES_LIBNAME = "org.lwjgl.opengles.libname";
    private static final String PROP_EGL_LIBNAME     = "org.lwjgl.egl.libname";

    private final OpenXrConfig config;

    /**
     * Creates a new {@code OpenXrSetup} backed by the given configuration.
     *
     * @param config the OpenXR configuration; must not be null
     */
    public OpenXrSetup(OpenXrConfig config) {
        if (config == null) throw new NullPointerException("config must not be null");
        this.config = config;
    }

    // -------------------------------------------------------------------------
    // Environment variables
    // -------------------------------------------------------------------------

    /**
     * Returns the environment variables that must be visible to the process running
     * Minecraft.
     *
     * <p>On desktop this map is passed to
     * {@link io.minelib.launch.SubprocessGameRunner#SubprocessGameRunner(Map)} so that
     * the child JVM inherits them.  On Android use {@link #applyAndroidEnvironment()}
     * instead to modify the current process environment via {@code android.system.Os}.
     *
     * <p>Variables included (when configured):
     * <ul>
     *   <li>{@code LIBGL_ES} — OpenGL ES version (e.g. {@code "3"})</li>
     *   <li>{@code POJLIB_RENDERER} — renderer name (e.g. {@code "MobileGlues"})</li>
     *   <li>{@code LD_LIBRARY_PATH} — prepended entries for native-library dirs</li>
     *   <li>{@code XR_RUNTIME_JSON} — if set in config</li>
     *   <li>{@code XR_API_LAYER_PATH} — if API layer paths are configured</li>
     *   <li>{@code XR_LOADER_DEBUG} — if a debug level is configured</li>
     *   <li>{@code XR_ENABLE_API_LAYERS} — if validation layer is enabled</li>
     * </ul>
     *
     * @return immutable map of environment variable name → value
     */
    public Map<String, String> getEnvironmentVariables() {
        Map<String, String> env = new LinkedHashMap<>();

        // GLES version and renderer (mirrors Pojlib's JREUtils.setJavaEnvironment)
        env.put(ENV_LIBGL_ES, String.valueOf(config.getGlesVersion()));
        if (config.getRendererName() != null) {
            env.put(ENV_RENDERER, config.getRendererName());
        }

        // Prepend extra native-library directories to LD_LIBRARY_PATH
        if (!config.getExtraNativeLibDirs().isEmpty()) {
            String extraPaths = config.getExtraNativeLibDirs().stream()
                    .map(p -> p.toAbsolutePath().toString())
                    .collect(Collectors.joining(String.valueOf(File.pathSeparatorChar)));
            // Read any existing LD_LIBRARY_PATH and append after our new paths
            String existing = System.getenv(ENV_LD_LIBRARY_PATH);
            String combined = (existing != null && !existing.isBlank())
                    ? extraPaths + File.pathSeparatorChar + existing
                    : extraPaths;
            env.put(ENV_LD_LIBRARY_PATH, combined);
        }

        // OpenXR loader env vars
        if (config.getRuntimeJsonPath() != null) {
            env.put(ENV_RUNTIME_JSON,
                    config.getRuntimeJsonPath().toAbsolutePath().toString());
        }
        if (!config.getApiLayerPaths().isEmpty()) {
            String layerPath = config.getApiLayerPaths().stream()
                    .map(p -> p.toAbsolutePath().toString())
                    .collect(Collectors.joining(String.valueOf(File.pathSeparatorChar)));
            env.put(ENV_API_LAYER_PATH, layerPath);
        }
        if (config.getLoaderDebugLevel() != null) {
            env.put(ENV_LOADER_DEBUG, config.getLoaderDebugLevel());
        }
        if (config.isValidationLayerEnabled()) {
            env.put(ENV_ENABLE_LAYERS, VALIDATION_LAYER);
        }

        return Collections.unmodifiableMap(env);
    }

    // -------------------------------------------------------------------------
    // System properties
    // -------------------------------------------------------------------------

    /**
     * Returns the JVM system properties that LWJGL's OpenXR and OpenGL ES bindings
     * require at game startup.
     *
     * <p>Properties included (when configured):
     * <ul>
     *   <li>{@code org.lwjgl.openxr.libname} → absolute path to
     *       {@code libopenxr_loader.so}</li>
     *   <li>{@code org.lwjgl.opengles.libname} → OpenGL ES 3 library (default:
     *       {@value OpenXrConfig#SYSTEM_GLES3_PATH})</li>
     *   <li>{@code org.lwjgl.egl.libname} → EGL library (default:
     *       {@value OpenXrConfig#SYSTEM_EGL_DRI_PATH})</li>
     * </ul>
     *
     * @return immutable map of system property name → value
     */
    public Map<String, String> getSystemProperties() {
        Map<String, String> props = new LinkedHashMap<>();

        if (config.getLoaderLibraryPath() != null) {
            props.put(PROP_OPENXR_LIBNAME,
                    config.getLoaderLibraryPath().toAbsolutePath().toString());
        }

        // OpenGL ES library (Pojlib: -Dorg.lwjgl.opengles.libname=/system/lib64/libGLESv3.so)
        String glesLib = config.getGlesLibraryPath() != null
                ? config.getGlesLibraryPath().toAbsolutePath().toString()
                : OpenXrConfig.SYSTEM_GLES3_PATH;
        props.put(PROP_OPENGLES_LIBNAME, glesLib);

        // EGL library (Pojlib: -Dorg.lwjgl.egl.libname=/system/lib64/libEGL_dri.so)
        String eglLib = config.getEglLibraryPath() != null
                ? config.getEglLibraryPath().toAbsolutePath().toString()
                : OpenXrConfig.SYSTEM_EGL_DRI_PATH;
        props.put(PROP_EGL_LIBNAME, eglLib);

        return Collections.unmodifiableMap(props);
    }

    /**
     * Convenience method: applies all system properties from {@link #getSystemProperties()}
     * to the current JVM via {@link System#setProperty(String, String)}.
     *
     * <p>Call this on Android before invoking the game's {@code main} method.
     */
    public void applySystemProperties() {
        getSystemProperties().forEach(System::setProperty);
    }

    // -------------------------------------------------------------------------
    // Android in-process environment
    // -------------------------------------------------------------------------

    /**
     * Applies environment variables to the <em>current</em> process using
     * {@code android.system.Os.setenv()}.
     *
     * <p>This is how QuestCraft's Pojlib ({@code JREUtils.setJavaEnvironment}) injects
     * {@code LIBGL_ES}, {@code LD_LIBRARY_PATH}, and other variables — it calls the
     * POSIX {@code setenv(3)} function directly via {@code android.system.Os}, which
     * modifies the C-level process environment even after JVM startup.  All subsequent
     * native {@code dlopen} calls (including those made by the OpenXR loader) pick up
     * the updated values.
     *
     * <p>On non-Android JVMs this method is a no-op because {@code android.system.Os}
     * is not present; use {@link #getEnvironmentVariables()} +
     * {@link io.minelib.launch.SubprocessGameRunner} instead.
     *
     * @param overwrite if {@code true}, existing env var values are replaced; if
     *                  {@code false}, pre-existing values are kept unchanged
     */
    public void applyAndroidEnvironment(boolean overwrite) {
        Map<String, String> vars = getEnvironmentVariables();
        if (vars.isEmpty()) return;
        try {
            Class<?> osClass   = Class.forName("android.system.Os");
            java.lang.reflect.Method setenv =
                    osClass.getMethod("setenv", String.class, String.class, boolean.class);
            for (Map.Entry<String, String> e : vars.entrySet()) {
                setenv.invoke(null, e.getKey(), e.getValue(), overwrite);
            }
        } catch (ClassNotFoundException ignored) {
            // Not running on Android — env vars must be passed to the subprocess instead
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set environment via android.system.Os", e);
        }
    }

    /**
     * Shorthand for {@link #applyAndroidEnvironment(boolean) applyAndroidEnvironment(true)}.
     * Overwrites any pre-existing values.
     */
    public void applyAndroidEnvironment() {
        applyAndroidEnvironment(true);
    }

    /** Returns the {@link OpenXrConfig} backing this setup instance. */
    public OpenXrConfig getConfig() { return config; }
}
