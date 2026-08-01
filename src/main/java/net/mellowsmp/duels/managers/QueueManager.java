package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.Kit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple FIFO matchmaking queue per kit. When two players are waiting for the
 * same kit, they're paired and a duel is started automatically.
 */
public class QueueManager {

    private final MellowDuels plugin;
    private final DuelManager duelManager;
    private final KitManager kitManager;
    private final ConfigManager configManager;

    private final Map<String, Set<UUID>> queuesByKit = new ConcurrentHashMap<>();
    private final Map<UUID, String> kitByQueuedPlayer = new ConcurrentHashMap<>();

    public QueueManager(MellowDuels plugin, DuelManager duelManager, KitManager kitManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.duelManager = duelManager;
        this.kitManager = kitManager;
        this.configManager = configManager;
    }

    public boolean isQueued(UUID uuid) {
        return kitByQueuedPlayer.containsKey(uuid);
    }

    public String join(Player player, String kitId) {
        if (isQueued(player.getUniqueId())) {
            return "already-in-queue";
        }
        if (duelManager.isInDuel(player.getUniqueId())) {
            return "already-in-duel";
        }
        Kit kit = kitManager.getKit(kitId);
        if (kit == null) {
            return "invalid-kit";
        }

        Set<UUID> queue = queuesByKit.computeIfAbsent(kitId, k -> new LinkedHashSet<>());
        queue.add(player.getUniqueId());
        kitByQueuedPlayer.put(player.getUniqueId(), kitId);
        player.sendMessage(configManager.message("queue-joined").replace("%kit%", kit.getDisplayName()));

        tryMatch(kitId);
        return "joined";
    }

    public void leave(Player player) {
        String kitId = kitByQueuedPlayer.remove(player.getUniqueId());
        if (kitId != null) {
            Set<UUID> queue = queuesByKit.get(kitId);
            if (queue != null) queue.remove(player.getUniqueId());
            Kit kit = kitManager.getKit(kitId);
            player.sendMessage(configManager.message("queue-left")
                    .replace("%kit%", kit != null ? kit.getDisplayName() : kitId));
        }
    }

    private void tryMatch(String kitId) {
        Set<UUID> queue = queuesByKit.get(kitId);
        if (queue == null || queue.size() < 2) return;

        UUID[] pair = queue.toArray(new UUID[0]);
        UUID uuidA = pair[0];
        UUID uuidB = pair[1];

        Player a = Bukkit.getPlayer(uuidA);
        Player b = Bukkit.getPlayer(uuidB);

        queue.remove(uuidA);
        queue.remove(uuidB);
        kitByQueuedPlayer.remove(uuidA);
        kitByQueuedPlayer.remove(uuidB);

        if (a == null || b == null) {
            // one disconnected between queueing and matching; requeue the one still online
            if (a != null) join(a, kitId);
            if (b != null) join(b, kitId);
            return;
        }

        Kit kit = kitManager.getKit(kitId);
        duelManager.startDuel(a, b, kit, null);
    }
}
