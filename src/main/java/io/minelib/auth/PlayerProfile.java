package io.minelib.auth;

/**
 * Represents an authenticated Minecraft player's profile.
 *
 * <p>Instances are produced by an {@link AuthProvider} after successful authentication and are
 * passed to {@link io.minelib.launch.LaunchConfig} so that the launcher can inject the
 * correct credentials into the game's command-line arguments.
 */
public final class PlayerProfile {

    private final String username;
    private final String uuid;
    private final String accessToken;
    private final String userType;

    private PlayerProfile(Builder builder) {
        this.username = builder.username;
        this.uuid = builder.uuid;
        this.accessToken = builder.accessToken;
        this.userType = builder.userType;
    }

    /** Returns the player's in-game username. */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the player's UUID in un-dashed form (e.g. {@code "069a79f444e94726a5befca90e38aaf5"}).
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Returns the Minecraft session access token used to authenticate with game servers and the
     * session service.
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Returns the user type string passed to the game via {@code --userType}.
     * Typically {@code "msa"} for Microsoft accounts.
     */
    public String getUserType() {
        return userType;
    }

    @Override
    public String toString() {
        return "PlayerProfile{username='" + username + "', uuid='" + uuid + "', userType='" + userType + "'}";
    }

    /** Creates a new builder for {@link PlayerProfile}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link PlayerProfile}. */
    public static final class Builder {

        private String username;
        private String uuid;
        private String accessToken;
        private String userType = "msa";

        private Builder() {}

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder uuid(String uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder userType(String userType) {
            this.userType = userType;
            return this;
        }

        public PlayerProfile build() {
            if (username == null || username.isBlank()) throw new IllegalStateException("username must be set");
            if (uuid == null || uuid.isBlank()) throw new IllegalStateException("uuid must be set");
            if (accessToken == null || accessToken.isBlank()) throw new IllegalStateException("accessToken must be set");
            if (userType == null || userType.isBlank()) throw new IllegalStateException("userType must be set");
            return new PlayerProfile(this);
        }
    }
}
