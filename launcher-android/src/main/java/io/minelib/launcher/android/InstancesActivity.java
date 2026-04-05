package io.minelib.launcher.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import io.minelib.MineLib;
import io.minelib.android.AndroidGameRunner;
import io.minelib.android.MobileGluesConfig;
import io.minelib.android.MobileGluesDriver;
import io.minelib.auth.AuthProvider;
import io.minelib.auth.PlayerProfile;
import io.minelib.download.DownloadManager;
import io.minelib.launcher.model.Instance;
import io.minelib.launcher.service.AuthService;
import io.minelib.launcher.service.InstanceService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Shows the list of saved launcher instances and lets the user create new ones or
 * launch an existing instance.
 *
 * <h2>Creating an instance</h2>
 * <p>The FAB ({@code +}) opens a simple dialog for name, Minecraft version, and mod
 * loader.  The instance is created on a background thread and added to the list on
 * success.
 *
 * <h2>Launching an instance</h2>
 * <p>Each list row has a "Play" button. Tapping it downloads the required Minecraft
 * files (if needed) and then starts the game in-process via
 * {@link AndroidGameRunner} + MobileGlues.
 */
public final class InstancesActivity extends Activity {

    private InstanceService instanceService;
    private AuthService authService;
    private InstanceAdapter adapter;
    private List<Instance> instances;
    private TextView tvEmpty;
    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_instances);

        LauncherApplication app = LauncherApplication.get(this);
        instanceService = app.getInstanceService();
        authService     = app.getAuthService();

        listView = findViewById(R.id.list_instances);
        tvEmpty  = findViewById(R.id.tv_empty);
        FloatingActionButton fab = findViewById(R.id.fab_add);

        instances = new ArrayList<>(instanceService.loadInstances());
        adapter   = new InstanceAdapter(this, instances, this::launchInstance);
        listView.setAdapter(adapter);

        refreshEmptyState();

        fab.setOnClickListener(v -> showAddInstanceDialog());

        // Navigate to the Modrinth mod browser
        Button btnMods = findViewById(R.id.btn_mods);
        btnMods.setOnClickListener(v -> startActivity(new Intent(this, ModsActivity.class)));
    }

    // ── Add instance dialog ───────────────────────────────────────────────────

    private void showAddInstanceDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_instance, null);
        EditText etName    = dialogView.findViewById(R.id.et_name);
        EditText etVersion = dialogView.findViewById(R.id.et_version);
        Spinner  spLoader  = dialogView.findViewById(R.id.sp_loader);

        ArrayAdapter<CharSequence> loaderAdapter = ArrayAdapter.createFromResource(
                this, R.array.mod_loaders, android.R.layout.simple_spinner_item);
        loaderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLoader.setAdapter(loaderAdapter);

        new AlertDialog.Builder(this)
                .setTitle(R.string.new_instance)
                .setView(dialogView)
                .setPositiveButton(R.string.create, (d, w) -> {
                    String name    = etName.getText().toString().trim();
                    String version = etVersion.getText().toString().trim();
                    if (name.isEmpty() || version.isEmpty()) {
                        Toast.makeText(this, R.string.fields_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Instance.ModLoader loader =
                            Instance.ModLoader.values()[spLoader.getSelectedItemPosition()];
                    createInstance(name, version, loader);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void createInstance(String name, String version, Instance.ModLoader loader) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Instance inst = instanceService.createInstance(name, version, loader);
                runOnUiThread(() -> {
                    instances.add(0, inst);
                    adapter.notifyDataSetChanged();
                    refreshEmptyState();
                });
            } catch (IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(this,
                                getString(R.string.create_failed) + ": " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        });
    }

    // ── Launch ────────────────────────────────────────────────────────────────

    /**
     * Downloads and launches the given {@link Instance} using
     * {@link AndroidGameRunner} + MobileGlues.  Runs entirely on a background thread.
     */
    void launchInstance(Instance instance) {
        Toast.makeText(this, R.string.launching, Toast.LENGTH_SHORT).show();

        Path dataDir       = getFilesDir().toPath();
        Path mglInstallDir = dataDir.resolve("mobileglues");

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                DownloadManager dm = new DownloadManager(4);
                MobileGluesConfig mglConfig = MobileGluesConfig.builder()
                        .installDirectory(mglInstallDir)
                        .build();
                MobileGluesDriver driver = new MobileGluesDriver(mglConfig, dm);

                Path gameDir = instanceService.getGameDirectory(instance);
                MineLib minelib = MineLib.builder()
                        .gameDirectory(gameDir)
                        .gameRunner(new AndroidGameRunner(driver))
                        .maxConcurrentDownloads(4)
                        .build();

                instance.markPlayed();
                instanceService.saveInstance(instance);

                final PlayerProfile profile = authService.getProfile();
                final AuthProvider authProvider = new AuthProvider() {
                    @Override public PlayerProfile authenticate() { return profile; }
                    @Override public PlayerProfile refresh(PlayerProfile p) { return profile; }
                    @Override public boolean validate(PlayerProfile p) { return true; }
                };
                minelib.installAndLaunch(
                        instance.getMinecraftVersion(),
                        authProvider);

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this,
                                getString(R.string.launch_failed) + ": " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void refreshEmptyState() {
        boolean empty = instances.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE   : View.VISIBLE);
    }
}
