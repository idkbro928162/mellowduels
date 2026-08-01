package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.models.PlayerState;
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

    public void restore(Player player) {
        PlayerState state = savedStates.remove(player.getUniqueId());
        if (state != null) {
            state.restore(player);
        }
    }

    public void discard(UUID uuid) {
        savedStates.remove(uuid);
    }
}
