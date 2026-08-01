package net.mellowsmp.duels.listeners;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.DuelSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public class DuelCombatListener implements Listener {

    private final MellowDuels plugin;

    public DuelCombatListener(MellowDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        // Spectators can never take damage
        if (plugin.getSpectatorManager().isSpectating(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        DuelSession session = plugin.getDuelManager().getSession(victim.getUniqueId());
        if (session == null) {
            return; // not in a duel - normal server rules apply
        }
        if (session.getPhase() != DuelSession.Phase.ACTIVE) {
            event.setCancelled(true); // no damage during countdown/ending
            return;
        }

        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player attacker) {
            plugin.getDuelManager().recordDamage(session.getId(), attacker.getUniqueId(), event.getFinalDamage());
        }

        // Detect a lethal hit ourselves so we can end the duel cleanly instead of
        // letting the player actually die (avoids item drops, respawn screen, etc.)
        if (victim.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            victim.setHealth(1.0); // keep them alive; endDuel restores their real state anyway
            DuelSession current = plugin.getDuelManager().getSessionById(session.getId());
            if (current != null) {
                var opponent = current.getOpponent(victim.getUniqueId());
                if (opponent != null) {
                    plugin.getDuelManager().endDuel(session.getId(), opponent);
                }
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // Safety net in case health hit 0 despite the cancellation above (e.g. void damage)
        Player victim = event.getEntity();
        DuelSession session = plugin.getDuelManager().getSession(victim.getUniqueId());
        if (session != null) {
            event.setCancelled(true);
            event.getDrops().clear();
            var opponent = session.getOpponent(victim.getUniqueId());
            if (opponent != null) {
                plugin.getDuelManager().endDuel(session.getId(), opponent);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getSpectatorManager().isSpectating(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getSpectatorManager().isSpectating(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getSpectatorManager().isSpectating(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
