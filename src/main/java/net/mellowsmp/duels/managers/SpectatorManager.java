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

    public boolean startSpectating(Player spectator, String sessionId, Arena arena, Player... participants) {
        if (plugin.getDuelManager().isInDuel(spectator.getUniqueId())) {
            return false;
        }
        if (isSpectating(spectator.getUniqueId())) {
            stopSpectating(spectator);
        }
        plugin.getPlayerStateManager().save(spectator);
        spectatingSessionBySpectator.put(spectator.getUniqueId(), sessionId);
        spectator.closeInventory();
        spectator.setGameMode(GameMode.SPECTATOR);
        spectator.setAllowFlight(true);
        spectator.setFlying(true);
        if (arena.getSpectatorSpawn() != null) {
            spectator.teleport(arena.getSpectatorSpawn());
        } else {
            spectator.teleport(arena.spawnOrOrigin(arena.getOrigin()));
        }

        if (plugin.getConfigManager().hideSpectatorsFromParticipants()) {
            for (Player participant : participants) {
                if (participant != null && participant.isOnline()) {
                    participant.hidePlayer(plugin, spectator);
                }
            }
        }
        return true;
    }

    public void stopSpectating(Player spectator) {
        String sessionId = spectatingSessionBySpectator.remove(spectator.getUniqueId());
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            other.showPlayer(plugin, spectator);
        }
        if (sessionId != null) {
            plugin.getPlayerStateManager().restore(spectator);
        }
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
                }
            }
        }
    }

    public void stopAll() {
        for (UUID uuid : Set.copyOf(spectatingSessionBySpectator.keySet())) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) {
                stopSpectating(p);
            } else {
                spectatingSessionBySpectator.remove(uuid);
            }
        }
    }

    public Set<UUID> getSpectators() {
        return Set.copyOf(spectatingSessionBySpectator.keySet());
    }
}
