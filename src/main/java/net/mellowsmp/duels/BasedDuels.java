package net.mellowsmp.duels;

import net.mellowsmp.duels.commands.DuelAdminCommand;
import net.mellowsmp.duels.commands.DuelCommand;
import net.mellowsmp.duels.listeners.DuelCombatListener;
import net.mellowsmp.duels.listeners.KitSelectListener;
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

import java.util.Objects;

/**
 * BasedDuels - an automatic-arena PvP dueling system.
 */
public final class BasedDuels extends JavaPlugin {

    private static BasedDuels instance;

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
        var duelPluginCommand = Objects.requireNonNull(getCommand("duel"), "duel command missing from plugin.yml");
        duelPluginCommand.setExecutor(duelCommand);
        duelPluginCommand.setTabCompleter(duelCommand);

        DuelAdminCommand adminCommand = new DuelAdminCommand(this);
        var adminPluginCommand = Objects.requireNonNull(getCommand("dueladmin"),
                "dueladmin command missing from plugin.yml");
        adminPluginCommand.setExecutor(adminCommand);
        adminPluginCommand.setTabCompleter(adminCommand);

        getServer().getPluginManager().registerEvents(new DuelCombatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new KitSelectListener(this), this);

        arenaManager.loadArenas();
        kitManager.loadKits();
        if (!statsManager.init()) {
            getLogger().warning("Statistics are disabled until the database configuration is fixed.");
        }

        getLogger().info("BasedDuels enabled. " + arenaManager.getArenaCount() + " arena(s) loaded, "
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
        instance = null;
        getLogger().info("BasedDuels disabled.");
    }

    private void saveResourceIfMissing(String name) {
        java.io.File f = new java.io.File(getDataFolder(), name);
        if (!f.exists()) {
            saveResource(name, false);
        }
    }

    public static BasedDuels getInstance() {
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
