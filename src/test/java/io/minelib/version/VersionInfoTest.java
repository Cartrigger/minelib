package io.minelib.version;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that {@link VersionInfo} correctly deserialises the Mojang version JSON format.
 */
class VersionInfoTest {

    private final Gson gson = new Gson();

    @Test
    void javaVersionObjectParsedCorrectly() {
        // Mojang encodes javaVersion as an object, not a plain int.
        // e.g. 1.21: {"component":"java-runtime-gamma","majorVersion":21}
        String json = """
                {
                  "id": "1.21",
                  "type": "release",
                  "javaVersion": {
                    "component": "java-runtime-gamma",
                    "majorVersion": 21
                  }
                }
                """;
        VersionInfo info = gson.fromJson(json, VersionInfo.class);

        assertEquals(21, info.getJavaVersion(),
                "getJavaVersion() must return majorVersion from the nested object");
        assertNotNull(info.getJavaVersionSpec());
        assertEquals("java-runtime-gamma", info.getJavaVersionSpec().getComponent());
        assertEquals(21, info.getJavaVersionSpec().getMajorVersion());
    }

    @Test
    void javaVersionDefaultsTo8WhenAbsent() {
        // Very old versions (pre-1.7.10) omit the javaVersion field entirely.
        String json = """
                {
                  "id": "b1.8.1",
                  "type": "old_beta"
                }
                """;
        VersionInfo info = gson.fromJson(json, VersionInfo.class);

        assertEquals(8, info.getJavaVersion(),
                "getJavaVersion() must return 8 as a safe fallback when javaVersion is absent");
        assertNull(info.getJavaVersionSpec());
    }

    @Test
    void javaVersion17ParsedCorrectly() {
        // Minecraft 1.18–1.20 require Java 17.
        String json = """
                {
                  "id": "1.20.1",
                  "type": "release",
                  "javaVersion": {
                    "component": "java-runtime-gamma",
                    "majorVersion": 17
                  }
                }
                """;
        VersionInfo info = gson.fromJson(json, VersionInfo.class);
        assertEquals(17, info.getJavaVersion());
    }
}
