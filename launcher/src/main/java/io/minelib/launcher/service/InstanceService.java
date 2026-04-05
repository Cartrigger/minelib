package io.minelib.launcher.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.minelib.MineLib;
import io.minelib.auth.PlayerProfile;
import io.minelib.launcher.model.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Manages the lifecycle of launcher instances: creation, persistence, listing,
 * deletion, and launching.
 *
 * <h2>Persistence layout</h2>
 * <pre>
 * &lt;launcherDir&gt;/
 *   instances/
 *     &lt;id&gt;/
 *       instance.json   – metadata (name, version, modLoader, lastPlayed)
 *       .minecraft/     – isolated game directory for this instance
 * </pre>
 */
public final class InstanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceService.class);

    private final Path instancesDir;
    private final List<Instance> instances = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Creates an {@code InstanceService} backed by {@code <launcherDir>/instances/}.
     *
     * @param launcherDir the launcher data directory
     */
    public InstanceService(Path launcherDir) {
        this.instancesDir = launcherDir.resolve("instances");
    }

    /**
     * Loads all persisted instances from disk.
     *
     * @return unmodifiable list of all instances
     */
    public List<Instance> loadInstances() {
        instances.clear();
        if (!Files.isDirectory(instancesDir)) return Collections.unmodifiableList(instances);
        try (var dirs = Files.list(instancesDir)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                Path meta = dir.resolve("instance.json");
                if (Files.exists(meta)) {
                    try {
                        Instance inst = gson.fromJson(Files.readString(meta), Instance.class);
                        if (inst != null && inst.getId() != null) {
                            instances.add(inst);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Could not read instance metadata at {}", meta, e);
                    }
                }
            });
        } catch (IOException e) {
            LOGGER.warn("Could not list instances", e);
        }
        instances.sort((a, b) -> Long.compare(b.getLastPlayedMs(), a.getLastPlayedMs()));
        return Collections.unmodifiableList(instances);
    }

    /**
     * Creates a new instance with the given settings and persists it to disk.
     *
     * @param name             display name
     * @param minecraftVersion Minecraft version identifier (e.g. {@code "1.21.4"})
     * @param modLoader        the desired mod loader
     * @return the newly created instance
     * @throws IOException if the instance directory or metadata file cannot be written
     */
    public Instance createInstance(String name, String minecraftVersion,
                                   Instance.ModLoader modLoader) throws IOException {
        String id = UUID.randomUUID().toString();
        Instance inst = new Instance(id, name, minecraftVersion, modLoader);
        Path instanceDir = instancesDir.resolve(id);
        Files.createDirectories(instanceDir.resolve(".minecraft"));
        saveInstance(inst);
        instances.add(0, inst);
        LOGGER.info("Created instance: {}", inst);
        return inst;
    }

    /**
     * Deletes an instance and all its game data.
     *
     * @param instance the instance to delete
     * @throws IOException if the directory cannot be deleted
     */
    public void deleteInstance(Instance instance) throws IOException {
        Path instanceDir = instancesDir.resolve(instance.getId());
        deleteDirectory(instanceDir);
        instances.remove(instance);
        LOGGER.info("Deleted instance: {}", instance.getId());
    }

    /**
     * Saves (persists) the current state of an instance to its {@code instance.json} file.
     * Call this after modifying an instance's name, version, or loader.
     *
     * @param instance the instance to save
     * @throws IOException if the file cannot be written
     */
    public void saveInstance(Instance instance) throws IOException {
        Path metaFile = instancesDir.resolve(instance.getId()).resolve("instance.json");
        Files.createDirectories(metaFile.getParent());
        Files.writeString(metaFile, gson.toJson(instance));
    }

    /**
     * Returns the isolated {@code .minecraft} directory for the given instance.
     * This is the directory passed to {@link MineLib} when launching.
     *
     * @param instance the instance
     * @return path to the instance's game directory
     */
    public Path getGameDirectory(Instance instance) {
        return instancesDir.resolve(instance.getId()).resolve(".minecraft");
    }

    /**
     * Launches the given Minecraft instance using {@link MineLib#installAndLaunch}.
     *
     * @param instance      the instance to launch
     * @param profile       the authenticated player profile
     * @throws Exception    if installation or launch fails
     */
    public io.minelib.launch.GameProcess launch(Instance instance, PlayerProfile profile)
            throws Exception {
        Path gameDir = getGameDirectory(instance);
        MineLib minelib = MineLib.builder()
                .gameDirectory(gameDir)
                .maxConcurrentDownloads(6)
                .build();

        LOGGER.info("Launching instance '{}' ({})", instance.getName(),
                instance.getMinecraftVersion());
        instance.markPlayed();
        saveInstance(instance);

        return minelib.installAndLaunch(instance.getMinecraftVersion(),
                new io.minelib.auth.AuthProvider() {
                    @Override
                    public PlayerProfile authenticate() { return profile; }
                    @Override
                    public PlayerProfile refresh(PlayerProfile p) { return profile; }
                    @Override
                    public boolean validate(PlayerProfile p) { return true; }
                });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        }
    }
}
