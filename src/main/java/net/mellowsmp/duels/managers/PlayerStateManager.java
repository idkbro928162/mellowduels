package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.models.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerStateManager {

    private final Map<UUID, PlayerState> savedStates = new ConcurrentHashMap<>();

    public void save(Player player) {
        savedStates.put(player.getUniqueId(), PlayerState.capture(player));
    }

    public boolean hasSavedState(UUID uuid) {
        return savedStates.containsKey(uuid);
    }

    /**
     * Restores a saved snapshot if one exists. Returns true when a snapshot was applied.
     * Offline players keep their snapshot so it can be applied on the next join.
     */
    public boolean restore(Player player) {
        PlayerState state = savedStates.remove(player.getUniqueId());
        if (state != null) {
            state.restore(player);
            return true;
        }
        return false;
    }

    public void discard(UUID uuid) {
        savedStates.remove(uuid);
    }

    /** Restores leftover snapshots for anyone currently online (plugin disable / crash recovery). */
    public void restoreAllOnline() {
        for (UUID uuid : java.util.List.copyOf(savedStates.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                restore(player);
            }
        }
    }
}
