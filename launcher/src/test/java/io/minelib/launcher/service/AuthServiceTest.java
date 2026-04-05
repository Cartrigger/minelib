package io.minelib.launcher.service;

import io.minelib.auth.PlayerProfile;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AuthServiceTest {

    @TempDir
    Path tempDir;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // tempDir is the launcher directory; auth.json will be written there
        authService = new AuthService(tempDir, mock(OkHttpClient.class));
    }

    // ── createOfflineProfile ──────────────────────────────────────────────────

    @Test
    void offlineProfileHasCorrectUsername() {
        PlayerProfile profile = authService.createOfflineProfile("Steve");
        assertEquals("Steve", profile.getUsername());
    }

    @Test
    void offlineProfileHasLegacyUserType() {
        assertEquals("legacy", authService.createOfflineProfile("Steve").getUserType());
    }

    @Test
    void offlineProfileHasOfflineAccessToken() {
        assertEquals("offline", authService.createOfflineProfile("Steve").getAccessToken());
    }

    @Test
    void offlineProfileUuidIsDeterministic() {
        String uuidA = authService.createOfflineProfile("Steve").getUuid();
        String uuidB = authService.createOfflineProfile("Steve").getUuid();
        assertEquals(uuidA, uuidB, "UUID must be the same for the same username");
    }

    @Test
    void offlineProfileUuidDiffersForDifferentUsernames() {
        String uuidSteve = authService.createOfflineProfile("Steve").getUuid();
        String uuidAlex  = authService.createOfflineProfile("Alex").getUuid();
        assertNotEquals(uuidSteve, uuidAlex);
    }

    @Test
    void offlineProfileUuidContainsNoHyphens() {
        assertFalse(authService.createOfflineProfile("Steve").getUuid().contains("-"));
    }

    @Test
    void offlineProfileThrowsOnNullUsername() {
        assertThrows(IllegalArgumentException.class,
                () -> authService.createOfflineProfile(null));
    }

    @Test
    void offlineProfileThrowsOnEmptyUsername() {
        assertThrows(IllegalArgumentException.class,
                () -> authService.createOfflineProfile(""));
    }

    @Test
    void offlineProfileThrowsOnBlankUsername() {
        assertThrows(IllegalArgumentException.class,
                () -> authService.createOfflineProfile("   "));
    }

    @Test
    void offlineProfileIsStoredViaGetProfile() {
        assertNull(authService.getProfile(), "no profile before sign-in");
        authService.createOfflineProfile("Steve");
        assertNotNull(authService.getProfile());
        assertEquals("Steve", authService.getProfile().getUsername());
    }

    // ── getProfile ────────────────────────────────────────────────────────────

    @Test
    void getProfileInitiallyNull() {
        assertNull(authService.getProfile());
    }

    // ── loadSavedProfile ──────────────────────────────────────────────────────

    @Test
    void loadSavedProfileReturnsNullWhenNoFileExists() {
        assertNull(authService.loadSavedProfile());
    }

    @Test
    void loadSavedProfileReadsValidAuthJson() throws Exception {
        String json = """
                {
                  "username": "Alex",
                  "uuid": "abc123",
                  "accessToken": "token-xyz",
                  "refreshToken": "refresh-abc",
                  "savedAt": 1000000
                }
                """;
        Files.writeString(tempDir.resolve("auth.json"), json);

        PlayerProfile profile = authService.loadSavedProfile();
        assertNotNull(profile);
        assertEquals("Alex",      profile.getUsername());
        assertEquals("abc123",    profile.getUuid());
        assertEquals("token-xyz", profile.getAccessToken());
        assertEquals("msa",       profile.getUserType());
    }

    @Test
    void loadSavedProfileReturnsNullOnMalformedJson() throws Exception {
        Files.writeString(tempDir.resolve("auth.json"), "not valid json {{{{");
        // AuthService catches all exceptions; must not throw, must return null
        assertNull(authService.loadSavedProfile());
    }

    @Test
    void loadSavedProfileReturnsNullWhenRequiredFieldsMissing() throws Exception {
        // accessToken is absent — the if-guard in loadSavedProfile will be false
        Files.writeString(tempDir.resolve("auth.json"),
                "{\"username\":\"x\",\"uuid\":\"y\"}");
        assertNull(authService.loadSavedProfile());
    }

    @Test
    void loadSavedProfileSetsGetProfile() throws Exception {
        Files.writeString(tempDir.resolve("auth.json"),
                "{\"username\":\"Bob\",\"uuid\":\"u1\",\"accessToken\":\"tok\"}");
        authService.loadSavedProfile();
        assertNotNull(authService.getProfile());
        assertEquals("Bob", authService.getProfile().getUsername());
    }

    // ── signOut ───────────────────────────────────────────────────────────────

    @Test
    void signOutClearsProfile() {
        authService.createOfflineProfile("Steve");
        assertNotNull(authService.getProfile());
        authService.signOut();
        assertNull(authService.getProfile());
    }

    @Test
    void signOutDeletesAuthFile() throws Exception {
        Path authFile = tempDir.resolve("auth.json");
        Files.writeString(authFile, "{}");
        assertTrue(Files.exists(authFile));
        authService.signOut();
        assertFalse(Files.exists(authFile));
    }

    @Test
    void signOutIsSafeWithNoProfileAndNoFile() {
        assertDoesNotThrow(() -> authService.signOut());
    }

    @Test
    void signOutAfterLoadedProfileDeletesFile() throws Exception {
        String json = "{\"username\":\"Bob\",\"uuid\":\"u1\",\"accessToken\":\"tok\"}";
        Files.writeString(tempDir.resolve("auth.json"), json);
        authService.loadSavedProfile();
        authService.signOut();
        assertFalse(Files.exists(tempDir.resolve("auth.json")));
        assertNull(authService.getProfile());
    }
}
