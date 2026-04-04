package io.minelib.library;

import com.google.gson.Gson;
import io.minelib.download.DownloadManager;
import io.minelib.version.VersionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LibraryManagerTest {

    @TempDir
    Path tempDir;

    private LibraryManager libraryManager;
    private Gson gson;

    @BeforeEach
    void setUp() {
        libraryManager = new LibraryManager(tempDir, new DownloadManager(1));
        gson = new Gson();
    }

    @Test
    void resolveLibraries_returnsEmptyListWhenNoLibraries() {
        VersionInfo version = gson.fromJson("{\"id\":\"1.21\",\"type\":\"release\"}", VersionInfo.class);
        List<LibraryInfo> libs = libraryManager.resolveLibraries(version);
        assertTrue(libs.isEmpty());
    }

    @Test
    void resolveLibraries_includesUnconditionalLibrary() {
        String versionJson = """
                {
                  "id": "1.21",
                  "type": "release",
                  "libraries": [
                    {
                      "name": "com.example:mylib:1.0",
                      "downloads": {
                        "artifact": {
                          "path": "com/example/mylib/1.0/mylib-1.0.jar",
                          "sha1": "abc123",
                          "size": 1024,
                          "url": "https://example.com/mylib-1.0.jar"
                        }
                      }
                    }
                  ]
                }
                """;
        VersionInfo version = gson.fromJson(versionJson, VersionInfo.class);
        List<LibraryInfo> libs = libraryManager.resolveLibraries(version);

        assertEquals(1, libs.size());
        LibraryInfo lib = libs.get(0);
        assertEquals("com.example:mylib:1.0", lib.getName());
        assertEquals("com/example/mylib/1.0/mylib-1.0.jar", lib.getPath());
        assertEquals("abc123", lib.getSha1());
        assertEquals(1024L, lib.getSize());
        assertEquals("https://example.com/mylib-1.0.jar", lib.getUrl());
        assertFalse(lib.isNative());
    }

    @Test
    void resolveLibraries_skipsLibraryWithDisallowRule() {
        // A "disallow" rule with no OS condition means this library is excluded everywhere.
        String versionJson = """
                {
                  "id": "1.21",
                  "type": "release",
                  "libraries": [
                    {
                      "name": "com.example:excluded:1.0",
                      "downloads": {
                        "artifact": {
                          "path": "com/example/excluded/1.0/excluded-1.0.jar",
                          "sha1": "def456",
                          "size": 512,
                          "url": "https://example.com/excluded-1.0.jar"
                        }
                      },
                      "rules": [{ "action": "disallow" }]
                    }
                  ]
                }
                """;
        VersionInfo version = gson.fromJson(versionJson, VersionInfo.class);
        List<LibraryInfo> libs = libraryManager.resolveLibraries(version);
        assertTrue(libs.isEmpty(), "Library with unconditional disallow rule must be excluded");
    }

    @Test
    void resolveLibraries_includesLibraryWithAllowRule() {
        String versionJson = """
                {
                  "id": "1.21",
                  "type": "release",
                  "libraries": [
                    {
                      "name": "com.example:allowed:1.0",
                      "downloads": {
                        "artifact": {
                          "path": "com/example/allowed/1.0/allowed-1.0.jar",
                          "sha1": "ghi789",
                          "size": 256,
                          "url": "https://example.com/allowed-1.0.jar"
                        }
                      },
                      "rules": [{ "action": "allow" }]
                    }
                  ]
                }
                """;
        VersionInfo version = gson.fromJson(versionJson, VersionInfo.class);
        List<LibraryInfo> libs = libraryManager.resolveLibraries(version);
        assertEquals(1, libs.size());
        assertEquals("com.example:allowed:1.0", libs.get(0).getName());
    }

    @Test
    void buildClasspath_endsWithClientJar() {
        VersionInfo version = gson.fromJson("{\"id\":\"1.21\",\"type\":\"release\"}", VersionInfo.class);
        List<Path> cp = libraryManager.buildClasspath(version, "1.21");

        assertFalse(cp.isEmpty());
        Path lastEntry = cp.get(cp.size() - 1);
        assertTrue(lastEntry.toString().endsWith("1.21.jar"),
                "Last classpath entry should be the client JAR, got: " + lastEntry);
    }
}
