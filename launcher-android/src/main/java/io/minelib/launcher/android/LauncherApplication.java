package io.minelib.launcher.android;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import io.minelib.launcher.service.AuthService;
import io.minelib.launcher.service.InstanceService;
import okhttp3.OkHttpClient;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Application subclass that holds shared, long-lived service instances for the
 * Minelib Android launcher.
 *
 * <p>Services are created once on first app start and remain alive for the
 * lifetime of the process. Activities retrieve them via {@link #get(Context)}.
 *
 * <p>The bundled FCL OpenJDK is extracted from APK assets into internal storage
 * on the very first run (subsequent runs skip extraction if already done).
 * The extraction result is exposed via {@link #getJreReady()} so that launch
 * code can wait for it to complete before starting the game.
 */
public final class LauncherApplication extends Application {

    private static final String TAG = "LauncherApplication";

    private OkHttpClient httpClient;
    private AuthService authService;
    private InstanceService instanceService;

    /**
     * Future that completes with the extracted JRE root path once
     * {@link BundledJreExtractor#extractIfNeeded} has finished (or exceptionally if it failed).
     * Use {@link CompletableFuture#get()} in any background thread that needs the JRE.
     */
    private final CompletableFuture<Path> jreReady = new CompletableFuture<>();

    @Override
    public void onCreate() {
        super.onCreate();
        httpClient = new OkHttpClient();
        java.nio.file.Path dataDir = getFilesDir().toPath();
        authService    = new AuthService(dataDir, httpClient);
        instanceService = new InstanceService(dataDir);

        // Extract the bundled FCL OpenJDK from APK assets on the first run.
        // Runs on a background thread; callers block on jreReady.get() before launching.
        final android.content.Context appCtx = getApplicationContext();
        new Thread(() -> {
            try {
                Path jreDir = BundledJreExtractor.extractIfNeeded(appCtx);
                jreReady.complete(jreDir);
            } catch (Exception e) {
                Log.e(TAG, "Failed to extract bundled JRE", e);
                jreReady.completeExceptionally(e);
            }
        }, "jre-extractor").start();
    }

    /**
     * Returns a future that completes with the extracted JRE root directory.
     * Background threads should call {@link CompletableFuture#get()} on this before
     * attempting to launch Minecraft.
     */
    public CompletableFuture<Path> getJreReady() { return jreReady; }

    /** Returns the shared {@link AuthService}. */
    public AuthService getAuthService() { return authService; }

    /** Returns the shared {@link InstanceService}. */
    public InstanceService getInstanceService() { return instanceService; }

    /** Returns the shared {@link OkHttpClient}. */
    public OkHttpClient getHttpClient() { return httpClient; }

    /**
     * Convenience cast to retrieve the {@link LauncherApplication} from any
     * {@link Context}.
     */
    public static LauncherApplication get(Context ctx) {
        return (LauncherApplication) ctx.getApplicationContext();
    }
}

