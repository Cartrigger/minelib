package io.minelib.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerProfileTest {

    @Test
    void buildsWithAllFields() {
        PlayerProfile profile = PlayerProfile.builder()
                .username("Steve")
                .uuid("069a79f444e94726a5befca90e38aaf5")
                .accessToken("token123")
                .userType("msa")
                .build();

        assertEquals("Steve", profile.getUsername());
        assertEquals("069a79f444e94726a5befca90e38aaf5", profile.getUuid());
        assertEquals("token123", profile.getAccessToken());
        assertEquals("msa", profile.getUserType());
    }

    @Test
    void defaultUserTypeIsMsa() {
        PlayerProfile profile = PlayerProfile.builder()
                .username("Alex")
                .uuid("someUuid")
                .accessToken("token")
                .build();

        assertEquals("msa", profile.getUserType());
    }

    @Test
    void throwsWhenUsernameMissing() {
        assertThrows(IllegalStateException.class, () ->
                PlayerProfile.builder()
                        .uuid("someUuid")
                        .accessToken("token")
                        .build());
    }

    @Test
    void throwsWhenUuidMissing() {
        assertThrows(IllegalStateException.class, () ->
                PlayerProfile.builder()
                        .username("Steve")
                        .accessToken("token")
                        .build());
    }

    @Test
    void throwsWhenAccessTokenMissing() {
        assertThrows(IllegalStateException.class, () ->
                PlayerProfile.builder()
                        .username("Steve")
                        .uuid("someUuid")
                        .build());
    }

    @Test
    void throwsWhenUsernameIsBlank() {
        assertThrows(IllegalStateException.class, () ->
                PlayerProfile.builder()
                        .username("  ")
                        .uuid("someUuid")
                        .accessToken("token")
                        .build());
    }

    @Test
    void toStringDoesNotLeakAccessToken() {
        PlayerProfile profile = PlayerProfile.builder()
                .username("Steve")
                .uuid("069a79f444e94726a5befca90e38aaf5")
                .accessToken("supersecrettoken")
                .build();

        assertFalse(profile.toString().contains("supersecrettoken"),
                "toString() must not expose the access token");
    }
}
