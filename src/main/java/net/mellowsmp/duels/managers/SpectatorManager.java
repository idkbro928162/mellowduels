package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.Arena;
import net.mellowsmp.duels.models.PlayerState;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.HashMap;
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

    public void startSpectating(Player spectator, String sessionId, Arena arena) {
        plugin.getPlayerStateManager().save(spectator);
        spectatingSessionBySpectator.put(spectator.getUniqueId(), sessionId);
        spectator.setGameMode(GameMode.SPECTATOR);
        if (arena.getSpectatorSpawn() != null) {
            spectator.teleport(arena.getSpectatorSpawn());
        } else {
            spectator.teleport(arena.getOrigin());
        }
    }

    public void stopSpectating(Player spectator) {
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
                }
            }
        }
    }

    public Set<UUID> getSpectators() {
        return spectatingSessionBySpectator.keySet();
    }
}
