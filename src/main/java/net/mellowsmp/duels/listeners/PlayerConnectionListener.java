package net.mellowsmp.duels.listeners;

import net.mellowsmp.duels.BasedDuels;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final BasedDuels plugin;

    public PlayerConnectionListener(BasedDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (plugin.getSpectatorManager().isSpectating(player.getUniqueId())) {
            plugin.getSpectatorManager().stopSpectating(player);
        }

        if (plugin.getQueueManager().isQueued(player.getUniqueId())) {
            plugin.getQueueManager().removeSilently(player.getUniqueId());
        }
        plugin.getRequestManager().removeRequestsFor(player.getUniqueId());

        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) {
            plugin.getDuelManager().handleDisconnect(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getDuelManager().handleReconnect(player);
        if (!plugin.getDuelManager().isInDuel(player.getUniqueId())
                && plugin.getPlayerStateManager().hasSavedState(player.getUniqueId())) {
            plugin.getPlayerStateManager().restore(player);
        }
    }
}
