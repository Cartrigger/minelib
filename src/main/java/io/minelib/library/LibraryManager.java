package io.minelib.library;

import io.minelib.download.DownloadManager;
import io.minelib.download.DownloadTask;
import io.minelib.version.VersionInfo;
import io.minelib.version.VersionInfo.Library;
import io.minelib.version.VersionInfo.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves and downloads the classpath and native libraries required to launch a specific
 * Minecraft version.
 *
 * <p>Libraries are stored under {@code <gameDirectory>/libraries/} following the standard
 * Minecraft launcher layout. Native JARs are additionally extracted into
 * {@code <gameDirectory>/versions/<versionId>/natives/}.
 */
public final class LibraryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryManager.class);

    /** Current OS name as used in Minecraft library rules. */
    private static final String OS_NAME = detectOsName();
    /** Current OS architecture. */
    private static final String OS_ARCH = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

    private final Path gameDirectory;
    private final DownloadManager downloadManager;

    public LibraryManager(Path gameDirectory, DownloadManager downloadManager) {
        this.gameDirectory = gameDirectory;
        this.downloadManager = downloadManager;
    }

    /**
     * Resolves, downloads, and extracts all libraries applicable to the current OS for the given
     * version.
     *
     * @param version the version whose libraries should be installed
     * @throws IOException if any library cannot be downloaded
     */
    public void downloadLibraries(VersionInfo version) throws IOException {
        List<LibraryInfo> libraries = resolveLibraries(version);

        List<DownloadTask> tasks = new ArrayList<>(libraries.size());
        for (LibraryInfo lib : libraries) {
            Path dest = gameDirectory.resolve("libraries").resolve(lib.getPath());
            tasks.add(DownloadTask.builder()
                    .url(lib.getUrl())
                    .destination(dest)
                    .sha1(lib.getSha1())
                    .size(lib.getSize())
                    .build());
        }

        // Ensure parent directories exist for all tasks
        for (DownloadTask task : tasks) {
            Files.createDirectories(task.getDestination().getParent());
        }

        if (!tasks.isEmpty()) {
            LOGGER.info("Downloading {} librar{} for {}", tasks.size(),
                    tasks.size() == 1 ? "y" : "ies", version.getId());
            downloadManager.downloadAll(tasks);
        }
    }

    /**
     * Returns the list of {@link LibraryInfo} objects applicable to the current operating
     * system for the given version.  This does <em>not</em> download anything.
     *
     * @param version the version to resolve libraries for
     * @return list of applicable libraries
     */
    public List<LibraryInfo> resolveLibraries(VersionInfo version) {
        List<LibraryInfo> result = new ArrayList<>();
        if (version.getLibraries() == null) {
            return result;
        }

        for (Library library : version.getLibraries()) {
            if (!isAllowed(library)) {
                continue;
            }

            VersionInfo.LibraryDownloads downloads = library.getDownloads();

            if (downloads != null && downloads.getArtifact() != null) {
                // Standard Mojang/Forge format: fully-specified downloads block
                VersionInfo.Artifact artifact = downloads.getArtifact();
                if (artifact.getUrl() != null && !artifact.getUrl().isBlank()) {
                    result.add(LibraryInfo.builder()
                            .name(library.getName())
                            .path(artifact.getPath())
                            .sha1(artifact.getSha1())
                            .size(artifact.getSize())
                            .url(artifact.getUrl())
                            .isNative(false)
                            .build());
                }
            } else if (library.getName() != null && library.getUrl() != null
                    && !library.getUrl().isBlank()) {
                // Fabric / NeoForge maven-style format: name + repository base URL only.
                // Construct the artifact URL from Maven coordinates.
                LibraryInfo mavenLib = resolveMavenLibrary(library.getName(), library.getUrl());
                if (mavenLib != null) {
                    result.add(mavenLib);
                }
            }

            // Native classifier (if any)
            if (downloads != null && library.getNatives() != null
                    && downloads.getClassifiers() != null) {
                String nativeKey = getNativeClassifier(library);
                if (nativeKey != null) {
                    VersionInfo.Artifact nativeArtifact = downloads.getClassifiers().get(nativeKey);
                    if (nativeArtifact != null && nativeArtifact.getUrl() != null
                            && !nativeArtifact.getUrl().isBlank()) {
                        result.add(LibraryInfo.builder()
                                .name(library.getName() + ":" + nativeKey)
                                .path(nativeArtifact.getPath())
                                .sha1(nativeArtifact.getSha1())
                                .size(nativeArtifact.getSize())
                                .url(nativeArtifact.getUrl())
                                .isNative(true)
                                .build());
                    }
                }
            }
        }

        return result;
    }

    /**
     * Returns the ordered classpath entries (non-native JARs) for the given version.
     *
     * @param version the version
     * @return list of absolute paths to the library JARs
     */
    public List<Path> buildClasspath(VersionInfo version, String versionId) {
        List<Path> classpath = new ArrayList<>();
        for (LibraryInfo lib : resolveLibraries(version)) {
            if (!lib.isNative()) {
                classpath.add(gameDirectory.resolve("libraries").resolve(lib.getPath()));
            }
        }
        // Add the client JAR last
        classpath.add(gameDirectory.resolve("versions").resolve(versionId).resolve(versionId + ".jar"));
        return classpath;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a Maven-style library (name + repository base URL, no {@code downloads}
     * block) into a {@link LibraryInfo}.
     *
     * <p>Given {@code name = "net.fabricmc:fabric-loader:0.16.10"} and
     * {@code repoUrl = "https://maven.fabricmc.net/"}, this produces:
     * <ul>
     *   <li>path: {@code net/fabricmc/fabric-loader/0.16.10/fabric-loader-0.16.10.jar}</li>
     *   <li>url:  {@code https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.16.10/fabric-loader-0.16.10.jar}</li>
     * </ul>
     *
     * @return the resolved {@link LibraryInfo}, or {@code null} if the name is malformed
     */
    private static LibraryInfo resolveMavenLibrary(String name, String repoUrl) {
        // Maven coordinates: group:artifact:version  (optionally :classifier)
        String[] parts = name.split(":");
        if (parts.length < 3) {
            return null;
        }
        String group    = parts[0];
        String artifact = parts[1];
        String version  = parts[2];
        String classifier = parts.length >= 4 ? parts[3] : null;

        String relPath = mavenCoordinatesToPath(group, artifact, version, classifier);
        String base = repoUrl.endsWith("/") ? repoUrl : repoUrl + "/";
        String url = base + relPath;

        return LibraryInfo.builder()
                .name(name)
                .path(relPath)
                .url(url)
                .isNative(false)
                .build();
    }

    /**
     * Converts Maven group/artifact/version coordinates to the standard relative JAR path,
     * e.g. {@code net/fabricmc/fabric-loader/0.16.10/fabric-loader-0.16.10.jar}.
     */
    public static String mavenCoordinatesToPath(String group, String artifact,
                                                 String version, String classifier) {
        String groupPath = group.replace('.', '/');
        String fileName = classifier == null
                ? artifact + "-" + version + ".jar"
                : artifact + "-" + version + "-" + classifier + ".jar";
        return groupPath + "/" + artifact + "/" + version + "/" + fileName;
    }

    /**
     * Evaluates the library's rule list against the current OS and returns {@code true} if
     * the library should be included.
     */
    private boolean isAllowed(Library library) {
        List<Rule> rules = library.getRules();
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        boolean allowed = false;
        for (Rule rule : rules) {
            if (ruleMatches(rule)) {
                allowed = "allow".equals(rule.getAction());
            }
        }
        return allowed;
    }

    private boolean ruleMatches(Rule rule) {
        VersionInfo.OsCondition os = rule.getOs();
        if (os == null) {
            // No OS restriction — applies to all platforms
            return true;
        }
        if (os.getName() != null && !os.getName().equals(OS_NAME)) {
            return false;
        }
        if (os.getArch() != null && !OS_ARCH.contains(os.getArch())) {
            return false;
        }
        return true;
    }

    /** Returns the native classifier key for the current OS, or {@code null}. */
    private String getNativeClassifier(Library library) {
        VersionInfo.Natives natives = library.getNatives();
        if (natives == null) {
            return null;
        }
        return switch (OS_NAME) {
            case "linux" -> natives.getLinux();
            case "osx" -> natives.getOsx();
            case "windows" -> natives.getWindows();
            default -> null;
        };
    }

    /** Returns the Minecraft-style OS name for the current platform. */
    private static String detectOsName() {
        String raw = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (raw.contains("win")) return "windows";
        if (raw.contains("mac") || raw.contains("darwin")) return "osx";
        return "linux";
    }
}
