package io.minelib.launcher.android;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import io.minelib.launcher.service.AuthService;
import io.minelib.launcher.service.InstanceService;
import okhttp3.OkHttpClient;

/**
 * Application subclass that holds shared, long-lived service instances for the
 * Minelib Android launcher.
 *
 * <p>Services are created once on first app start and remain alive for the
 * lifetime of the process. Activities retrieve them via {@link #get(Context)}.
 *
 * <p>The bundled FCL OpenJDK is extracted from APK assets into internal storage
 * on the very first run (subsequent runs skip extraction if already done).
 */
public final class LauncherApplication extends Application {

    private static final String TAG = "LauncherApplication";

    private OkHttpClient httpClient;
    private AuthService authService;
    private InstanceService instanceService;

    @Override
    public void onCreate() {
        super.onCreate();
        httpClient = new OkHttpClient();
        java.nio.file.Path dataDir = getFilesDir().toPath();
        authService    = new AuthService(dataDir, httpClient);
        instanceService = new InstanceService(dataDir);

        // Extract the bundled FCL OpenJDK from APK assets on the first run.
        // This is fast after the first launch (extraction is skipped when already done).
        // We run it on a background thread to avoid blocking the main thread during startup.
        new Thread(() -> {
            try {
                BundledJreExtractor.extractIfNeeded(this);
            } catch (Exception e) {
                Log.e(TAG, "Failed to extract bundled JRE — game launch will fail if JRE "
                        + "is not yet available", e);
            }
        }, "jre-extractor").start();
    }

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

