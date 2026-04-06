package io.minelib.launcher.android;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import io.minelib.launcher.service.AuthService;
import io.minelib.launcher.service.InstanceService;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Application subclass that holds shared, long-lived service instances for the
 * Minelib Android launcher.
 *
 * <p>Services are created once on first app start and remain alive for the
 * lifetime of the process. Activities retrieve them via {@link #get(Context)}.
 *
 * <p>All bundled FCL OpenJDK versions (8, 17, 25) are extracted from APK assets into
 * internal storage on the very first run (subsequent runs skip extraction if already done).
 * {@link #getJresReady()} returns a {@link CompletableFuture} that completes once every
 * bundled JRE has been extracted (or failed); callers block on it before launching Minecraft.
 */
public final class LauncherApplication extends Application {

    private static final String TAG = "LauncherApplication";

    private OkHttpClient httpClient;
    private AuthService authService;
    private InstanceService instanceService;

    /**
     * Future that completes (with {@code null}) when all bundled JRE versions have been
     * extracted from APK assets, or exceptionally if a required JRE version could not be
     * extracted.  Background launch threads should call {@link CompletableFuture#get()} on
     * this before invoking {@code MineLib.installAndLaunch(…)}.
     */
    private final CompletableFuture<Void> jresReady = new CompletableFuture<>();

    @Override
    public void onCreate() {
        super.onCreate();
        httpClient = new OkHttpClient();
        Path dataDir = getFilesDir().toPath();
        authService     = new AuthService(dataDir, httpClient);
        instanceService = new InstanceService(dataDir);

        // Extract all bundled FCL JRE versions in a background thread.
        // jresReady completes once every extraction is done so launch code can wait safely.
        final android.content.Context appCtx = getApplicationContext();
        new Thread(() -> {
            IOException firstFailure = null;
            for (int version : BundledJreExtractor.BUNDLED_JRE_VERSIONS) {
                try {
                    BundledJreExtractor.extractIfNeeded(appCtx, version);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to extract bundled JRE " + version, e);
                    if (firstFailure == null) {
                        firstFailure = (e instanceof IOException)
                                ? (IOException) e
                                : new java.io.IOException("JRE " + version + " extraction failed", e);
                    }
                }
            }
            if (firstFailure != null) {
                jresReady.completeExceptionally(firstFailure);
            } else {
                jresReady.complete(null);
            }
        }, "jre-extractor").start();
    }

    /**
     * Returns a future that completes when all bundled JRE versions are extracted and ready.
     * Background threads should call {@link CompletableFuture#get()} on this before
     * attempting to launch Minecraft.
     */
    public CompletableFuture<Void> getJresReady() { return jresReady; }

    /** Returns the shared {@link AuthService}. */
    public AuthService getAuthService() { return authService; }

    /** Returns the shared {@link InstanceService}. */
    public InstanceService getInstanceService() { return instanceService; }

    /** Returns the shared {@link OkHttpClient}. */
    public OkHttpClient getHttpClient() { return httpClient; }

    /**
     * Convenience cast to retrieve the {@link LauncherApplication} from any {@link Context}.
     */
    public static LauncherApplication get(Context ctx) {
        return (LauncherApplication) ctx.getApplicationContext();
    }
}


