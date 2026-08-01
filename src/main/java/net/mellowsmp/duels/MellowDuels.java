package net.mellowsmp.duels;

import net.mellowsmp.duels.commands.DuelAdminCommand;
import net.mellowsmp.duels.commands.DuelCommand;
import net.mellowsmp.duels.listeners.DuelCombatListener;
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
        saveResource_ifMissing("kits.yml");

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

        getCommand("duel").setExecutor(new DuelCommand(this));
        getCommand("dueladmin").setExecutor(new DuelAdminCommand(this));

        getServer().getPluginManager().registerEvents(new DuelCombatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        arenaManager.loadArenas();
        kitManager.loadKits();
        statsManager.init();

        getLogger().info("MellowDuels enabled. " + arenaManager.getArenaCount() + " arena(s) loaded, "
                + kitManager.getKitNames().size() + " kit(s) loaded.");
    }

    @Override
    public void onDisable() {
        if (duelManager != null) {
            duelManager.endAllDuelsForShutdown();
        }
        if (statsManager != null) {
            statsManager.close();
        }
    }

    private void saveResource_ifMissing(String name) {
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
