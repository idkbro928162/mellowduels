/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerChangedWorldEvent
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.event.player.PlayerRespawnEvent
 *  org.bukkit.plugin.Plugin
 */
package dev.mellow.antiesp;

import dev.mellow.antiesp.AntiESPConfig;
import dev.mellow.antiesp.AntiESPPlugin;
import dev.mellow.antiesp.PlayerState;
import dev.mellow.antiesp.Schedulers;
import dev.mellow.antiesp.WorldRule;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

final class PlayerTracker
implements Listener {
    private final AntiESPPlugin plugin;
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<UUID, PlayerState>();

    PlayerTracker(AntiESPPlugin antiESPPlugin) {
        this.plugin = antiESPPlugin;
    }

    PlayerState get(UUID uUID) {
        return this.states.get(uUID);
    }

    Map<UUID, PlayerState> all() {
        return this.states;
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent playerJoinEvent) {
        Player player = playerJoinEvent.getPlayer();
        PlayerState playerState = this.states.computeIfAbsent(player.getUniqueId(), PlayerState::new);
        this.apply(player, playerState, true);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent playerQuitEvent) {
        PlayerState playerState = this.states.remove(playerQuitEvent.getPlayer().getUniqueId());
        if (playerState != null) {
            playerState.clearEntities();
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent playerChangedWorldEvent) {
        Player player = playerChangedWorldEvent.getPlayer();
        PlayerState playerState = this.states.computeIfAbsent(player.getUniqueId(), PlayerState::new);
        playerState.clearEntities();
        playerState.revealed.clear();
        this.apply(player, playerState, true);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent playerRespawnEvent) {
        Player player = playerRespawnEvent.getPlayer();
        PlayerState playerState = this.states.computeIfAbsent(player.getUniqueId(), PlayerState::new);
        playerState.y = playerRespawnEvent.getRespawnLocation().getY();
        this.apply(player, playerState, false);
    }

    void apply(Player player, PlayerState playerState, boolean bl) {
        AntiESPConfig antiESPConfig = this.plugin.config();
        Location location = player.getLocation();
        playerState.worldId = player.getWorld().getUID();
        playerState.bypass = false;
        playerState.rule = antiESPConfig.ruleFor(player.getWorld());
        if (bl) {
            playerState.x = location.getX();
            playerState.y = location.getY();
            playerState.z = location.getZ();
        }
        playerState.chunkX = location.getBlockX() >> 4;
        playerState.chunkZ = location.getBlockZ() >> 4;
        WorldRule worldRule = playerState.rule;
        playerState.hidden = this.plugin.isActive() && worldRule != null && !playerState.bypass && playerState.y >= (double)(worldRule.hideBelowY + antiESPConfig.hideMargin);
        boolean bl2 = playerState.hidden;
        if (!playerState.protectedWorld() || !this.plugin.isActive()) {
            this.plugin.visibility().revealAll(player, playerState);
        }
    }

    void applyAll() {
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            PlayerState playerState = this.states.computeIfAbsent(player.getUniqueId(), PlayerState::new);
            Schedulers.player((Plugin)this.plugin, player, () -> {
                this.apply(player, playerState, true);
                this.plugin.refresher().queueSquare(playerState.worldId, playerState.chunkX, playerState.chunkZ, this.plugin.config().refreshRadius);
            });
        }
    }

    void refreshPermissions() {
    }

    void move(PlayerState playerState, double d, double d2, double d3) {
        playerState.x = d;
        playerState.y = d2;
        playerState.z = d3;
        int n = (int)Math.floor(d) >> 4;
        int n2 = (int)Math.floor(d3) >> 4;
        WorldRule worldRule = playerState.rule;
        if (worldRule == null || playerState.bypass || !this.plugin.isActive()) {
            playerState.chunkX = n;
            playerState.chunkZ = n2;
            return;
        }
        AntiESPConfig antiESPConfig = this.plugin.config();
        boolean bl = playerState.hidden;
        boolean bl2 = bl ? d2 >= (double)(worldRule.hideBelowY + antiESPConfig.revealMargin) : d2 >= (double)(worldRule.hideBelowY + antiESPConfig.hideMargin);
        int n3 = playerState.chunkX;
        int n4 = playerState.chunkZ;
        playerState.chunkX = n;
        playerState.chunkZ = n2;
        if (bl2 != bl) {
            playerState.hidden = bl2;
            this.plugin.refresher().queueSquare(playerState.worldId, n, n2, antiESPConfig.refreshRadius);
            return;
        }
        if (!(bl2 || antiESPConfig.revealRadius < 0 || n == n3 && n2 == n4)) {
            this.plugin.refresher().queueRadiusDiff(playerState.worldId, n3, n4, n, n2, antiESPConfig.revealRadius);
        }
    }
}

