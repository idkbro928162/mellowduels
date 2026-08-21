package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.BasedDuels;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacySection();

    private final BasedDuels plugin;
    private volatile boolean spectatorChatAllowed;

    public ConfigManager(BasedDuels plugin) {
        this.plugin = plugin;
        refreshCachedValues();
    }

    public FileConfiguration raw() {
        return plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        refreshCachedValues();
    }

    public String message(String key) {
        String prefix = raw().getString("messages.prefix", "");
        String msg = raw().getString("messages." + key, "&c[missing message: " + key + "]");
        return color(msg.contains("%prefix%") ? msg.replace("%prefix%", prefix) : prefix + msg);
    }

    public String messageRaw(String key) {
        return color(raw().getString("messages." + key, "&c[missing message: " + key + "]"));
    }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public Component component(String legacyText) {
        return LEGACY_SERIALIZER.deserialize(color(legacyText));
    }

    public int countdownSeconds() {
        return Math.max(0, raw().getInt("countdown.seconds", 5));
    }

    public String arenaWorldName() {
        return raw().getString("arenas.world", "duels_world");
    }

    public int arenaSpacing() {
        return Math.max(32, raw().getInt("arenas.spacing", 250));
    }

    public int minSpareCopies() {
        return Math.max(0, raw().getInt("arenas.min-spare-copies", 2));
    }

    public int maxDuelDurationSeconds() {
        return Math.max(0, raw().getInt("duel.max-duration-seconds", 600));
    }

    public boolean forfeitOnQuit() {
        return raw().getBoolean("duel.forfeit-on-quit", true);
    }

    public int forfeitGraceSeconds() {
        return Math.max(0, raw().getInt("duel.forfeit-on-disconnect-grace-seconds", 15));
    }

    public boolean disableHunger() {
        return raw().getBoolean("duel.disable-hunger", false);
    }

    public boolean disableNaturalRegen() {
        return raw().getBoolean("duel.disable-natural-regen", false);
    }

    public boolean spectatorEnabled() {
        return raw().getBoolean("spectator.enabled", true);
    }

    public boolean hideSpectatorsFromParticipants() {
        return raw().getBoolean("spectator.hide-spectators-from-participants", false);
    }

    public boolean allowSpectatorChat() {
        return spectatorChatAllowed;
    }

    public boolean preventDuplicateRequests() {
        return raw().getBoolean("queue.prevent-duplicate-requests", true);
    }

    public String storageType() {
        String type = raw().getString("storage.type", "SQLITE");
        return type == null ? "SQLITE" : type.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private void refreshCachedValues() {
        spectatorChatAllowed = raw().getBoolean("spectator.allow-spectator-chat", true);
    }
}
