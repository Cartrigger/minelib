package io.minelib.auth;

/**
 * Abstraction over different Minecraft authentication mechanisms.
 *
 * <p>Implementations should handle the full authentication flow for a given account type and
 * return a ready-to-use {@link PlayerProfile}. The library ships with
 * {@link MicrosoftAuthProvider} for Microsoft (Xbox Live / MSA) accounts, which is the only
 * account type officially supported by Mojang since June 2022.
 *
 * <p>Offline-mode profiles can be created directly with {@link PlayerProfile.Builder} without
 * going through an {@code AuthProvider}.
 */
public interface AuthProvider {

    /**
     * Performs the full authentication flow and returns the resulting {@link PlayerProfile}.
     *
     * @return the authenticated player profile
     * @throws AuthException if authentication fails for any reason
     */
    PlayerProfile authenticate() throws AuthException;

    /**
     * Refreshes a previously issued session, exchanging an expired access token for a new one.
     *
     * @param profile the profile whose token needs refreshing
     * @return a new {@link PlayerProfile} with a fresh access token
     * @throws AuthException if the refresh fails (e.g. the refresh token has also expired)
     */
    PlayerProfile refresh(PlayerProfile profile) throws AuthException;

    /**
     * Checks whether the given profile's access token is still valid.
     *
     * @param profile the profile to validate
     * @return {@code true} if the token is valid, {@code false} otherwise
     */
    boolean validate(PlayerProfile profile);
}
