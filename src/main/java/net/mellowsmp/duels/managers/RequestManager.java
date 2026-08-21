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

    private record PendingRequest(UUID challenger, UUID target, String kitId, long sentAtMillis) {
    }

    private final MellowDuels plugin;
    private final DuelManager duelManager;
    private final KitManager kitManager;
    private final ConfigManager configManager;

    /** target uuid -> challenger uuid -> request */
    private final Map<UUID, Map<UUID, PendingRequest>> incomingRequests = new ConcurrentHashMap<>();

    public RequestManager(MellowDuels plugin, DuelManager duelManager, KitManager kitManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.duelManager = duelManager;
        this.kitManager = kitManager;
        this.configManager = configManager;
    }

    public String challenge(Player challenger, Player target, String kitId) {
        if (challenger.getUniqueId().equals(target.getUniqueId())) {
            return "cannot-challenge-self";
        }
        if (duelManager.isInDuel(challenger.getUniqueId())) {
            return "already-in-duel";
        }
        if (duelManager.isInDuel(target.getUniqueId())) {
            return "opponent-in-duel";
        }
        if (plugin.getSpectatorManager().isSpectating(challenger.getUniqueId())
                || plugin.getSpectatorManager().isSpectating(target.getUniqueId())) {
            return "cannot-spectate";
        }
        Kit kit = kitManager.getKit(kitId);
        if (kit == null) {
            return "invalid-kit";
        }

        Map<UUID, PendingRequest> forTarget = incomingRequests.computeIfAbsent(target.getUniqueId(), k -> new ConcurrentHashMap<>());
        if (configManager.preventDuplicateRequests() && !forTarget.isEmpty() && !forTarget.containsKey(challenger.getUniqueId())) {
            return "duplicate-request";
        }

        PendingRequest request = new PendingRequest(challenger.getUniqueId(), target.getUniqueId(), kit.getId(), System.currentTimeMillis());
        forTarget.put(challenger.getUniqueId(), request);

        challenger.sendMessage(configManager.message("challenge-sent",
                "%target%", target.getName(), "%kit%", kit.getDisplayName()));
        target.sendMessage(configManager.message("challenge-received",
                "%player%", challenger.getName(), "%kit%", kit.getDisplayName()));

        Bukkit.getScheduler().runTaskLater(plugin, () -> expire(request), 20L * (REQUEST_TIMEOUT_MILLIS / 1000L));
        return "sent";
    }

    private void expire(PendingRequest request) {
        Map<UUID, PendingRequest> forTarget = incomingRequests.get(request.target());
        if (forTarget == null) {
            return;
        }
        PendingRequest current = forTarget.get(request.challenger());
        if (current == null || current.sentAtMillis() != request.sentAtMillis()) {
            return;
        }
        forTarget.remove(request.challenger());
        if (forTarget.isEmpty()) {
            incomingRequests.remove(request.target());
        }
        Player challenger = Bukkit.getPlayer(request.challenger());
        Player target = Bukkit.getPlayer(request.target());
        if (challenger != null) {
            String targetName = target != null ? target.getName() : "that player";
            challenger.sendMessage(configManager.message("challenge-expired", "%target%", targetName));
        }
    }

    public boolean accept(Player target, UUID expectedChallenger) {
        Map<UUID, PendingRequest> forTarget = incomingRequests.get(target.getUniqueId());
        if (forTarget == null || forTarget.isEmpty()) {
            return false;
        }

        PendingRequest req;
        if (expectedChallenger != null) {
            req = forTarget.remove(expectedChallenger);
        } else if (forTarget.size() == 1) {
            UUID only = forTarget.keySet().iterator().next();
            req = forTarget.remove(only);
        } else {
            return false;
        }
        if (forTarget.isEmpty()) {
            incomingRequests.remove(target.getUniqueId());
        }
        if (req == null) {
            return false;
        }
        if (System.currentTimeMillis() - req.sentAtMillis() > REQUEST_TIMEOUT_MILLIS) {
            return false;
        }

        Player challenger = Bukkit.getPlayer(req.challenger());
        if (challenger == null || !challenger.isOnline()) {
            target.sendMessage(configManager.message("challenger-offline"));
            return true;
        }
        if (duelManager.isInDuel(challenger.getUniqueId()) || duelManager.isInDuel(target.getUniqueId())) {
            target.sendMessage(configManager.message("already-in-duel"));
            return true;
        }

        Kit kit = kitManager.getKit(req.kitId());
        if (kit == null) {
            target.sendMessage(configManager.message("invalid-kit", "%kit%", req.kitId()));
            return true;
        }

        plugin.getQueueManager().leave(challenger, true);
        plugin.getQueueManager().leave(target, true);
        duelManager.startDuel(challenger, target, kit, null);
        return true;
    }

    public boolean accept(Player target) {
        return accept(target, null);
    }

    public boolean deny(Player target) {
        Map<UUID, PendingRequest> forTarget = incomingRequests.remove(target.getUniqueId());
        if (forTarget == null || forTarget.isEmpty()) {
            return false;
        }
        target.sendMessage(configManager.message("challenge-denied-self"));
        for (PendingRequest req : forTarget.values()) {
            Player challenger = Bukkit.getPlayer(req.challenger());
            if (challenger != null) {
                challenger.sendMessage(configManager.message("challenge-denied", "%player%", target.getName()));
            }
        }
        return true;
    }

    public boolean hasIncomingRequest(UUID uuid) {
        Map<UUID, PendingRequest> forTarget = incomingRequests.get(uuid);
        return forTarget != null && !forTarget.isEmpty();
    }

    public void clearFor(UUID uuid) {
        incomingRequests.remove(uuid);
        for (Map.Entry<UUID, Map<UUID, PendingRequest>> e : incomingRequests.entrySet()) {
            e.getValue().remove(uuid);
        }
        incomingRequests.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
}
