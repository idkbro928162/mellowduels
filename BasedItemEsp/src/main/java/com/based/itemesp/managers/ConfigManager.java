package com.based.itemesp.managers;

import com.based.itemesp.BasedItemEsp;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Loads and exposes config.yml values. All user-facing strings go through
 * {@link #colorize(String)} / {@link ChatColor#translateAlternateColorCodes(char, String)}.
 */
public final class ConfigManager {

    private final BasedItemEsp plugin;

    private boolean enabled;
    /** Horizontal max distance; {@code < 0} means unlimited. */
    private double maxDistance;
    private int recheckTicks;
    private boolean hideCompletely;
    private boolean hideStackerHolograms;
    private double hologramItemRadius;
    private boolean debug;

    private String prefix;
    private String msgEnabled;
    private String msgDisabled;
    private String msgNoProtocolLib;

    public ConfigManager(BasedItemEsp plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        enabled = config.getBoolean("settings.enabled", true);
        maxDistance = config.getDouble("settings.max-distance", -1.0D);
        recheckTicks = Math.max(1, config.getInt("settings.recheck-ticks", 5));
        hideCompletely = config.getBoolean("settings.hide-completely", true);
        hideStackerHolograms = config.getBoolean("settings.hide-stacker-holograms", true);
        hologramItemRadius = Math.max(0.5D, config.getDouble("settings.hologram-item-radius", 2.0D));
        debug = config.getBoolean("settings.debug", false);

        prefix = config.getString("messages.prefix", "&8[&6BasedItemEsp&8]&r ");
        msgEnabled = config.getString("messages.enabled", "&aAnti Item-ESP is enabled.");
        msgDisabled = config.getString("messages.disabled", "&cAnti Item-ESP is disabled.");
        msgNoProtocolLib = config.getString("messages.no-protocollib",
                "&cProtocolLib is required. Plugin disabled.");
    }

    public String colorize(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public String getPrefix() {
        return colorize(prefix);
    }

    public String getMessage(String key) {
        return switch (key) {
            case "enabled" -> getPrefix() + colorize(msgEnabled);
            case "disabled" -> getPrefix() + colorize(msgDisabled);
            case "no-protocollib" -> getPrefix() + colorize(msgNoProtocolLib);
            default -> getPrefix() + colorize(key);
        };
    }

    /** Raw (uncolored) message body for logging without double-prefix issues. */
    public String getRawMessage(String key) {
        return switch (key) {
            case "enabled" -> msgEnabled;
            case "disabled" -> msgDisabled;
            case "no-protocollib" -> msgNoProtocolLib;
            default -> key;
        };
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Horizontal scan/LOS cap. Values {@code < 0} mean unlimited.
     */
    public double getMaxDistance() {
        return maxDistance;
    }

    public boolean isUnlimitedDistance() {
        return maxDistance < 0.0D;
    }

    /**
     * Effective horizontal radius for entity scans (unlimited uses a large loaded-chunk window).
     */
    public double getHorizontalScanRadius() {
        if (isUnlimitedDistance()) {
            // Large enough to cover typical client entity render + simulation distance.
            return 512.0D;
        }
        return maxDistance;
    }

    public int getRecheckTicks() {
        return recheckTicks;
    }

    public boolean isHideCompletely() {
        return hideCompletely;
    }

    public boolean isHideStackerHolograms() {
        return hideStackerHolograms;
    }

    public double getHologramItemRadius() {
        return hologramItemRadius;
    }

    public boolean isDebug() {
        return debug;
    }

    public void debug(String message) {
        if (debug) {
            plugin.getLogger().info("[Debug] " + message);
        }
    }
}
