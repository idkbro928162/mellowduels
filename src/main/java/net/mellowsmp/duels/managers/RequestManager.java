package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.BasedDuels;
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

    private final BasedDuels plugin;
    private final DuelManager duelManager;
    private final KitManager kitManager;
    private final ConfigManager configManager;

    // key = target player uuid, value = who challenged them
    private final Map<UUID, PendingRequest> incomingRequests = new ConcurrentHashMap<>();
    // key = challenger uuid, value = target uuid
    private final Map<UUID, UUID> outgoingTargets = new ConcurrentHashMap<>();

    public RequestManager(BasedDuels plugin, DuelManager duelManager, KitManager kitManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.duelManager = duelManager;
        this.kitManager = kitManager;
        this.configManager = configManager;
    }

    public String challenge(Player challenger, Player target, String kitId) {
        if (challenger.getUniqueId().equals(target.getUniqueId())) {
            return "self-challenge";
        }
        if (duelManager.isInDuel(challenger.getUniqueId()) || duelManager.isInDuel(target.getUniqueId())) {
            return "already-in-duel";
        }
        if (plugin.getSpectatorManager().isSpectating(challenger.getUniqueId())
                || plugin.getSpectatorManager().isSpectating(target.getUniqueId())) {
            return "already-spectating";
        }
        if (configManager.preventDuplicateRequests()
                && (incomingRequests.containsKey(target.getUniqueId())
                || outgoingTargets.containsKey(challenger.getUniqueId()))) {
            return "duplicate-request";
        }
        Kit kit = kitManager.getKit(kitId);
        if (kit == null) {
            return "invalid-kit";
        }

        removeRequestsFor(challenger.getUniqueId());
        PendingRequest request = new PendingRequest(challenger.getUniqueId(), kit.getId(), System.currentTimeMillis());
        PendingRequest replaced = incomingRequests.put(target.getUniqueId(), request);
        if (replaced != null) {
            outgoingTargets.remove(replaced.challenger(), target.getUniqueId());
        }
        outgoingTargets.put(challenger.getUniqueId(), target.getUniqueId());

        challenger.sendMessage(configManager.message("challenge-sent")
                .replace("%target%", target.getName()).replace("%kit%", kit.getDisplayName()));
        target.sendMessage(configManager.message("challenge-received")
                .replace("%player%", challenger.getName())
                .replace("%kit%", kit.getDisplayName()));

        // auto-expire after timeout
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                PendingRequest req = incomingRequests.get(target.getUniqueId());
                if (request.equals(req) && incomingRequests.remove(target.getUniqueId(), request)) {
                    outgoingTargets.remove(challenger.getUniqueId(), target.getUniqueId());
                    Player c = Bukkit.getPlayer(challenger.getUniqueId());
                    if (c != null) {
                        c.sendMessage(configManager.message("challenge-expired").replace("%target%", target.getName()));
                    }
                }
            }
        }.runTaskLater(plugin, REQUEST_TIMEOUT_MILLIS / 50);

        return "sent";
    }

    public String accept(Player target) {
        PendingRequest req = incomingRequests.remove(target.getUniqueId());
        if (req == null) return "no-request";
        outgoingTargets.remove(req.challenger(), target.getUniqueId());
        if (System.currentTimeMillis() - req.sentAtMillis() > REQUEST_TIMEOUT_MILLIS) return "expired";

        Player challenger = Bukkit.getPlayer(req.challenger());
        if (challenger == null) return "challenger-offline";

        Kit kit = kitManager.getKit(req.kitId());
        if (kit == null || duelManager.isInDuel(challenger.getUniqueId())
                || duelManager.isInDuel(target.getUniqueId())
                || plugin.getSpectatorManager().isSpectating(challenger.getUniqueId())
                || plugin.getSpectatorManager().isSpectating(target.getUniqueId())) {
            return "unavailable";
        }
        return duelManager.startDuel(challenger, target, kit, null) ? "started" : "unavailable";
    }

    public boolean deny(Player target) {
        PendingRequest request = incomingRequests.remove(target.getUniqueId());
        if (request == null) {
            return false;
        }
        outgoingTargets.remove(request.challenger(), target.getUniqueId());
        Player challenger = Bukkit.getPlayer(request.challenger());
        if (challenger != null) {
            challenger.sendMessage(configManager.message("challenge-denied")
                    .replace("%target%", target.getName()));
        }
        return true;
    }

    public boolean hasIncomingRequest(UUID uuid) {
        return incomingRequests.containsKey(uuid);
    }

    public void removeRequestsFor(UUID uuid) {
        UUID target = outgoingTargets.remove(uuid);
        if (target != null) {
            PendingRequest request = incomingRequests.get(target);
            if (request != null && request.challenger().equals(uuid)) {
                incomingRequests.remove(target, request);
            }
        }

        PendingRequest incoming = incomingRequests.remove(uuid);
        if (incoming != null) {
            outgoingTargets.remove(incoming.challenger(), uuid);
        }
    }
}
