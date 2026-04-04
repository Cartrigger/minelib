package io.minelib.launch;

import com.google.gson.Gson;
import io.minelib.auth.AuthException;
import io.minelib.auth.AuthProvider;
import io.minelib.auth.PlayerProfile;
import io.minelib.runtime.JavaRuntime;
import io.minelib.version.VersionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LaunchConfigTest {

    @TempDir
    Path tempDir;

    private VersionInfo version;
    private AuthProvider authProvider;
    private JavaRuntime javaRuntime;

    @BeforeEach
    void setUp() {
        // Use Gson to initialise VersionInfo so getId() returns a real value, which is required
        // by LaunchConfig when computing the default natives directory path.
        version = new Gson().fromJson(
                "{\"id\":\"1.21\",\"type\":\"release\",\"mainClass\":\"net.minecraft.client.main.Main\"}",
                VersionInfo.class);
        authProvider = new StubAuthProvider();
        javaRuntime = new JavaRuntime(17, "Eclipse Temurin", Path.of("/java/home"));
    }

    @Test
    void buildsWithRequiredFields() {
        LaunchConfig config = LaunchConfig.builder()
                .version(version)
                .authProvider(authProvider)
                .javaRuntime(javaRuntime)
                .gameDirectory(tempDir)
                .build();

        assertSame(version, config.getVersion());
        assertSame(authProvider, config.getAuthProvider());
        assertSame(javaRuntime, config.getJavaRuntime());
        assertEquals(tempDir, config.getGameDirectory());
    }

    @Test
    void defaultsAreApplied() {
        LaunchConfig config = LaunchConfig.builder()
                .version(version)
                .authProvider(authProvider)
                .javaRuntime(javaRuntime)
                .gameDirectory(tempDir)
                .build();

        assertEquals(512, config.getMinMemoryMb());
        assertEquals(2048, config.getMaxMemoryMb());
        assertEquals(854, config.getWindowWidth());
        assertEquals(480, config.getWindowHeight());
        assertTrue(config.getExtraJvmArgs().isEmpty());
        assertTrue(config.getExtraGameArgs().isEmpty());
    }

    @Test
    void derivedDirectoriesDefaultToSubdirectoriesOfGameDir() {
        LaunchConfig config = LaunchConfig.builder()
                .version(version)
                .authProvider(authProvider)
                .javaRuntime(javaRuntime)
                .gameDirectory(tempDir)
                .build();

        assertEquals(tempDir.resolve("assets"), config.getAssetsDirectory());
        assertEquals(tempDir.resolve("libraries"), config.getLibrariesDirectory());
    }

    @Test
    void extraArgsAreImmutable() {
        LaunchConfig config = LaunchConfig.builder()
                .version(version)
                .authProvider(authProvider)
                .javaRuntime(javaRuntime)
                .gameDirectory(tempDir)
                .extraJvmArgs(List.of("-XX:+UseG1GC"))
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> config.getExtraJvmArgs().add("-Xss1m"));
    }

    @Test
    void throwsWhenVersionMissing() {
        assertThrows(IllegalStateException.class, () ->
                LaunchConfig.builder()
                        .authProvider(authProvider)
                        .javaRuntime(javaRuntime)
                        .gameDirectory(tempDir)
                        .build());
    }

    @Test
    void throwsWhenAuthProviderMissing() {
        assertThrows(IllegalStateException.class, () ->
                LaunchConfig.builder()
                        .version(version)
                        .javaRuntime(javaRuntime)
                        .gameDirectory(tempDir)
                        .build());
    }

    @Test
    void throwsWhenJavaRuntimeMissing() {
        assertThrows(IllegalStateException.class, () ->
                LaunchConfig.builder()
                        .version(version)
                        .authProvider(authProvider)
                        .gameDirectory(tempDir)
                        .build());
    }

    @Test
    void throwsWhenGameDirectoryMissing() {
        assertThrows(IllegalStateException.class, () ->
                LaunchConfig.builder()
                        .version(version)
                        .authProvider(authProvider)
                        .javaRuntime(javaRuntime)
                        .build());
    }

    @Test
    void throwsWhenMaxMemoryLessThanMin() {
        assertThrows(IllegalStateException.class, () ->
                LaunchConfig.builder()
                        .version(version)
                        .authProvider(authProvider)
                        .javaRuntime(javaRuntime)
                        .gameDirectory(tempDir)
                        .minMemoryMb(2048)
                        .maxMemoryMb(512)
                        .build());
    }

    // -------------------------------------------------------------------------
    // Stub helpers
    // -------------------------------------------------------------------------

    private static final class StubAuthProvider implements AuthProvider {

        @Override
        public PlayerProfile authenticate() throws AuthException {
            return PlayerProfile.builder()
                    .username("TestPlayer")
                    .uuid("00000000000000000000000000000001")
                    .accessToken("test-token")
                    .build();
        }

        @Override
        public PlayerProfile refresh(PlayerProfile profile) throws AuthException {
            return authenticate();
        }

        @Override
        public boolean validate(PlayerProfile profile) {
            return true;
        }
    }
}
