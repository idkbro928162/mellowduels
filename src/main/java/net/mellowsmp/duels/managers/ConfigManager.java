package net.mellowsmp.duels.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.mellowsmp.duels.MellowDuels;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

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
        return color(msg.replace("%prefix%", prefix));
    }

    public String messageRaw(String key) {
        return color(raw().getString("messages." + key, "&c[missing message: " + key + "]"));
    }

    public String color(String s) {
        if (s == null) return "";
        // Convert &-codes into §-codes for Bukkit string messages
        return LegacyComponentSerializer.legacySection().serialize(LEGACY.deserialize(s));
    }

    /** Converts an &- or §-colored string into an Adventure component for titles/GUI. */
    public Component component(String s) {
        if (s == null || s.isEmpty()) {
            return Component.empty();
        }
        if (s.indexOf('§') >= 0) {
            return LegacyComponentSerializer.legacySection().deserialize(s);
        }
        return LEGACY.deserialize(s);
    }

    public int countdownSeconds() {
        return Math.max(0, raw().getInt("countdown.seconds", 5));
    }

    public String arenaWorldName() {
        return raw().getString("arenas.world", "duels_world");
    }

    public int arenaSpacing() {
        return Math.max(1, raw().getInt("arenas.spacing", 250));
    }

    public int minSpareCopies() {
        return Math.max(0, raw().getInt("arenas.min-spare-copies", 2));
    }

    public int maxDuelDurationSeconds() {
        return raw().getInt("duel.max-duration-seconds", 600);
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

    public boolean matchSameKitOnly() {
        return raw().getBoolean("queue.match-same-kit-only", true);
    }

    public String storageType() {
        return raw().getString("storage.type", "SQLITE");
    }
}
