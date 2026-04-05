package io.minelib.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Implements the Microsoft / Xbox Live / Minecraft authentication flow.
 *
 * <p>The full flow is:
 * <ol>
 *   <li>Exchange a Microsoft OAuth 2.0 authorization code for an MSA token.</li>
 *   <li>Authenticate with Xbox Live using the MSA token.</li>
 *   <li>Obtain an XSTS token from the Xbox Security Token Service.</li>
 *   <li>Authenticate with the Minecraft services API using the XSTS token.</li>
 *   <li>Fetch the Minecraft profile (username + UUID).</li>
 * </ol>
 *
 * <p>This class is designed to work with the <em>Device Code</em> or
 * <em>Authorization Code</em> OAuth 2.0 flows. The caller is responsible for obtaining a
 * valid MSA authorization code (or refresh token) before invoking {@link #authenticate()}.
 * Pass it via the constructor using {@link Builder#authorizationCode(String)} or
 * {@link Builder#refreshToken(String)}.
 */
public final class MicrosoftAuthProvider implements AuthProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(MicrosoftAuthProvider.class);

    // Microsoft OAuth endpoints
    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    // Xbox Live endpoints
    private static final String XBL_AUTH_URL =
            "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTH_URL =
            "https://xsts.auth.xboxlive.com/xsts/authorize";
    // Minecraft services endpoints
    private static final String MC_LOGIN_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final String clientId;
    private final String redirectUri;
    private final String authorizationCode;
    private final String refreshToken;

    private MicrosoftAuthProvider(Builder builder) {
        this.httpClient = builder.httpClient != null ? builder.httpClient : new OkHttpClient();
        this.clientId = builder.clientId;
        this.redirectUri = builder.redirectUri;
        this.authorizationCode = builder.authorizationCode;
        this.refreshToken = builder.refreshToken;
    }

    @Override
    public PlayerProfile authenticate() throws AuthException {
        try {
            String msaAccessToken;
            String msaRefreshToken;

            if (refreshToken != null) {
                JsonObject msaToken = refreshMsaToken(refreshToken);
                msaAccessToken = msaToken.get("access_token").getAsString();
                msaRefreshToken = msaToken.get("refresh_token").getAsString();
            } else if (authorizationCode != null) {
                JsonObject msaToken = exchangeCodeForToken(authorizationCode);
                msaAccessToken = msaToken.get("access_token").getAsString();
                msaRefreshToken = msaToken.get("refresh_token").getAsString();
            } else {
                throw new AuthException("Either authorizationCode or refreshToken must be provided");
            }

            LOGGER.debug("Obtained MSA token, authenticating with Xbox Live");
            JsonObject xblResponse = authenticateWithXboxLive(msaAccessToken);
            String xblToken = xblResponse.getAsJsonObject("Token").getAsString();
            String userHash = xblResponse.getAsJsonObject("DisplayClaims")
                    .getAsJsonArray("xui").get(0).getAsJsonObject()
                    .get("uhs").getAsString();

            LOGGER.debug("Obtained XBL token, requesting XSTS token");
            JsonObject xstsResponse = requestXstsToken(xblToken);
            String xstsToken = xstsResponse.get("Token").getAsString();

            LOGGER.debug("Obtained XSTS token, authenticating with Minecraft services");
            String mcAccessToken = authenticateWithMinecraft(userHash, xstsToken);

            LOGGER.debug("Obtaining Minecraft profile");
            JsonObject profile = fetchMinecraftProfile(mcAccessToken);

            return PlayerProfile.builder()
                    .username(profile.get("name").getAsString())
                    .uuid(profile.get("id").getAsString())
                    .accessToken(mcAccessToken)
                    .userType("msa")
                    .build();
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException("Microsoft authentication failed", e);
        }
    }

    @Override
    public PlayerProfile refresh(PlayerProfile profile) throws AuthException {
        if (refreshToken == null) {
            throw new AuthException("No refresh token available; create a new MicrosoftAuthProvider with a refresh token");
        }
        return authenticate();
    }

    @Override
    public boolean validate(PlayerProfile profile) {
        Request request = new Request.Builder()
                .url(MC_PROFILE_URL)
                .header("Authorization", "Bearer " + profile.getAccessToken())
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            LOGGER.warn("Could not validate token", e);
            return false;
        }
    }

    private JsonObject exchangeCodeForToken(String code) throws IOException, AuthException {
        RequestBody body = new FormBody.Builder()
                .add("client_id", clientId)
                .add("code", code)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", redirectUri)
                .add("scope", "XboxLive.signin offline_access")
                .build();
        return postForm(TOKEN_URL, body);
    }

    private JsonObject refreshMsaToken(String token) throws IOException, AuthException {
        RequestBody body = new FormBody.Builder()
                .add("client_id", clientId)
                .add("refresh_token", token)
                .add("grant_type", "refresh_token")
                .add("scope", "XboxLive.signin offline_access")
                .build();
        return postForm(TOKEN_URL, body);
    }

    private JsonObject authenticateWithXboxLive(String msaAccessToken) throws IOException, AuthException {
        String json = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\","
                + "\"RpsTicket\":\"d=" + msaAccessToken + "\"},"
                + "\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";
        return postJson(XBL_AUTH_URL, json);
    }

    private JsonObject requestXstsToken(String xblToken) throws IOException, AuthException {
        String json = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblToken + "\"]},"
                + "\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}";
        return postJson(XSTS_AUTH_URL, json);
    }

    private String authenticateWithMinecraft(String userHash, String xstsToken) throws IOException, AuthException {
        String json = "{\"identityToken\":\"XBL3.0 x=" + userHash + ";" + xstsToken + "\"}";
        JsonObject response = postJson(MC_LOGIN_URL, json);
        return response.get("access_token").getAsString();
    }

    private JsonObject fetchMinecraftProfile(String accessToken) throws IOException, AuthException {
        Request request = new Request.Builder()
                .url(MC_PROFILE_URL)
                .header("Authorization", "Bearer " + accessToken)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String bodyStr = requireSuccessBody(response, MC_PROFILE_URL);
            return JsonParser.parseString(bodyStr).getAsJsonObject();
        }
    }

    private JsonObject postForm(String url, RequestBody body) throws IOException, AuthException {
        Request request = new Request.Builder().url(url).post(body).build();
        try (Response response = httpClient.newCall(request).execute()) {
            String bodyStr = requireSuccessBody(response, url);
            return JsonParser.parseString(bodyStr).getAsJsonObject();
        }
    }

    private JsonObject postJson(String url, String json) throws IOException, AuthException {
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String bodyStr = requireSuccessBody(response, url);
            return JsonParser.parseString(bodyStr).getAsJsonObject();
        }
    }

    private String requireSuccessBody(Response response, String url) throws IOException, AuthException {
        ResponseBody body = response.body();
        String bodyStr = body != null ? body.string() : "";
        if (!response.isSuccessful()) {
            throw new AuthException("HTTP " + response.code() + " from " + url + ": " + bodyStr);
        }
        return bodyStr;
    }

    /** Creates a new builder for {@link MicrosoftAuthProvider}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link MicrosoftAuthProvider}. */
    public static final class Builder {

        private OkHttpClient httpClient;
        private String clientId;
        private String redirectUri = "https://login.microsoftonline.com/common/oauth2/nativeclient";
        private String authorizationCode;
        private String refreshToken;

        private Builder() {}

        /** Overrides the default {@link OkHttpClient}. */
        public Builder httpClient(OkHttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /** Sets the Azure application (client) ID. */
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        /** Sets the OAuth redirect URI registered in Azure. */
        public Builder redirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
            return this;
        }

        /** Provides a fresh authorization code to exchange for tokens. */
        public Builder authorizationCode(String authorizationCode) {
            this.authorizationCode = authorizationCode;
            return this;
        }

        /** Provides an existing refresh token to obtain a new session. */
        public Builder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public MicrosoftAuthProvider build() {
            if (clientId == null || clientId.isBlank()) throw new IllegalStateException("clientId must be set");
            if (authorizationCode == null && refreshToken == null) {
                throw new IllegalStateException("Either authorizationCode or refreshToken must be provided");
            }
            return new MicrosoftAuthProvider(this);
        }
    }
}
