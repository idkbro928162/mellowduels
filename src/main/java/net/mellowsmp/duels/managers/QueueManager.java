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
    private final Object queueLock = new Object();

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

    public String getQueuedKit(UUID uuid) {
        return kitByQueuedPlayer.get(uuid);
    }

    public String join(Player player, String kitId) {
        if (duelManager.isInDuel(player.getUniqueId())) {
            return "already-in-duel";
        }
        if (plugin.getSpectatorManager().isSpectating(player.getUniqueId())) {
            return "cannot-spectate";
        }
        Kit kit = kitManager.getKit(kitId);
        if (kit == null) {
            return "invalid-kit";
        }
        synchronized (queueLock) {
            if (isQueued(player.getUniqueId())) {
                return "already-in-queue";
            }
            Set<UUID> queue = queuesByKit.computeIfAbsent(kit.getId(), k -> new LinkedHashSet<>());
            queue.add(player.getUniqueId());
            kitByQueuedPlayer.put(player.getUniqueId(), kit.getId());
        }
        player.sendMessage(configManager.message("queue-joined", "%kit%", kit.getDisplayName()));
        tryMatch(kit.getId());
        return "joined";
    }

    public boolean leave(Player player, boolean silent) {
        String kitId;
        synchronized (queueLock) {
            kitId = kitByQueuedPlayer.remove(player.getUniqueId());
            if (kitId != null) {
                Set<UUID> queue = queuesByKit.get(kitId);
                if (queue != null) {
                    queue.remove(player.getUniqueId());
                }
            }
        }
        if (kitId == null) {
            return false;
        }
        if (!silent) {
            Kit kit = kitManager.getKit(kitId);
            player.sendMessage(configManager.message("queue-left",
                    "%kit%", kit != null ? kit.getDisplayName() : kitId));
        }
        return true;
    }

    public void leave(Player player) {
        if (!leave(player, false) && player.isOnline()) {
            player.sendMessage(configManager.message("not-in-queue"));
        }
    }

    public void clearAll() {
        synchronized (queueLock) {
            queuesByKit.clear();
            kitByQueuedPlayer.clear();
        }
    }

    private void tryMatch(String kitId) {
        UUID uuidA;
        UUID uuidB;
        synchronized (queueLock) {
            Set<UUID> queue = queuesByKit.get(kitId);
            if (queue == null || queue.size() < 2) {
                return;
            }
            java.util.Iterator<UUID> it = queue.iterator();
            uuidA = it.next();
            uuidB = it.next();
            queue.remove(uuidA);
            queue.remove(uuidB);
            kitByQueuedPlayer.remove(uuidA);
            kitByQueuedPlayer.remove(uuidB);
        }

        Player a = Bukkit.getPlayer(uuidA);
        Player b = Bukkit.getPlayer(uuidB);
        if (a == null || !a.isOnline() || b == null || !b.isOnline()) {
            if (a != null && a.isOnline()) {
                join(a, kitId);
            }
            if (b != null && b.isOnline()) {
                join(b, kitId);
            }
            return;
        }

        Kit kit = kitManager.getKit(kitId);
        if (kit == null) {
            return;
        }
        if (!duelManager.startDuel(a, b, kit, null)) {
            requeueQuietly(a, kitId);
            requeueQuietly(b, kitId);
        }
    }

    /** Puts a player back into a kit queue without immediately retrying a match. */
    private void requeueQuietly(Player player, String kitId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        synchronized (queueLock) {
            if (isQueued(player.getUniqueId()) || duelManager.isInDuel(player.getUniqueId())) {
                return;
            }
            queuesByKit.computeIfAbsent(kitId, k -> new LinkedHashSet<>()).add(player.getUniqueId());
            kitByQueuedPlayer.put(player.getUniqueId(), kitId);
        }
        Kit kit = kitManager.getKit(kitId);
        player.sendMessage(configManager.message("queue-joined",
                "%kit%", kit != null ? kit.getDisplayName() : kitId));
    }
}
