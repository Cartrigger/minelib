package io.minelib.launcher.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Splash / routing activity. Checks whether a saved auth profile exists and
 * sends the user to the appropriate screen immediately.
 *
 * <p>This activity has no visible layout — it finishes itself after redirecting,
 * so it is never visible for more than a few milliseconds.
 */
public final class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean isSignedIn =
                LauncherApplication.get(this).getAuthService().loadSavedProfile() != null;

        Intent next = isSignedIn
                ? new Intent(this, InstancesActivity.class)
                : new Intent(this, SignInActivity.class);

        startActivity(next);
        finish();
    }
}
