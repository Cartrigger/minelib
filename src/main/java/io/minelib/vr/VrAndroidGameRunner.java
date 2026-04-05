package io.minelib.vr;

import io.minelib.android.AndroidGameRunner;
import io.minelib.android.MobileGluesDriver;
import io.minelib.launch.GameProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Android {@link io.minelib.launch.GameRunner} that launches Minecraft with full VR support
 * for Meta Quest (QuestCraft / Vivecraft), combining:
 * <ul>
 *   <li><strong>MobileGlues</strong> — OpenGL → OpenGL ES translation layer
 *       (via {@link MobileGluesDriver})</li>
 *   <li><strong>OpenXR</strong> — VR runtime integration for head-tracking and stereoscopic
 *       rendering (via {@link OpenXrSetup})</li>
 * </ul>
 *
 * <h2>Startup sequence (mirrors Pojlib's {@code JREUtils.launchJavaVM})</h2>
 * <ol>
 *   <li>{@link OpenXrSetup#applyAndroidEnvironment()} — calls {@code android.system.Os.setenv}
 *       to inject {@code LIBGL_ES}, {@code LD_LIBRARY_PATH} (with the native-lib dir prepended
 *       so {@code libopenxr_loader.so} is findable via {@code dlopen}), and
 *       {@code POJLIB_RENDERER} into the running process environment.</li>
 *   <li>{@link OpenXrSetup#applySystemProperties()} — sets LWJGL system properties:
 *       {@code org.lwjgl.openxr.libname}, {@code org.lwjgl.opengles.libname},
 *       {@code org.lwjgl.egl.libname}.</li>
 *   <li>{@link AndroidGameRunner#run} (super) — downloads and loads {@code libMobileGlues.so},
 *       sets {@code org.lwjgl.opengl.libname} to MobileGlues, then launches Minecraft
 *       in-process via a {@link ClassLoader} thread.</li>
 * </ol>
 *
 * <h2>Vivecraft / VLoader JNI bridge</h2>
 * <p>Vivecraft calls into the {@code org.vivecraft.util.VLoader} JNI class to obtain the
 * current EGL display, EGL context, EGL config, and Dalvik VM pointer for OpenXR session
 * creation.  These are provided by Pojlib's {@code libvloader.so} (compiled from
 * {@code vloader.cpp}).  If you ship {@code libvloader.so} in your APK's JNI libs directory,
 * those calls will be satisfied automatically at runtime.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // nativeLibDir = context.getApplicationInfo().nativeLibraryDir
 * Path nativeLibDir = Path.of(context.getApplicationInfo().nativeLibraryDir);
 *
 * // Configure MobileGlues
 * MobileGluesConfig mglConfig = MobileGluesConfig.builder()
 *     .installDirectory(filesDir.resolve("mobileglues"))
 *     .build();
 * MobileGluesDriver mglDriver = new MobileGluesDriver(mglConfig, downloadManager);
 *
 * // Configure OpenXR (loaderLibraryPath = nativeLibDir/libopenxr_loader.so)
 * OpenXrConfig xrConfig = OpenXrConfig.forMetaQuest(nativeLibDir);
 * OpenXrSetup  xrSetup  = new OpenXrSetup(xrConfig);
 *
 * // Build MineLib with the VR runner
 * MineLib minelib = MineLib.builder()
 *     .gameDirectory(filesDir.resolve(".minecraft"))
 *     .gameRunner(new VrAndroidGameRunner(mglDriver, xrSetup))
 *     .build();
 * }</pre>
 */
public final class VrAndroidGameRunner extends AndroidGameRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(VrAndroidGameRunner.class);

    private final OpenXrSetup openXrSetup;

    /**
     * Creates a VR-capable Android game runner.
     *
     * @param mobileGluesDriver configured MobileGlues driver (handles OpenGL ES rendering)
     * @param openXrSetup       configured OpenXR setup (handles VR runtime + head-tracking)
     */
    public VrAndroidGameRunner(MobileGluesDriver mobileGluesDriver, OpenXrSetup openXrSetup) {
        super(mobileGluesDriver);
        if (openXrSetup == null) throw new NullPointerException("openXrSetup must not be null");
        this.openXrSetup = openXrSetup;
    }

    /**
     * Applies the OpenXR environment and system properties, then delegates to
     * {@link AndroidGameRunner#run} for MobileGlues setup and in-process game loading.
     *
     * <p>Application order:
     * <ol>
     *   <li>Android env vars via {@code Os.setenv} ({@code LIBGL_ES},
     *       {@code LD_LIBRARY_PATH}, {@code POJLIB_RENDERER}, OpenXR loader vars).</li>
     *   <li>LWJGL system properties ({@code org.lwjgl.openxr.libname},
     *       {@code org.lwjgl.opengles.libname}, {@code org.lwjgl.egl.libname}).</li>
     *   <li>MobileGlues super ({@code org.lwjgl.opengl.libname} + game thread).</li>
     * </ol>
     */
    @Override
    public GameProcess run(List<String> command, Path workingDirectory) throws IOException {
        LOGGER.info("Setting up OpenXR environment for VR launch");

        // Step 1 — inject env vars into the running Android process via Os.setenv
        openXrSetup.applyAndroidEnvironment();

        // Step 2 — apply LWJGL system properties
        openXrSetup.applySystemProperties();
        Map<String, String> sysProps = openXrSetup.getSystemProperties();
        if (!sysProps.isEmpty()) {
            LOGGER.debug("OpenXR system properties applied: {}", sysProps.keySet());
        }

        // Step 3 — MobileGlues setup + game launch (AndroidGameRunner)
        return super.run(command, workingDirectory);
    }

    /** Returns the {@link OpenXrSetup} used by this runner. */
    public OpenXrSetup getOpenXrSetup() { return openXrSetup; }
}
