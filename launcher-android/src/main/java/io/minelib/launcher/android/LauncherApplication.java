package io.minelib.launcher.android;

import android.app.Application;
import android.content.Context;
import io.minelib.launcher.service.AuthService;
import io.minelib.launcher.service.InstanceService;
import okhttp3.OkHttpClient;

/**
 * Application subclass that holds shared, long-lived service instances for the
 * Minelib Android launcher.
 *
 * <p>Services are created once on first app start and remain alive for the
 * lifetime of the process. Activities retrieve them via {@link #get(Context)}.
 */
public final class LauncherApplication extends Application {

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
