package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.util.Texts;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed accessors for config.yml. Message lookup replaces {@code %prefix%}
 * inside the template instead of concatenating a second copy of the prefix.
 */
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

    public String message(String key, String... replacements) {
        String prefix = raw().getString("messages.prefix", "");
        String template = raw().getString("messages." + key);
        if (template == null) {
            template = "&c[missing message: " + key + "]";
        }
        String msg;
        if (template.contains("%prefix%")) {
            msg = template.replace("%prefix%", prefix);
        } else {
            msg = prefix + template;
        }
        if (replacements != null) {
            for (int i = 0; i + 1 < replacements.length; i += 2) {
                if (replacements[i] != null && replacements[i + 1] != null) {
                    msg = msg.replace(replacements[i], replacements[i + 1]);
                }
            }
        }
        return Texts.legacy(msg);
    }

    public String color(String s) {
        return Texts.legacy(s);
    }

    public int countdownSeconds() {
        return Math.max(0, raw().getInt("countdown.seconds", 5));
    }

    public String countdownTitle() {
        return raw().getString("countdown.title", "&e&lDUEL STARTING");
    }

    public String countdownSubtitle() {
        return raw().getString("countdown.subtitle", "&f%seconds%");
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

    public int builtinArenaSize() {
        return Math.max(16, raw().getInt("arenas.builtin-size", 25));
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
        return raw().getBoolean("spectator.hide-spectators-from-participants", true);
    }

    public boolean allowSpectatorChat() {
        return raw().getBoolean("spectator.allow-spectator-chat", true);
    }

    public boolean isolateDuelChat() {
        return raw().getBoolean("spectator.isolate-duel-chat", true);
    }

    public boolean matchSameKitOnly() {
        return raw().getBoolean("queue.match-same-kit-only", true);
    }

    public boolean preventDuplicateRequests() {
        return raw().getBoolean("queue.prevent-duplicate-requests", true);
    }

    public String storageType() {
        return raw().getString("storage.type", "SQLITE");
    }

    public boolean isMysql() {
        return "MYSQL".equalsIgnoreCase(storageType());
    }
}
