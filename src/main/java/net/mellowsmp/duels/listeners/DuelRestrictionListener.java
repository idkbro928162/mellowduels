package net.mellowsmp.duels.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.DuelSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Locale;
import java.util.UUID;

/**
 * Freeze, hunger/regen rules, chat isolation, and teleport guards for active duels.
 */
public class DuelRestrictionListener implements Listener {

    private final MellowDuels plugin;

    public DuelRestrictionListener(MellowDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        DuelSession session = plugin.getDuelManager().getSession(event.getPlayer().getUniqueId());
        if (session == null || session.getPhase() != DuelSession.Phase.COUNTDOWN) {
            return;
        }
        if (event.getFrom().getX() == event.getTo().getX()
                && event.getFrom().getY() == event.getTo().getY()
                && event.getFrom().getZ() == event.getTo().getZ()) {
            return;
        }
        event.setTo(event.getFrom().clone().setDirection(event.getTo().getDirection()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        DuelSession session = plugin.getDuelManager().getSession(event.getPlayer().getUniqueId());
        if (session != null && session.getPhase() != DuelSession.Phase.ACTIVE) {
            event.setCancelled(true);
        }
        if (plugin.getSpectatorManager().isSpectating(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        DuelSession session = plugin.getDuelManager().getSession(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.isInternalTeleport()) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DuelSession session = plugin.getDuelManager().getSession(player.getUniqueId());
        if (session != null && plugin.getConfigManager().disableHunger()) {
            event.setCancelled(true);
            player.setFoodLevel(session.getKit().getHunger());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DuelSession session = plugin.getDuelManager().getSession(player.getUniqueId());
        if (session == null) {
            return;
        }
        boolean disable = plugin.getConfigManager().disableNaturalRegen() || session.getKit().isDisableNaturalRegen();
        if (!disable) {
            return;
        }
        EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
        if (reason == EntityRegainHealthEvent.RegainReason.SATIATED
                || reason == EntityRegainHealthEvent.RegainReason.REGEN
                || reason == EntityRegainHealthEvent.RegainReason.EATING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (plugin.getSpectatorManager().isSpectating(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        DuelSession session = plugin.getDuelManager().getSession(player.getUniqueId());
        if (session != null && session.getPhase() != DuelSession.Phase.ACTIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDuelManager().isInDuel(player.getUniqueId())) {
            return;
        }
        String msg = event.getMessage().toLowerCase(Locale.ROOT);
        String body = msg.startsWith("/") ? msg.substring(1) : msg;
        if (body.startsWith("duel leave") || body.startsWith("duels leave")
                || body.startsWith("duel forfeit") || body.startsWith("duels forfeit")
                || body.startsWith("duel stats") || body.startsWith("duels stats")
                || body.startsWith("duel help") || body.startsWith("msg ") || body.startsWith("tell ")
                || body.startsWith("r ") || body.startsWith("w ") || body.startsWith("reply")) {
            return;
        }
        if (body.startsWith("tp") || body.startsWith("tpa") || body.startsWith("spawn")
                || body.startsWith("home") || body.startsWith("warp") || body.startsWith("back")
                || body.startsWith("gamemode") || body.startsWith("gm ") || body.startsWith("kill")
                || body.startsWith("duel") || body.startsWith("duels")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().message("already-in-duel")
                    + " Use /duel leave to forfeit.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        boolean isolate = plugin.getConfigManager().isolateDuelChat();
        boolean allowSpecChat = plugin.getConfigManager().allowSpectatorChat();

        DuelSession senderSession = plugin.getDuelManager().getSession(sender.getUniqueId());
        String spectating = plugin.getSpectatorManager().getSpectatedSession(sender.getUniqueId());

        if (spectating != null && !allowSpecChat) {
            event.setCancelled(true);
            return;
        }
        if (!isolate) {
            return;
        }

        event.viewers().removeIf(audience -> {
            if (!(audience instanceof Player viewer)) {
                return false;
            }
            UUID viewerId = viewer.getUniqueId();
            if (viewerId.equals(sender.getUniqueId())) {
                return false;
            }
            DuelSession viewerSession = plugin.getDuelManager().getSession(viewerId);
            String viewerSpec = plugin.getSpectatorManager().getSpectatedSession(viewerId);

            if (senderSession != null) {
                return !(senderSession.hasPlayer(viewerId)
                        || senderSession.getId().equals(viewerSpec));
            }
            if (spectating != null) {
                DuelSession watched = plugin.getDuelManager().getSessionById(spectating);
                return watched == null || !(watched.hasPlayer(viewerId) || spectating.equals(viewerSpec));
            }
            return viewerSession != null || viewerSpec != null;
        });
    }
}
