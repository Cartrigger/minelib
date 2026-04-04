package io.minelib.platform;

import java.util.Locale;

/**
 * Utility class for detecting the current platform at runtime.
 *
 * <p>Platform detection is used throughout minelib to enable Android-specific code paths:
 * <ul>
 *   <li>Skipping desktop JRE downloads — Android uses the ART runtime.</li>
 *   <li>Skipping desktop-only JVM flags (e.g. {@code -Djava.library.path}) in launch
 *       arguments.</li>
 *   <li>Selecting the correct {@link io.minelib.launch.GameRunner} implementation.</li>
 * </ul>
 *
 * <p>Android detection is performed by checking for the presence of the
 * {@code android.os.Build} class, which is always available on Android but never on a
 * standard JVM.
 */
public final class Platform {

    private Platform() {}

    /**
     * Returns {@code true} if the library is running on the Android (ART) runtime.
     *
     * <p>Detection is done by attempting to load {@code android.os.Build}, which is present
     * on all Android API levels and absent on standard desktop JVMs.
     */
    public static boolean isAndroid() {
        try {
            Class.forName("android.os.Build");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Returns {@code true} if the current OS is Microsoft Windows. */
    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Returns {@code true} if the current OS is macOS / Darwin. */
    public static boolean isMac() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return name.contains("mac") || name.contains("darwin");
    }

    /** Returns {@code true} if the current OS is Linux (or any non-Windows, non-Mac OS). */
    public static boolean isLinux() {
        return !isWindows() && !isMac();
    }

    /**
     * Returns the Minecraft-style OS name for the current platform:
     * {@code "windows"}, {@code "osx"}, or {@code "linux"}.
     */
    public static String minecraftOsName() {
        if (isWindows()) return "windows";
        if (isMac()) return "osx";
        return "linux";
    }
}
