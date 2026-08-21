package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.Kit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles direct /duel challenge requests between two specific players
 * (as opposed to the anonymous kit-based QueueManager matchmaking).
 */
public class RequestManager {

    private static final long REQUEST_TIMEOUT_MILLIS = 60_000L;

    private record PendingRequest(UUID challenger, String kitId, long sentAtMillis) {
    }

    private final MellowDuels plugin;
    private final DuelManager duelManager;
    private final KitManager kitManager;
    private final ConfigManager configManager;

    // key = target player uuid, value = who challenged them
    private final Map<UUID, PendingRequest> incomingRequests = new ConcurrentHashMap<>();

    public RequestManager(MellowDuels plugin, DuelManager duelManager, KitManager kitManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.duelManager = duelManager;
        this.kitManager = kitManager;
        this.configManager = configManager;
    }

    public String challenge(Player challenger, Player target, String kitId) {
        if (challenger.getUniqueId().equals(target.getUniqueId())) {
            return "self";
        }
        if (duelManager.isInDuel(challenger.getUniqueId()) || duelManager.isInDuel(target.getUniqueId())) {
            return "already-in-duel";
        }
        if (plugin.getSpectatorManager().isSpectating(challenger.getUniqueId())
                || plugin.getSpectatorManager().isSpectating(target.getUniqueId())) {
            return "spectating";
        }
        if (plugin.getQueueManager().isQueued(challenger.getUniqueId())
                || plugin.getQueueManager().isQueued(target.getUniqueId())) {
            return "in-queue";
        }
        if (incomingRequests.containsKey(target.getUniqueId())) {
            return "duplicate-request";
        }
        Kit kit = kitManager.getKit(kitId);
        if (kit == null) {
            return "invalid-kit";
        }

        incomingRequests.put(target.getUniqueId(),
                new PendingRequest(challenger.getUniqueId(), kitId, System.currentTimeMillis()));

        challenger.sendMessage(configManager.message("challenge-sent")
                .replace("%target%", target.getName()).replace("%kit%", kit.getDisplayName()));
        target.sendMessage(configManager.message("challenge-received")
                .replace("%player%", challenger.getName())
                .replace("%kit%", kit.getDisplayName()));

        final UUID targetId = target.getUniqueId();
        final UUID challengerId = challenger.getUniqueId();
        final String targetName = target.getName();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingRequest req = incomingRequests.get(targetId);
            if (req != null && req.challenger().equals(challengerId)
                    && System.currentTimeMillis() - req.sentAtMillis() >= REQUEST_TIMEOUT_MILLIS - 50) {
                incomingRequests.remove(targetId, req);
                Player c = Bukkit.getPlayer(challengerId);
                if (c != null) {
                    c.sendMessage(configManager.message("challenge-expired").replace("%target%", targetName));
                }
            }
        }, REQUEST_TIMEOUT_MILLIS / 50);

        return "sent";
    }

    public boolean accept(Player target) {
        PendingRequest req = incomingRequests.remove(target.getUniqueId());
        if (req == null) return false;
        if (System.currentTimeMillis() - req.sentAtMillis() > REQUEST_TIMEOUT_MILLIS) return false;

        Player challenger = Bukkit.getPlayer(req.challenger());
        if (challenger == null) return false;
        if (duelManager.isInDuel(challenger.getUniqueId()) || duelManager.isInDuel(target.getUniqueId())) {
            return false;
        }

        Kit kit = kitManager.getKit(req.kitId());
        if (kit == null) return false;

        return duelManager.startDuel(challenger, target, kit, null);
    }

    public boolean deny(Player target) {
        PendingRequest req = incomingRequests.remove(target.getUniqueId());
        if (req == null) return false;
        Player challenger = Bukkit.getPlayer(req.challenger());
        if (challenger != null) {
            challenger.sendMessage(configManager.message("challenge-denied").replace("%player%", target.getName()));
        }
        return true;
    }

    public void clearFor(UUID uuid) {
        incomingRequests.remove(uuid);
        incomingRequests.entrySet().removeIf(e -> e.getValue().challenger().equals(uuid));
    }

    public boolean hasIncomingRequest(UUID uuid) {
        return incomingRequests.containsKey(uuid);
    }
}
