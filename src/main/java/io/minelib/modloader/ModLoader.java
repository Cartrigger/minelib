package io.minelib.modloader;

/**
 * The supported Minecraft mod loaders that minelib can install automatically.
 */
public enum ModLoader {

    /**
     * <a href="https://fabricmc.net/">Fabric</a> — a lightweight, modular modding toolchain.
     * Installation is performed entirely through Fabric's public meta API; no separate
     * installer JAR is required.
     */
    FABRIC,

    /**
     * <a href="https://minecraftforge.net/">Forge</a> (MinecraftForge) — the long-standing
     * Minecraft modding platform. Installation downloads the official Forge installer JAR
     * and runs it with {@code --installClient}.
     */
    FORGE,

    /**
     * <a href="https://neoforged.net/">NeoForge</a> — the community fork of Forge started in
     * 2023. Installation downloads the official NeoForge installer JAR and runs it with
     * {@code --installClient}.
     */
    NEOFORGE
}
