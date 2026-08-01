package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.MellowDuels;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final MellowDuels plugin;

    public ConfigManager(MellowDuels plugin) {
        this.plugin = plugin;
    }

    public FileConfiguration raw() {
        return plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public String message(String key) {
        String prefix = raw().getString("messages.prefix", "");
        String msg = raw().getString("messages." + key, "&c[missing message: " + key + "]");
        return color(prefix + msg);
    }

    public String messageRaw(String key) {
        return color(raw().getString("messages." + key, "&c[missing message: " + key + "]"));
    }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public int countdownSeconds() {
        return raw().getInt("countdown.seconds", 5);
    }

    public String arenaWorldName() {
        return raw().getString("arenas.world", "duels_world");
    }

    public int arenaSpacing() {
        return raw().getInt("arenas.spacing", 250);
    }

    public int minSpareCopies() {
        return raw().getInt("arenas.min-spare-copies", 2);
    }

    public int maxDuelDurationSeconds() {
        return raw().getInt("duel.max-duration-seconds", 600);
    }

    public boolean forfeitOnQuit() {
        return raw().getBoolean("duel.forfeit-on-quit", true);
    }

    public int forfeitGraceSeconds() {
        return raw().getInt("duel.forfeit-on-disconnect-grace-seconds", 15);
    }

    public boolean spectatorEnabled() {
        return raw().getBoolean("spectator.enabled", true);
    }

    public boolean hideSpectatorsFromParticipants() {
        return raw().getBoolean("spectator.hide-spectators-from-participants", false);
    }

    public boolean matchSameKitOnly() {
        return raw().getBoolean("queue.match-same-kit-only", true);
    }

    public String storageType() {
        return raw().getString("storage.type", "SQLITE");
    }
}
