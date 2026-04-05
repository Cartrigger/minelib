package io.minelib.launcher.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import io.minelib.launcher.service.AuthService;

/**
 * Handles authentication — either via the Microsoft device-code OAuth flow or
 * offline (username-only) mode.
 *
 * <h2>Microsoft flow</h2>
 * <ol>
 *   <li>"Sign in with Microsoft" button → {@link AuthService#signIn} starts a
 *       {@link java.util.concurrent.CompletableFuture} on a background thread.</li>
 *   <li>The device-code message (URL + short code) is posted to the UI thread via
 *       {@link #runOnUiThread}.</li>
 *   <li>On success the user is taken to {@link InstancesActivity}.</li>
 * </ol>
 *
 * <h2>Offline mode</h2>
 * <p>The user enters a username; an offline {@link io.minelib.auth.PlayerProfile} is
 * created immediately without any network request.
 */
public final class SignInActivity extends Activity {

    private AuthService authService;
    private TextView tvCodeMessage;
    private Button btnSignIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        authService  = LauncherApplication.get(this).getAuthService();
        tvCodeMessage = findViewById(R.id.tv_code_message);
        btnSignIn    = findViewById(R.id.btn_sign_in);
        EditText etUsername = findViewById(R.id.et_username);
        Button btnOffline   = findViewById(R.id.btn_offline);

        btnSignIn.setOnClickListener(v -> startMicrosoftSignIn());

        btnOffline.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            if (username.isEmpty()) {
                etUsername.setError(getString(R.string.username_required));
                return;
            }
            authService.createOfflineProfile(username);
            goToInstances();
        });
    }

    private void startMicrosoftSignIn() {
        btnSignIn.setEnabled(false);
        tvCodeMessage.setText(R.string.waiting_for_code);
        tvCodeMessage.setVisibility(View.VISIBLE);

        authService.signIn(message -> runOnUiThread(() -> tvCodeMessage.setText(message)))
                .thenAccept(profile -> runOnUiThread(this::goToInstances))
                .exceptionally(ex -> {
                    runOnUiThread(() -> {
                        btnSignIn.setEnabled(true);
                        tvCodeMessage.setVisibility(View.GONE);
                        Toast.makeText(this,
                                getString(R.string.sign_in_failed) + ": " + ex.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
                    return null;
                });
    }

    private void goToInstances() {
        startActivity(new Intent(this, InstancesActivity.class));
        finish();
    }
}
