package com.based.itemesp.listeners;

import com.based.itemesp.managers.VisibilityManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Drops per-player caches on quit to avoid memory leaks.
 */
public final class QuitListener implements Listener {

    private final VisibilityManager visibilityManager;

    public QuitListener(VisibilityManager visibilityManager) {
        this.visibilityManager = visibilityManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        visibilityManager.removePlayer(event.getPlayer().getUniqueId());
    }
}
