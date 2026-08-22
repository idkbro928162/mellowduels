package com.based.itemesp;

import com.based.itemesp.listeners.ItemPacketListener;
import com.based.itemesp.listeners.QuitListener;
import com.based.itemesp.managers.ConfigManager;
import com.based.itemesp.managers.VisibilityManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * BasedItemEsp — hides dropped items that lack line-of-sight to block Item ESP.
 */
public final class BasedItemEsp extends JavaPlugin {

    private ConfigManager configManager;
    private VisibilityManager visibilityManager;
    private ItemPacketListener itemPacketListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.configManager.reload();

        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe(configManager.colorize(
                    configManager.getRawMessage("no-protocollib")
                            .replace(configManager.getPrefix(), "")));
            getLogger().severe("ProtocolLib is missing — disabling BasedItemEsp.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.visibilityManager = new VisibilityManager(this, configManager);
        this.visibilityManager.start();

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new QuitListener(visibilityManager), this);

        this.itemPacketListener = new ItemPacketListener(this, configManager, visibilityManager);
        this.itemPacketListener.register();

        getLogger().info("BasedItemEsp enabled.");
        if (configManager.isDebug()) {
            getLogger().info(configManager.colorize(configManager.getMessage("enabled")));
        }
    }

    @Override
    public void onDisable() {
        if (itemPacketListener != null) {
            itemPacketListener.unregister();
            itemPacketListener = null;
        }
        if (visibilityManager != null) {
            visibilityManager.shutdown();
            visibilityManager = null;
        }
        getLogger().info("BasedItemEsp disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public VisibilityManager getVisibilityManager() {
        return visibilityManager;
    }
}
