package net.mellowsmp.duels;

import net.mellowsmp.duels.commands.DuelAdminCommand;
import net.mellowsmp.duels.commands.DuelCommand;
import net.mellowsmp.duels.listeners.DuelCombatListener;
import net.mellowsmp.duels.listeners.DuelRestrictionListener;
import net.mellowsmp.duels.listeners.KitGuiListener;
import net.mellowsmp.duels.listeners.PlayerConnectionListener;
import net.mellowsmp.duels.managers.ArenaManager;
import net.mellowsmp.duels.managers.ConfigManager;
import net.mellowsmp.duels.managers.DuelManager;
import net.mellowsmp.duels.managers.KitManager;
import net.mellowsmp.duels.managers.PlayerStateManager;
import net.mellowsmp.duels.managers.QueueManager;
import net.mellowsmp.duels.managers.RequestManager;
import net.mellowsmp.duels.managers.SpectatorManager;
import net.mellowsmp.duels.managers.StatsManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MellowDuels - an automatic-arena PvP dueling system.
 *
 * This is an original implementation built for MellowSMP. It is not based on,
 * and does not reuse any code from, any other dueling plugin.
 */
public final class MellowDuels extends JavaPlugin {

    private static MellowDuels instance;

    private ConfigManager configManager;
    private ArenaManager arenaManager;
    private KitManager kitManager;
    private PlayerStateManager playerStateManager;
    private StatsManager statsManager;
    private QueueManager queueManager;
    private RequestManager requestManager;
    private SpectatorManager spectatorManager;
    private DuelManager duelManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResourceIfMissing("kits.yml");

        this.configManager = new ConfigManager(this);
        this.playerStateManager = new PlayerStateManager();
        this.kitManager = new KitManager(this, configManager);
        this.statsManager = new StatsManager(this, configManager);
        this.arenaManager = new ArenaManager(this, configManager);
        this.spectatorManager = new SpectatorManager(this);
        this.duelManager = new DuelManager(this, arenaManager, kitManager, playerStateManager,
                statsManager, spectatorManager, configManager);
        this.queueManager = new QueueManager(this, duelManager, kitManager, configManager);
        this.requestManager = new RequestManager(this, duelManager, kitManager, configManager);

        DuelCommand duelCommand = new DuelCommand(this);
        DuelAdminCommand adminCommand = new DuelAdminCommand(this);
        PluginCommand duel = getCommand("duel");
        PluginCommand dueladmin = getCommand("dueladmin");
        if (duel != null) {
            duel.setExecutor(duelCommand);
            duel.setTabCompleter(duelCommand);
        } else {
            getLogger().severe("Command 'duel' is missing from plugin.yml");
        }
        if (dueladmin != null) {
            dueladmin.setExecutor(adminCommand);
            dueladmin.setTabCompleter(adminCommand);
        } else {
            getLogger().severe("Command 'dueladmin' is missing from plugin.yml");
        }

        getServer().getPluginManager().registerEvents(new DuelCombatListener(this), this);
        getServer().getPluginManager().registerEvents(new DuelRestrictionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new KitGuiListener(this, duelCommand), this);

        kitManager.loadKits();
        arenaManager.loadArenas();
        statsManager.init();

        getLogger().info("MellowDuels enabled. " + arenaManager.getArenaCount() + " arena(s) loaded, "
                + kitManager.getKitNames().size() + " kit(s) loaded.");
    }

    @Override
    public void onDisable() {
        if (spectatorManager != null) {
            spectatorManager.stopAll();
        }
        if (queueManager != null) {
            queueManager.clearAll();
        }
        if (duelManager != null) {
            duelManager.endAllDuelsForShutdown();
        }
        if (playerStateManager != null) {
            playerStateManager.restoreAllOnline();
        }
        if (statsManager != null) {
            statsManager.close();
        }
        instance = null;
    }

    private void saveResourceIfMissing(String name) {
        java.io.File f = new java.io.File(getDataFolder(), name);
        if (!f.exists()) {
            saveResource(name, false);
        }
    }

    public static MellowDuels getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public PlayerStateManager getPlayerStateManager() {
        return playerStateManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public QueueManager getQueueManager() {
        return queueManager;
    }

    public RequestManager getRequestManager() {
        return requestManager;
    }

    public SpectatorManager getSpectatorManager() {
        return spectatorManager;
    }

    public DuelManager getDuelManager() {
        return duelManager;
    }
}
