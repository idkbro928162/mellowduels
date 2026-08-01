package net.mellowsmp.duels.listeners;

import net.mellowsmp.duels.MellowDuels;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final MellowDuels plugin;

    public PlayerConnectionListener(MellowDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (plugin.getSpectatorManager().isSpectating(player.getUniqueId())) {
            plugin.getSpectatorManager().stopSpectating(player);
        }

        if (plugin.getQueueManager().isQueued(player.getUniqueId())) {
            plugin.getQueueManager().leave(player);
        }

        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) {
            plugin.getDuelManager().handleDisconnect(player.getUniqueId());
        }
    }
}
