package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.Arena;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpectatorManager {

    private final MellowDuels plugin;
    private final Map<UUID, String> spectatingSessionBySpectator = new ConcurrentHashMap<>();

    public SpectatorManager(MellowDuels plugin) {
        this.plugin = plugin;
    }

    public boolean startSpectating(Player spectator, String sessionId, Arena arena) {
        if (!plugin.getConfigManager().spectatorEnabled()) {
            return false;
        }
        if (plugin.getDuelManager().isInDuel(spectator.getUniqueId())) {
            return false;
        }
        if (isSpectating(spectator.getUniqueId())) {
            stopSpectating(spectator);
        }

        plugin.getPlayerStateManager().save(spectator);
        spectatingSessionBySpectator.put(spectator.getUniqueId(), sessionId);
        spectator.setGameMode(GameMode.SPECTATOR);
        if (arena.getSpectatorSpawn() != null) {
            spectator.teleport(arena.getSpectatorSpawn());
        } else {
            spectator.teleport(arena.getOrigin());
        }
        return true;
    }

    public void stopSpectating(Player spectator) {
        if (!spectatingSessionBySpectator.containsKey(spectator.getUniqueId())) {
            return;
        }
        spectatingSessionBySpectator.remove(spectator.getUniqueId());
        plugin.getPlayerStateManager().restore(spectator);
    }

    public boolean isSpectating(UUID uuid) {
        return spectatingSessionBySpectator.containsKey(uuid);
    }

    public String getSpectatedSession(UUID uuid) {
        return spectatingSessionBySpectator.get(uuid);
    }

    /** Stops spectating everyone watching a given session, e.g. once it ends. */
    public void stopAllSpectatorsOf(String sessionId) {
        for (Map.Entry<UUID, String> e : Map.copyOf(spectatingSessionBySpectator).entrySet()) {
            if (e.getValue().equals(sessionId)) {
                Player p = plugin.getServer().getPlayer(e.getKey());
                if (p != null) {
                    stopSpectating(p);
                } else {
                    spectatingSessionBySpectator.remove(e.getKey());
                    plugin.getPlayerStateManager().discard(e.getKey());
                }
            }
        }
    }

    public Set<UUID> getSpectators() {
        return spectatingSessionBySpectator.keySet();
    }
}
