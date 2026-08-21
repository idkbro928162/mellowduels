package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.Kit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
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

    private final Map<String, Deque<UUID>> queuesByKit = new ConcurrentHashMap<>();
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
        if (plugin.getSpectatorManager().isSpectating(player.getUniqueId())) {
            return "spectating";
        }
        Kit kit = kitManager.getKit(kitId);
        if (kit == null) {
            return "invalid-kit";
        }

        Deque<UUID> queue = queuesByKit.computeIfAbsent(kitId, k -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(player.getUniqueId());
        }
        kitByQueuedPlayer.put(player.getUniqueId(), kitId);
        player.sendMessage(configManager.message("queue-joined").replace("%kit%", kit.getDisplayName()));

        tryMatch(kitId);
        return "joined";
    }

    public void leave(Player player) {
        String kitId = leaveQuiet(player.getUniqueId());
        if (kitId != null) {
            Kit kit = kitManager.getKit(kitId);
            player.sendMessage(configManager.message("queue-left")
                    .replace("%kit%", kit != null ? kit.getDisplayName() : kitId));
        }
    }

    /** Removes a player from any queue without messaging them. */
    public String leaveQuiet(UUID uuid) {
        String kitId = kitByQueuedPlayer.remove(uuid);
        if (kitId != null) {
            Deque<UUID> queue = queuesByKit.get(kitId);
            if (queue != null) {
                synchronized (queue) {
                    queue.remove(uuid);
                }
            }
        }
        return kitId;
    }

    private void tryMatch(String kitId) {
        Deque<UUID> queue = queuesByKit.get(kitId);
        if (queue == null) return;

        UUID uuidA;
        UUID uuidB;
        synchronized (queue) {
            if (queue.size() < 2) return;
            uuidA = queue.pollFirst();
            uuidB = queue.pollFirst();
        }
        if (uuidA == null || uuidB == null) {
            if (uuidA != null) requeue(uuidA, kitId);
            if (uuidB != null) requeue(uuidB, kitId);
            return;
        }

        kitByQueuedPlayer.remove(uuidA);
        kitByQueuedPlayer.remove(uuidB);

        Player a = Bukkit.getPlayer(uuidA);
        Player b = Bukkit.getPlayer(uuidB);

        if (a == null || b == null) {
            if (a != null) join(a, kitId);
            if (b != null) join(b, kitId);
            return;
        }

        Kit kit = kitManager.getKit(kitId);
        if (kit == null) {
            a.sendMessage("§cThat kit is no longer available.");
            b.sendMessage("§cThat kit is no longer available.");
            return;
        }

        if (!duelManager.startDuel(a, b, kit, null)) {
            // Arena unavailable — put them back without immediately rematching (avoids recursion)
            enqueueOnly(a, kitId);
            enqueueOnly(b, kitId);
        }
    }

    private void enqueueOnly(Player player, String kitId) {
        if (isQueued(player.getUniqueId()) || duelManager.isInDuel(player.getUniqueId())) {
            return;
        }
        Kit kit = kitManager.getKit(kitId);
        if (kit == null) return;
        Deque<UUID> queue = queuesByKit.computeIfAbsent(kitId, k -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(player.getUniqueId());
        }
        kitByQueuedPlayer.put(player.getUniqueId(), kitId);
        player.sendMessage(configManager.message("queue-joined").replace("%kit%", kit.getDisplayName()));
    }

    private void requeue(UUID uuid, String kitId) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            enqueueOnly(player, kitId);
        }
    }
}
