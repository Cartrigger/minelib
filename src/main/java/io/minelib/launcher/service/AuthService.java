package io.minelib.launcher.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.minelib.auth.PlayerProfile;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Manages Microsoft / Xbox Live / Minecraft authentication for the launcher.
 *
 * <h2>Auth flow</h2>
 * <p>Uses the <em>Device Code</em> OAuth 2.0 flow, which avoids the need for a
 * local redirect server:
 * <ol>
 *   <li>Request a device code from Microsoft.</li>
 *   <li>Show the user a short {@code user_code} and a URL ({@code microsoft.com/link}).</li>
 *   <li>Poll for a token in the background while the user authenticates.</li>
 *   <li>Exchange the MSA token for an Xbox Live, XSTS, and Minecraft token.</li>
 *   <li>Fetch the Minecraft profile (username + UUID).</li>
 * </ol>
 *
 * <h2>Token persistence</h2>
 * <p>The refresh token and profile are stored in {@code <launcherDir>/auth.json} so the
 * user only needs to log in once.
 *
 * <h2>Client ID</h2>
 * <p>You must register an Azure application at {@code https://portal.azure.com} with:
 * <ul>
 *   <li>Platform: <em>Mobile and desktop applications</em></li>
 *   <li>Allow public client flows: <em>yes</em></li>
 *   <li>API permission: {@code XboxLive.signin offline_access}</li>
 * </ul>
 * Pass the resulting Application (client) ID via {@link #setClientId(String)}.
 */
public final class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    // Microsoft Azure / Xbox endpoints
    private static final String DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_URL  =
            "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL =
            "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile";

    private static final okhttp3.MediaType JSON_TYPE =
            okhttp3.MediaType.get("application/json; charset=utf-8");

    private final Path authFile;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    private String clientId = "YOUR_AZURE_CLIENT_ID"; // configure before calling signIn()
    private PlayerProfile cachedProfile;
    private String storedRefreshToken;

    /**
     * Creates an {@code AuthService} that persists tokens to {@code launcherDir/auth.json}.
     *
     * @param launcherDir the launcher data directory (e.g. {@code ~/.minelib-launcher})
     * @param httpClient  shared OkHttp client
     */
    public AuthService(Path launcherDir, OkHttpClient httpClient) {
        this.authFile   = launcherDir.resolve("auth.json");
        this.httpClient = httpClient;
    }

    /** Sets the Azure application (client) ID. Must be called before {@link #signIn}. */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * Returns the persisted {@link PlayerProfile} if valid tokens exist, or
     * {@code null} if the user has never signed in (or auth data was deleted).
     */
    public PlayerProfile loadSavedProfile() {
        if (!Files.exists(authFile)) return null;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(authFile)).getAsJsonObject();
            storedRefreshToken = json.has("refreshToken")
                    ? json.get("refreshToken").getAsString() : null;
            if (json.has("username") && json.has("uuid") && json.has("accessToken")) {
                cachedProfile = PlayerProfile.builder()
                        .username(json.get("username").getAsString())
                        .uuid(json.get("uuid").getAsString())
                        .accessToken(json.get("accessToken").getAsString())
                        .userType("msa")
                        .build();
            }
            return cachedProfile;
        } catch (Exception e) {
            LOGGER.warn("Could not load saved auth: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Creates an <em>offline</em> {@link PlayerProfile} for demo / LAN play.
     *
     * <p>Offline mode does not authenticate with Microsoft and cannot join
     * online-mode (paid) servers. The UUID is randomly generated.
     *
     * @param username the desired in-game name
     */
    public PlayerProfile createOfflineProfile(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        String fakeUuid = java.util.UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString().replace("-", "");
        cachedProfile = PlayerProfile.builder()
                .username(username)
                .uuid(fakeUuid)
                .accessToken("offline")
                .userType("legacy")
                .build();
        return cachedProfile;
    }

    /** Returns the currently loaded profile, or {@code null} if not signed in. */
    public PlayerProfile getProfile() { return cachedProfile; }

    /**
     * Initiates the Device Code sign-in flow asynchronously.
     *
     * <p>The {@code codeConsumer} is called on a background thread with a human-readable
     * message such as:
     * <pre>
     * Go to https://microsoft.com/link and enter:  ABCD-EFGH
     * </pre>
     *
     * <p>The returned future completes with the authenticated {@link PlayerProfile} on
     * success, or completes exceptionally on error.
     *
     * @param codeConsumer  callback to show the device code message to the user (UI thread
     *                      transition is the caller's responsibility)
     */
    public CompletableFuture<PlayerProfile> signIn(Consumer<String> codeConsumer) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doDeviceCodeFlow(codeConsumer);
            } catch (Exception e) {
                throw new RuntimeException("Sign-in failed: " + e.getMessage(), e);
            }
        });
    }

    /** Removes saved auth data and clears the in-memory profile. */
    public void signOut() {
        cachedProfile = null;
        storedRefreshToken = null;
        try {
            Files.deleteIfExists(authFile);
        } catch (IOException e) {
            LOGGER.warn("Could not delete auth file", e);
        }
    }

    // -------------------------------------------------------------------------
    // Device code flow implementation
    // -------------------------------------------------------------------------

    private PlayerProfile doDeviceCodeFlow(Consumer<String> codeConsumer) throws Exception {
        // Step 1: request device code
        RequestBody dcBody = new FormBody.Builder()
                .add("client_id", clientId)
                .add("scope", "XboxLive.signin offline_access")
                .build();
        JsonObject dcResponse = postForm(DEVICE_CODE_URL, dcBody);

        String deviceCode = dcResponse.get("device_code").getAsString();
        String userCode   = dcResponse.get("user_code").getAsString();
        String verifyUri  = dcResponse.get("verification_uri").getAsString();
        int    interval   = dcResponse.has("interval") ? dcResponse.get("interval").getAsInt() : 5;

        String message = "Go to  " + verifyUri + "  and enter:\n\n    " + userCode;
        codeConsumer.accept(message);

        // Step 2: poll for token
        JsonObject msaToken = null;
        for (int attempt = 0; attempt < 180; attempt++) { // 15 min max
            Thread.sleep(interval * 1000L);
            RequestBody pollBody = new FormBody.Builder()
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .add("client_id", clientId)
                    .add("device_code", deviceCode)
                    .build();
            try {
                msaToken = postForm(TOKEN_URL, pollBody);
                break; // success
            } catch (IOException e) {
                // "authorization_pending" returns a 4xx — keep polling
                if (!e.getMessage().contains("authorization_pending")
                        && !e.getMessage().contains("400")) {
                    throw e;
                }
            }
        }
        if (msaToken == null) throw new IOException("Device code flow timed out");

        String msaAccessToken   = msaToken.get("access_token").getAsString();
        String msaRefreshToken  = msaToken.get("refresh_token").getAsString();

        return finishAuth(msaAccessToken, msaRefreshToken);
    }

    /**
     * Completes the XBL → XSTS → Minecraft chain and persists tokens.
     * Shared by device-code and refresh-token flows.
     */
    private PlayerProfile finishAuth(String msaAccessToken, String msaRefreshToken)
            throws IOException {
        // XBL auth
        String xblJson = "{\"Properties\":{\"AuthMethod\":\"RPS\","
                + "\"SiteName\":\"user.auth.xboxlive.com\","
                + "\"RpsTicket\":\"d=" + msaAccessToken + "\"},"
                + "\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";
        JsonObject xblResp = postJson(XBL_URL, xblJson);
        String xblToken = xblResp.get("Token").getAsString();
        String userHash = xblResp.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject()
                .get("uhs").getAsString();

        // XSTS auth
        String xstsJson = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\""
                + xblToken + "\"]},"
                + "\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}";
        JsonObject xstsResp = postJson(XSTS_URL, xstsJson);
        String xstsToken = xstsResp.get("Token").getAsString();

        // Minecraft auth
        String mcJson = "{\"identityToken\":\"XBL3.0 x=" + userHash + ";" + xstsToken + "\"}";
        JsonObject mcResp = postJson(MC_LOGIN_URL, mcJson);
        String mcToken = mcResp.get("access_token").getAsString();

        // Profile
        Request profileReq = new Request.Builder()
                .url(MC_PROFILE_URL)
                .header("Authorization", "Bearer " + mcToken)
                .build();
        JsonObject profile;
        try (Response r = httpClient.newCall(profileReq).execute()) {
            if (!r.isSuccessful()) throw new IOException("MC profile fetch failed: " + r.code());
            profile = JsonParser.parseString(r.body().string()).getAsJsonObject();
        }

        PlayerProfile playerProfile = PlayerProfile.builder()
                .username(profile.get("name").getAsString())
                .uuid(profile.get("id").getAsString())
                .accessToken(mcToken)
                .userType("msa")
                .build();

        // Persist
        saveAuth(playerProfile, msaRefreshToken);
        this.cachedProfile = playerProfile;
        this.storedRefreshToken = msaRefreshToken;
        return playerProfile;
    }

    private void saveAuth(PlayerProfile profile, String refreshToken) {
        try {
            Files.createDirectories(authFile.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("username", profile.getUsername());
            json.addProperty("uuid", profile.getUuid());
            json.addProperty("accessToken", profile.getAccessToken());
            json.addProperty("refreshToken", refreshToken);
            json.addProperty("savedAt", System.currentTimeMillis());
            Files.writeString(authFile, gson.toJson(json));
        } catch (IOException e) {
            LOGGER.warn("Could not save auth tokens", e);
        }
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    private JsonObject postForm(String url, RequestBody body) throws IOException {
        Request req = new Request.Builder().url(url).post(body).build();
        try (Response r = httpClient.newCall(req).execute()) {
            String respBody = r.body() != null ? r.body().string() : "";
            if (!r.isSuccessful()) {
                throw new IOException("HTTP " + r.code() + " from " + url + ": " + respBody);
            }
            return JsonParser.parseString(respBody).getAsJsonObject();
        }
    }

    private JsonObject postJson(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(json, JSON_TYPE);
        Request req = new Request.Builder().url(url).post(body).build();
        try (Response r = httpClient.newCall(req).execute()) {
            ResponseBody rb = r.body();
            String respBody = rb != null ? rb.string() : "";
            if (!r.isSuccessful()) {
                throw new IOException("HTTP " + r.code() + " from " + url + ": " + respBody);
            }
            return JsonParser.parseString(respBody).getAsJsonObject();
        }
    }
}
