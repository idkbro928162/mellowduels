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
        if (duelManager.isInDuel(challenger.getUniqueId()) || duelManager.isInDuel(target.getUniqueId())) {
            return "already-in-duel";
        }
        if (incomingRequests.containsKey(target.getUniqueId())) {
            return "duplicate-request";
        }
        Kit kit = kitManager.getKit(kitId);
        if (kit == null) {
            return "invalid-kit";
        }

        incomingRequests.put(target.getUniqueId(), new PendingRequest(challenger.getUniqueId(), kitId, System.currentTimeMillis()));

        challenger.sendMessage(configManager.message("challenge-sent")
                .replace("%target%", target.getName()).replace("%kit%", kit.getDisplayName()));
        target.sendMessage(configManager.message("challenge-received").replace("%player%", challenger.getName()));

        // auto-expire after timeout
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                PendingRequest req = incomingRequests.get(target.getUniqueId());
                if (req != null && req.challenger().equals(challenger.getUniqueId())) {
                    incomingRequests.remove(target.getUniqueId());
                    Player c = Bukkit.getPlayer(challenger.getUniqueId());
                    if (c != null) {
                        c.sendMessage(configManager.message("challenge-expired").replace("%target%", target.getName()));
                    }
                }
            }
        }.runTaskLater(plugin, REQUEST_TIMEOUT_MILLIS / 50);

        return "sent";
    }

    public boolean accept(Player target) {
        PendingRequest req = incomingRequests.remove(target.getUniqueId());
        if (req == null) return false;
        if (System.currentTimeMillis() - req.sentAtMillis() > REQUEST_TIMEOUT_MILLIS) return false;

        Player challenger = Bukkit.getPlayer(req.challenger());
        if (challenger == null) return false;

        Kit kit = kitManager.getKit(req.kitId());
        duelManager.startDuel(challenger, target, kit, null);
        return true;
    }

    public boolean deny(Player target) {
        return incomingRequests.remove(target.getUniqueId()) != null;
    }

    public boolean hasIncomingRequest(UUID uuid) {
        return incomingRequests.containsKey(uuid);
    }
}
