package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.BasedDuels;
import net.mellowsmp.duels.models.Kit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple FIFO matchmaking queue per kit. When two players are waiting for the
 * same kit, they're paired and a duel is started automatically.
 */
public class QueueManager {

    private final BasedDuels plugin;
    private final DuelManager duelManager;
    private final KitManager kitManager;
    private final ConfigManager configManager;

    private final Map<String, Set<UUID>> queuesByKit = new ConcurrentHashMap<>();
    private final Map<UUID, String> kitByQueuedPlayer = new ConcurrentHashMap<>();

    public QueueManager(BasedDuels plugin, DuelManager duelManager, KitManager kitManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.duelManager = duelManager;
        this.kitManager = kitManager;
        this.configManager = configManager;
    }

    public boolean isQueued(UUID uuid) {
        return kitByQueuedPlayer.containsKey(uuid);
    }

    public synchronized String join(Player player, String requestedKitId) {
        if (isQueued(player.getUniqueId())) {
            return "already-in-queue";
        }
        if (duelManager.isInDuel(player.getUniqueId())) {
            return "already-in-duel";
        }
        if (plugin.getSpectatorManager().isSpectating(player.getUniqueId())) {
            return "already-spectating";
        }
        Kit kit = kitManager.getKit(requestedKitId);
        if (kit == null) {
            return "invalid-kit";
        }
        String kitId = kit.getId().toLowerCase(Locale.ROOT);

        Set<UUID> queue = queuesByKit.computeIfAbsent(kitId, k -> new LinkedHashSet<>());
        queue.add(player.getUniqueId());
        kitByQueuedPlayer.put(player.getUniqueId(), kitId);
        player.sendMessage(configManager.message("queue-joined").replace("%kit%", kit.getDisplayName()));

        tryMatch(kitId);
        return "joined";
    }

    public synchronized void leave(Player player) {
        String kitId = removeSilently(player.getUniqueId());
        if (kitId != null) {
            Kit kit = kitManager.getKit(kitId);
            player.sendMessage(configManager.message("queue-left")
                    .replace("%kit%", kit != null ? kit.getDisplayName() : kitId));
        }
    }

    public synchronized String removeSilently(UUID uuid) {
        String kitId = kitByQueuedPlayer.remove(uuid);
        if (kitId != null) {
            Set<UUID> queue = queuesByKit.get(kitId);
            if (queue != null) {
                queue.remove(uuid);
                if (queue.isEmpty()) {
                    queuesByKit.remove(kitId);
                }
            }
        }
        return kitId;
    }

    private synchronized void tryMatch(String kitId) {
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
        if (kit == null) {
            a.sendMessage(configManager.message("invalid-kit"));
            b.sendMessage(configManager.message("invalid-kit"));
            return;
        }
        if (!duelManager.startDuel(a, b, kit, null)) {
            requeueIfEligible(a, kitId);
            requeueIfEligible(b, kitId);
        }
    }

    private void requeueIfEligible(Player player, String kitId) {
        if (player.isOnline() && !duelManager.isInDuel(player.getUniqueId())) {
            queuesByKit.computeIfAbsent(kitId, ignored -> new LinkedHashSet<>()).add(player.getUniqueId());
            kitByQueuedPlayer.put(player.getUniqueId(), kitId);
        }
    }
}
