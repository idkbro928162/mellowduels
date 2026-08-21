package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.BasedDuels;
import net.mellowsmp.duels.models.Arena;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpectatorManager {

    private final BasedDuels plugin;
    private final Map<UUID, String> spectatingSessionBySpectator = new ConcurrentHashMap<>();

    public SpectatorManager(BasedDuels plugin) {
        this.plugin = plugin;
    }

    public boolean startSpectating(Player spectator, String sessionId, Arena arena) {
        if (!plugin.getConfigManager().spectatorEnabled()
                || isSpectating(spectator.getUniqueId())
                || plugin.getDuelManager().isInDuel(spectator.getUniqueId())
                || plugin.getDuelManager().getSessionById(sessionId) == null) {
            return false;
        }
        plugin.getQueueManager().removeSilently(spectator.getUniqueId());
        plugin.getRequestManager().removeRequestsFor(spectator.getUniqueId());
        plugin.getPlayerStateManager().save(spectator);
        spectatingSessionBySpectator.put(spectator.getUniqueId(), sessionId);
        spectator.setGameMode(GameMode.SPECTATOR);
        boolean teleported = spectator.teleport(arena.getSpectatorSpawn() != null
                ? arena.getSpectatorSpawn() : arena.getOrigin());
        if (!teleported) {
            spectatingSessionBySpectator.remove(spectator.getUniqueId());
            plugin.getPlayerStateManager().restore(spectator);
            return false;
        }
        if (plugin.getConfigManager().hideSpectatorsFromParticipants()) {
            var session = plugin.getDuelManager().getSessionById(sessionId);
            if (session != null) {
                hideFrom(session.getPlayerA(), spectator);
                hideFrom(session.getPlayerB(), spectator);
            }
        }
        return true;
    }

    public void stopSpectating(Player spectator) {
        String sessionId = spectatingSessionBySpectator.remove(spectator.getUniqueId());
        if (sessionId == null) {
            return;
        }
        var session = plugin.getDuelManager().getSessionById(sessionId);
        if (session != null) {
            showTo(session.getPlayerA(), spectator);
            showTo(session.getPlayerB(), spectator);
        } else {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                online.showPlayer(plugin, spectator);
            }
        }
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
                    spectatingSessionBySpectator.remove(e.getKey(), sessionId);
                    plugin.getPlayerStateManager().discard(e.getKey());
                }
            }
        }
    }

    public Set<UUID> getSpectators() {
        return Set.copyOf(spectatingSessionBySpectator.keySet());
    }

    private void hideFrom(UUID participantId, Player spectator) {
        Player participant = plugin.getServer().getPlayer(participantId);
        if (participant != null) {
            participant.hidePlayer(plugin, spectator);
        }
    }

    private void showTo(UUID participantId, Player spectator) {
        Player participant = plugin.getServer().getPlayer(participantId);
        if (participant != null) {
            participant.showPlayer(plugin, spectator);
        }
    }
}
