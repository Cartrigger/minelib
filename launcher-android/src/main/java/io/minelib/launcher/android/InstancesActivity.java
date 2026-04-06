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
import android.widget.SeekBar;
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
 * Shows the list of saved launcher instances and lets the user create, edit, delete, or
 * launch instances.
 *
 * <h2>Creating an instance</h2>
 * <p>The FAB ({@code +}) opens a dialog for name, Minecraft version, and mod loader.
 *
 * <h2>Instance actions</h2>
 * <p>Each row has a "▶ Play" button (launches the game) and a "⋮" button that opens a
 * menu with "Edit", "Info", and "Delete" options.
 *
 * <h2>Launching an instance</h2>
 * <p>Downloads files if needed, then starts the game in-process via
 * {@link AndroidGameRunner} + MobileGlues. Waits for the bundled JRE extraction to
 * complete before launching.
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
        adapter   = new InstanceAdapter(this, instances, this::launchInstance,
                this::showInstanceMenu);
        listView.setAdapter(adapter);

        refreshEmptyState();

        fab.setOnClickListener(v -> showAddInstanceDialog());

        // Toolbar buttons
        Button btnMods     = findViewById(R.id.btn_mods);
        Button btnSettings = findViewById(R.id.btn_settings);
        btnMods.setOnClickListener(v -> startActivity(new Intent(this, ModsActivity.class)));
        btnSettings.setOnClickListener(v -> showSettingsDialog());
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

    // ── Instance overflow menu ────────────────────────────────────────────────

    /**
     * Shows a popup menu for the given instance with Edit, Info, and Delete options.
     * Called when the user taps the "⋮" button on an instance row.
     */
    private void showInstanceMenu(Instance instance) {
        String[] options = {
                getString(R.string.edit),
                getString(R.string.info),
                getString(R.string.delete)
        };
        new AlertDialog.Builder(this)
                .setTitle(instance.getName())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: showEditInstanceDialog(instance); break;
                        case 1: showInstanceInfoDialog(instance); break;
                        case 2: confirmDeleteInstance(instance);  break;
                    }
                })
                .show();
    }

    // ── Edit instance ─────────────────────────────────────────────────────────

    private void showEditInstanceDialog(Instance instance) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_instance, null);

        EditText etName    = dialogView.findViewById(R.id.et_edit_name);
        EditText etVersion = dialogView.findViewById(R.id.et_edit_version);
        Spinner  spLoader  = dialogView.findViewById(R.id.sp_edit_loader);
        SeekBar  sbMemory  = dialogView.findViewById(R.id.sb_memory);
        TextView tvMemory  = dialogView.findViewById(R.id.tv_memory_value);

        etName.setText(instance.getName());
        etVersion.setText(instance.getMinecraftVersion());

        ArrayAdapter<CharSequence> loaderAdapter = ArrayAdapter.createFromResource(
                this, R.array.mod_loaders, android.R.layout.simple_spinner_item);
        loaderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLoader.setAdapter(loaderAdapter);
        spLoader.setSelection(instance.getModLoader().ordinal());

        // Memory slider: 512 MB – 8192 MB in steps of 512
        int memSteps = (8192 - 512) / 512; // = 15
        sbMemory.setMax(memSteps);
        int currentStep = (instance.getMemoryMb() - 512) / 512;
        sbMemory.setProgress(Math.max(0, Math.min(memSteps, currentStep)));
        tvMemory.setText(instance.getMemoryMb() + " MB");

        sbMemory.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                tvMemory.setText((512 + progress * 512) + " MB");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        new AlertDialog.Builder(this)
                .setTitle(R.string.edit_instance)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String name    = etName.getText().toString().trim();
                    String version = etVersion.getText().toString().trim();
                    if (name.isEmpty() || version.isEmpty()) {
                        Toast.makeText(this, R.string.fields_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    instance.setName(name);
                    instance.setMinecraftVersion(version);
                    instance.setModLoader(Instance.ModLoader.values()[spLoader.getSelectedItemPosition()]);
                    instance.setMemoryMb(512 + sbMemory.getProgress() * 512);
                    Executors.newSingleThreadExecutor().execute(() -> {
                        try {
                            instanceService.saveInstance(instance);
                            runOnUiThread(() -> {
                                adapter.notifyDataSetChanged();
                                Toast.makeText(this, R.string.instance_saved, Toast.LENGTH_SHORT).show();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() ->
                                    Toast.makeText(this,
                                            getString(R.string.save_failed) + ": " + e.getMessage(),
                                            Toast.LENGTH_LONG).show());
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ── Instance info ─────────────────────────────────────────────────────────

    private void showInstanceInfoDialog(Instance instance) {
        String lastPlayed = instance.getLastPlayedMs() > 0
                ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                        java.util.Locale.getDefault())
                        .format(new java.util.Date(instance.getLastPlayedMs()))
                : getString(R.string.never);

        String msg = getString(R.string.info_version)   + ": " + instance.getMinecraftVersion() + "\n"
                   + getString(R.string.info_loader)    + ": " + instance.getModLoader().name() + "\n"
                   + getString(R.string.info_memory)    + ": " + instance.getMemoryMb() + " MB\n"
                   + getString(R.string.info_last_played) + ": " + lastPlayed + "\n"
                   + getString(R.string.info_id)        + ": " + instance.getId();

        new AlertDialog.Builder(this)
                .setTitle(instance.getName())
                .setMessage(msg)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    // ── Delete instance ───────────────────────────────────────────────────────

    private void confirmDeleteInstance(Instance instance) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_instance)
                .setMessage(getString(R.string.delete_confirm, instance.getName()))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        try {
                            instanceService.deleteInstance(instance);
                            runOnUiThread(() -> {
                                instances.remove(instance);
                                adapter.notifyDataSetChanged();
                                refreshEmptyState();
                                Toast.makeText(this, R.string.instance_deleted, Toast.LENGTH_SHORT).show();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() ->
                                    Toast.makeText(this,
                                            getString(R.string.delete_failed) + ": " + e.getMessage(),
                                            Toast.LENGTH_LONG).show());
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ── Settings dialog ───────────────────────────────────────────────────────

    private void showSettingsDialog() {
        PlayerProfile profile = authService.loadSavedProfile();
        String username = (profile != null) ? profile.getUsername() : getString(R.string.not_signed_in);
        String msg = getString(R.string.settings_account) + ": " + username;

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings)
                .setMessage(msg)
                .setNeutralButton(R.string.sign_out, (d, w) -> {
                    authService.signOut();
                    Toast.makeText(this, R.string.signed_out, Toast.LENGTH_SHORT).show();
                    finish(); // Return to login screen
                })
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    // ── Launch ────────────────────────────────────────────────────────────────

    /**
     * Downloads and launches the given {@link Instance} using
     * {@link AndroidGameRunner} + MobileGlues.  Runs entirely on a background thread.
     * Waits for all bundled JRE extractions to complete before starting the game.
     */
    void launchInstance(Instance instance) {
        Toast.makeText(this, R.string.launching, Toast.LENGTH_SHORT).show();

        Path dataDir       = getFilesDir().toPath();
        Path mglInstallDir = dataDir.resolve("mobileglues");

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Block until all bundled JREs have been extracted (fast on subsequent runs)
                LauncherApplication.get(this).getJresReady().get();

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
                        authProvider,
                        instance.getMemoryMb());

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

