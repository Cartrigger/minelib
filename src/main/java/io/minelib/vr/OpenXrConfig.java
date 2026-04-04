package io.minelib.vr;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for the OpenXR runtime used by VR-capable Minecraft mods such as
 * <a href="https://www.vivecraft.org/">Vivecraft</a> on Meta Quest (QuestCraft).
 *
 * <h2>How OpenXR works with QuestCraft / Vivecraft</h2>
 * <p>QuestCraft's
 * <a href="https://github.com/QuestCraftPlusPlus/Pojlib">Pojlib</a> ships
 * {@code libopenxr_loader.so} as a prebuilt JNI library bundled inside the launcher APK.
 * At launch time it:
 * <ol>
 *   <li>Adds the app's native-library directory (where {@code libopenxr_loader.so} lives)
 *       to {@code LD_LIBRARY_PATH} so the loader is found by subsequent {@code dlopen} calls.</li>
 *   <li>Sets {@code LIBGL_ES} so the EGL bridge requests the correct OpenGL ES version.</li>
 *   <li>Sets JVM system properties:
 *       <ul>
 *         <li>{@code org.lwjgl.opengl.libname} → the OpenGL renderer (MobileGlues /
 *             LightThinWrapper)</li>
 *         <li>{@code org.lwjgl.opengles.libname} → {@value #SYSTEM_GLES3_PATH}</li>
 *         <li>{@code org.lwjgl.egl.libname} → {@value #SYSTEM_EGL_DRI_PATH}</li>
 *         <li>{@code org.lwjgl.openxr.libname} → path to {@code libopenxr_loader.so}</li>
 *       </ul>
 *   </li>
 *   <li>Vivecraft calls into the {@code org.vivecraft.util.VLoader} JNI bridge (provided by
 *       Pojlib's {@code libvloader.so}) to get the current EGL display/context/config and
 *       Dalvik VM pointer for OpenXR session creation.</li>
 * </ol>
 *
 * <p>In minelib's architecture:
 * <ul>
 *   <li>MobileGlues acts as the OpenGL renderer (replaces LightThinWrapper).</li>
 *   <li>{@code OpenXrConfig} holds all paths and flags required to wire up OpenXR.</li>
 *   <li>{@link OpenXrSetup} applies them to the process before the game starts.</li>
 *   <li>{@link VrAndroidGameRunner} combines MobileGlues + OpenXR setup.</li>
 * </ul>
 *
 * <h2>Example — Meta Quest</h2>
 * <pre>{@code
 * // The OpenXR loader .so is bundled as a JNI lib in the launcher APK;
 * // nativeLibraryDir is Context.getApplicationInfo().nativeLibraryDir
 * Path nativeLibDir = Path.of(context.getApplicationInfo().nativeLibraryDir);
 * OpenXrConfig xrConfig = OpenXrConfig.forMetaQuest(nativeLibDir);
 * OpenXrSetup  xrSetup  = new OpenXrSetup(xrConfig);
 *
 * VrAndroidGameRunner runner = new VrAndroidGameRunner(mobileGluesDriver, xrSetup);
 * }</pre>
 *
 * <h2>Example — Desktop SteamVR</h2>
 * <pre>{@code
 * OpenXrConfig xrConfig = OpenXrConfig.builder()
 *     .loaderLibraryPath(Path.of("/usr/lib/libopenxr_loader.so"))
 *     .build();
 * OpenXrSetup xrSetup = new OpenXrSetup(xrConfig);
 * // Pass env vars to the child JVM process:
 * GameRunner runner = new SubprocessGameRunner(xrSetup.getEnvironmentVariables());
 * }</pre>
 */
public final class OpenXrConfig {

    // -------------------------------------------------------------------------
    // Well-known Android system library paths (arm64, Meta Quest OS)
    // -------------------------------------------------------------------------

    /** System OpenGL ES 3 library path on Android arm64 devices. */
    public static final String SYSTEM_GLES3_PATH  = "/system/lib64/libGLESv3.so";

    /**
     * Primary EGL library path used by QuestCraft ({@code libEGL_dri.so}).
     * Use {@link #SYSTEM_EGL_PATH} as a fallback if this is absent.
     */
    public static final String SYSTEM_EGL_DRI_PATH = "/system/lib64/libEGL_dri.so";

    /** Fallback EGL library path ({@code libEGL.so}). */
    public static final String SYSTEM_EGL_PATH     = "/system/lib64/libEGL.so";

    // -------------------------------------------------------------------------
    // Renderer names (POJLIB_RENDERER env var)
    // -------------------------------------------------------------------------

    /**
     * Renderer name for
     * <a href="https://github.com/PojavLauncherTeam/MobileGlues">MobileGlues</a>.
     * This is the renderer used by minelib's {@link io.minelib.android.MobileGluesDriver}.
     */
    public static final String RENDERER_MOBILEGLUES      = "MobileGlues";

    /**
     * Renderer name for QuestCraft's LightThinWrapper — an alternative to MobileGlues.
     * Pass this constant to {@link Builder#rendererName(String)} if your launcher bundles
     * {@code libltw.so} instead of MobileGlues.
     */
    public static final String RENDERER_LIGHTTHINWRAPPER = "LightThinWrapper";

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final Path   loaderLibraryPath;
    private final Path   runtimeJsonPath;
    private final List<Path> apiLayerPaths;
    private final String loaderDebugLevel;
    private final boolean validationLayerEnabled;
    private final Path   glesLibraryPath;
    private final Path   eglLibraryPath;
    private final int    glesVersion;
    private final String rendererName;
    private final List<Path> extraNativeLibDirs;

    private OpenXrConfig(Builder b) {
        this.loaderLibraryPath     = b.loaderLibraryPath;
        this.runtimeJsonPath       = b.runtimeJsonPath;
        this.apiLayerPaths         = Collections.unmodifiableList(new ArrayList<>(b.apiLayerPaths));
        this.loaderDebugLevel      = b.loaderDebugLevel;
        this.validationLayerEnabled = b.validationLayerEnabled;
        this.glesLibraryPath       = b.glesLibraryPath;
        this.eglLibraryPath        = b.eglLibraryPath;
        this.glesVersion           = b.glesVersion;
        this.rendererName          = b.rendererName;
        this.extraNativeLibDirs    = Collections.unmodifiableList(new ArrayList<>(b.extraNativeLibDirs));
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Returns the path to {@code libopenxr_loader.so} (Android) or
     * {@code openxr_loader-1_0.dll} (Windows), or {@code null} if the loader is
     * expected on the system library path.
     * <p>On Meta Quest this is typically the APK's native-library directory:
     * {@code context.getApplicationInfo().nativeLibraryDir + "/libopenxr_loader.so"}.
     */
    public Path getLoaderLibraryPath() { return loaderLibraryPath; }

    /**
     * Returns the path to the OpenXR runtime JSON manifest ({@code XR_RUNTIME_JSON}),
     * or {@code null}.  Not needed on Android where the Meta runtime is discovered via the
     * Android package manager.  On desktop this is usually set by SteamVR automatically.
     */
    public Path getRuntimeJsonPath() { return runtimeJsonPath; }

    /**
     * Returns the list of directories that contain OpenXR API layer JSON manifests.
     * Used to build the {@code XR_API_LAYER_PATH} environment variable.
     */
    public List<Path> getApiLayerPaths() { return apiLayerPaths; }

    /**
     * Returns the {@code XR_LOADER_DEBUG} value (e.g. {@code "error"}, {@code "warn"},
     * {@code "info"}, {@code "verbose"}), or {@code null} to leave the variable unset.
     */
    public String getLoaderDebugLevel() { return loaderDebugLevel; }

    /**
     * Returns {@code true} if the OpenXR validation layer
     * ({@code XR_APILAYER_LUNARG_core_validation}) should be enabled via
     * {@code XR_ENABLE_API_LAYERS}.
     */
    public boolean isValidationLayerEnabled() { return validationLayerEnabled; }

    /**
     * Returns the path to the system OpenGL ES 3 library, or {@code null} to use the
     * default {@value #SYSTEM_GLES3_PATH}.
     * Used for the {@code org.lwjgl.opengles.libname} system property.
     */
    public Path getGlesLibraryPath() { return glesLibraryPath; }

    /**
     * Returns the path to the EGL library, or {@code null} to use the default
     * {@value #SYSTEM_EGL_DRI_PATH}.
     * Used for the {@code org.lwjgl.egl.libname} system property.
     */
    public Path getEglLibraryPath() { return eglLibraryPath; }

    /**
     * Returns the OpenGL ES version number for the {@code LIBGL_ES} environment variable.
     * QuestCraft uses {@code 2}; the default here is {@code 3} (recommended for modern
     * MobileGlues builds that expose the full GLES 3.x surface).
     */
    public int getGlesVersion() { return glesVersion; }

    /**
     * Returns the renderer name set in the {@code POJLIB_RENDERER} environment variable.
     * Defaults to {@value #RENDERER_MOBILEGLUES}.
     */
    public String getRendererName() { return rendererName; }

    /**
     * Returns additional directories that should be prepended to {@code LD_LIBRARY_PATH}
     * before the game starts.  Typically includes the app's native-library directory so
     * {@code libopenxr_loader.so} and the MobileGlues {@code .so} are reachable via
     * {@code dlopen}.
     */
    public List<Path> getExtraNativeLibDirs() { return extraNativeLibDirs; }

    // -------------------------------------------------------------------------
    // Convenience factories
    // -------------------------------------------------------------------------

    /**
     * Returns a configuration pre-set for Meta Quest Android devices.
     *
     * <p>Pass the app's native-library directory (from
     * {@code context.getApplicationInfo().nativeLibraryDir}) so that
     * {@code libopenxr_loader.so} bundled in the APK is on the {@code LD_LIBRARY_PATH}
     * and can be loaded by subsequent {@code dlopen} calls inside the JVM.
     *
     * @param nativeLibraryDir the app's JNI native-library directory; must contain
     *                         {@code libopenxr_loader.so}
     */
    public static OpenXrConfig forMetaQuest(Path nativeLibraryDir) {
        return builder()
                .loaderLibraryPath(nativeLibraryDir.resolve("libopenxr_loader.so"))
                .addExtraNativeLibDir(nativeLibraryDir)
                .rendererName(RENDERER_MOBILEGLUES)
                .build();
    }

    /**
     * Returns a configuration suitable for desktop SteamVR / Monado.
     *
     * <p>SteamVR sets {@code XR_RUNTIME_JSON} globally, so in most cases you do not need
     * to configure {@link Builder#runtimeJsonPath(Path)}.  Provide the loader path only if
     * the OpenXR loader is not on the default system library search path.
     *
     * @param loaderLibraryPath path to {@code libopenxr_loader.so} /
     *                          {@code openxr_loader-1_0.dll}, or {@code null}
     */
    public static OpenXrConfig forSteamVr(Path loaderLibraryPath) {
        return builder()
                .loaderLibraryPath(loaderLibraryPath)
                .build();
    }

    /** Creates a new {@link Builder}. */
    public static Builder builder() { return new Builder(); }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /** Builder for {@link OpenXrConfig}. */
    public static final class Builder {

        private Path   loaderLibraryPath;
        private Path   runtimeJsonPath;
        private final List<Path> apiLayerPaths = new ArrayList<>();
        private String loaderDebugLevel;
        private boolean validationLayerEnabled = false;
        private Path   glesLibraryPath;
        private Path   eglLibraryPath;
        private int    glesVersion  = 3;
        private String rendererName = RENDERER_MOBILEGLUES;
        private final List<Path> extraNativeLibDirs = new ArrayList<>();

        private Builder() {}

        /**
         * Sets the path to the OpenXR loader shared library
         * ({@code libopenxr_loader.so} on Android / Linux,
         * {@code openxr_loader-1_0.dll} on Windows).
         * When set, {@link OpenXrSetup#getSystemProperties()} includes
         * {@code org.lwjgl.openxr.libname} pointing to this path.
         */
        public Builder loaderLibraryPath(Path path) {
            this.loaderLibraryPath = path; return this;
        }

        /**
         * Sets the path to the OpenXR runtime JSON manifest
         * ({@code XR_RUNTIME_JSON} environment variable).
         * Not required on Meta Quest (runtime discovered via Android package manager)
         * or when SteamVR is already running (sets the variable itself).
         */
        public Builder runtimeJsonPath(Path path) {
            this.runtimeJsonPath = path; return this;
        }

        /**
         * Adds a directory containing OpenXR API layer JSON manifests
         * ({@code XR_API_LAYER_PATH}).
         */
        public Builder addApiLayerPath(Path path) {
            this.apiLayerPaths.add(path); return this;
        }

        /**
         * Sets the {@code XR_LOADER_DEBUG} level:
         * {@code none}, {@code error}, {@code warn}, {@code info}, {@code verbose},
         * {@code all}.
         */
        public Builder loaderDebugLevel(String level) {
            this.loaderDebugLevel = level; return this;
        }

        /**
         * Enables the OpenXR validation layer via {@code XR_ENABLE_API_LAYERS}.
         * Useful in development; should be {@code false} in production.
         */
        public Builder validationLayerEnabled(boolean enabled) {
            this.validationLayerEnabled = enabled; return this;
        }

        /**
         * Overrides the OpenGL ES 3 library path used for
         * {@code org.lwjgl.opengles.libname} (default: {@value #SYSTEM_GLES3_PATH}).
         */
        public Builder glesLibraryPath(Path path) {
            this.glesLibraryPath = path; return this;
        }

        /**
         * Overrides the EGL library path used for {@code org.lwjgl.egl.libname}
         * (default: {@value #SYSTEM_EGL_DRI_PATH}).
         */
        public Builder eglLibraryPath(Path path) {
            this.eglLibraryPath = path; return this;
        }

        /**
         * Sets the OpenGL ES version for the {@code LIBGL_ES} environment variable
         * (default: {@code 3}).  QuestCraft uses {@code 2} historically; {@code 3}
         * is recommended for current MobileGlues builds.
         */
        public Builder glesVersion(int version) {
            this.glesVersion = version; return this;
        }

        /**
         * Sets the renderer name for the {@code POJLIB_RENDERER} environment variable
         * (default: {@value #RENDERER_MOBILEGLUES}).
         */
        public Builder rendererName(String name) {
            this.rendererName = name; return this;
        }

        /**
         * Adds a directory to prepend to {@code LD_LIBRARY_PATH} so that native
         * libraries (OpenXR loader, MobileGlues, etc.) in that directory are found
         * by subsequent {@code dlopen} calls.
         */
        public Builder addExtraNativeLibDir(Path dir) {
            this.extraNativeLibDirs.add(dir); return this;
        }

        /** Builds the {@link OpenXrConfig}. */
        public OpenXrConfig build() { return new OpenXrConfig(this); }
    }
}
