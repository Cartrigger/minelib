package io.minelib.launch;

import io.minelib.auth.AuthException;
import io.minelib.auth.PlayerProfile;
import io.minelib.library.LibraryManager;
import io.minelib.platform.Platform;
import io.minelib.version.VersionInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the full JVM + game argument list required to launch a Minecraft version.
 *
 * <p>The builder supports both the legacy (pre-1.13) flat {@code minecraftArguments} string
 * and the modern structured {@link VersionInfo.Arguments} format introduced in 1.13.
 */
public final class LaunchArguments {

    /** Template variable substitution map populated from {@link LaunchConfig}. */
    private final Map<String, String> variables;
    private final LaunchConfig config;
    private final LibraryManager libraryManager;

    public LaunchArguments(LaunchConfig config, LibraryManager libraryManager) {
        this.config = config;
        this.libraryManager = libraryManager;

        PlayerProfile profile;
        try {
            profile = config.getAuthProvider().authenticate();
        } catch (AuthException e) {
            throw new IllegalStateException("Cannot build launch arguments: authentication failed", e);
        }

        Path gameDir = config.getGameDirectory();
        VersionInfo version = config.getVersion();

        this.variables = Map.ofEntries(
                Map.entry("${auth_player_name}", profile.getUsername()),
                Map.entry("${auth_uuid}", profile.getUuid()),
                Map.entry("${auth_access_token}", profile.getAccessToken()),
                Map.entry("${user_type}", profile.getUserType()),
                Map.entry("${version_name}", version.getId()),
                Map.entry("${version_type}", version.getType() != null ? version.getType() : "release"),
                Map.entry("${game_directory}", gameDir.toAbsolutePath().toString()),
                Map.entry("${assets_root}", config.getAssetsDirectory().toAbsolutePath().toString()),
                Map.entry("${assets_index_name}",
                        version.getAssetIndex() != null ? version.getAssetIndex().getId() : version.getAssets()),
                Map.entry("${natives_directory}", config.getNativesDirectory().toAbsolutePath().toString()),
                Map.entry("${launcher_name}", "minelib"),
                Map.entry("${launcher_version}", "0.1.0"),
                Map.entry("${classpath}", buildClasspathString()),
                Map.entry("${resolution_width}", String.valueOf(config.getWindowWidth())),
                Map.entry("${resolution_height}", String.valueOf(config.getWindowHeight()))
        );
    }

    /**
     * Builds the complete command-line argument list (JVM args + main class + game args).
     *
     * @return the ordered list of arguments, suitable for passing to {@link ProcessBuilder}
     */
    public List<String> build() {
        List<String> args = new ArrayList<>();

        // JVM memory settings — only meaningful when launching a subprocess (desktop).
        // On Android the game runs in-process under ART and these flags are no-ops, but
        // AndroidGameRunner filters them out before invoking the main class.
        args.add("-Xms" + config.getMinMemoryMb() + "m");
        args.add("-Xmx" + config.getMaxMemoryMb() + "m");

        if (!Platform.isAndroid()) {
            // Native library search path — ART on Android does not use this property;
            // MobileGluesDriver sets the correct path via org.lwjgl.opengl.libname instead.
            args.add("-Djava.library.path=" + config.getNativesDirectory().toAbsolutePath());

            // LWJGL desktop flags — not applicable on Android where LWJGL is replaced by
            // MobileGlues / an OpenGL ES bridge loaded by AndroidGameRunner.
            args.add("-Dorg.lwjgl.util.Debug=false");
            args.add("-Dorg.lwjgl.system.SharedLibraryExtractPath="
                    + config.getNativesDirectory().toAbsolutePath());
        }

        // Structured JVM arguments (1.13+)
        VersionInfo.Arguments structured = config.getVersion().getArguments();
        if (structured != null && structured.getJvm() != null) {
            appendStructuredArgs(args, structured.getJvm());
        }

        // Extra caller-supplied JVM args
        args.addAll(config.getExtraJvmArgs());

        // Main class
        args.add(config.getVersion().getMainClass());

        // Game arguments
        String legacyArgs = config.getVersion().getMinecraftArguments();
        if (legacyArgs != null && !legacyArgs.isBlank()) {
            // Pre-1.13 legacy flat string
            for (String token : legacyArgs.split(" ")) {
                args.add(substitute(token));
            }
        } else if (structured != null && structured.getGame() != null) {
            appendStructuredArgs(args, structured.getGame());
        }

        // Extra caller-supplied game args
        args.addAll(config.getExtraGameArgs());

        return args;
    }

    /**
     * Appends the string tokens from a structured argument list, substituting template
     * variables.  Conditional argument objects (with rules) are silently skipped since
     * feature-flag resolution is out of scope for this initial implementation.
     */
    private void appendStructuredArgs(List<String> target, List<Object> argList) {
        for (Object arg : argList) {
            if (arg instanceof String s) {
                target.add(substitute(s));
            }
            // Conditional argument objects (maps with "rules" + "value") are intentionally
            // skipped here; callers can handle them via extraJvmArgs / extraGameArgs.
        }
    }

    private String substitute(String template) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String buildClasspathString() {
        List<Path> cp = libraryManager.buildClasspath(config.getVersion(), config.getVersion().getId());
        String separator = System.getProperty("path.separator");
        List<String> parts = new ArrayList<>(cp.size());
        for (Path p : cp) {
            parts.add(p.toAbsolutePath().toString());
        }
        return String.join(separator, parts);
    }
}
