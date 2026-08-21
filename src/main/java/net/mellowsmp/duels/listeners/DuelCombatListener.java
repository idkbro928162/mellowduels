package net.mellowsmp.duels.listeners;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.DuelSession;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.projectiles.ProjectileSource;

public class DuelCombatListener implements Listener {

    private final MellowDuels plugin;

    public DuelCombatListener(MellowDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (plugin.getSpectatorManager().isSpectating(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        DuelSession victimSession = plugin.getDuelManager().getSession(victim.getUniqueId());
        Player attacker = resolveAttacker(event);

        if (attacker != null && plugin.getSpectatorManager().isSpectating(attacker.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (victimSession == null) {
            if (attacker != null && plugin.getDuelManager().isInDuel(attacker.getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }

        if (victimSession.getPhase() != DuelSession.Phase.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        if (attacker != null) {
            if (!victimSession.hasPlayer(attacker.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            plugin.getDuelManager().recordDamage(victimSession.getId(), attacker.getUniqueId(), event.getFinalDamage());
        } else if (event instanceof EntityDamageByEntityEvent) {
            // Explosion / crystal / unknown combat entity: if the other duelist is nearby, count it
            var opponentId = victimSession.getOpponent(victim.getUniqueId());
            if (opponentId != null) {
                plugin.getDuelManager().recordDamage(victimSession.getId(), opponentId, event.getFinalDamage());
            }
        }

        double remainingHealth = victim.getHealth() + victim.getAbsorptionAmount() - event.getFinalDamage();
        if (remainingHealth <= 0) {
            event.setCancelled(true);
            try {
                victim.setHealth(Math.max(1.0, victim.getHealth()));
            } catch (IllegalArgumentException ignored) {
                victim.setHealth(1.0);
            }
            var opponent = victimSession.getOpponent(victim.getUniqueId());
            if (opponent != null && plugin.getDuelManager().getSessionById(victimSession.getId()) != null) {
                plugin.getDuelManager().endDuel(victimSession.getId(), opponent);
            }
        }
    }

    private Player resolveAttacker(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        if (byEntity.getDamager() instanceof Player player) {
            return player;
        }
        if (byEntity.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        if (byEntity.getDamager() instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        if (byEntity.getDamager() instanceof EnderCrystal) {
            return null;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        DuelSession session = plugin.getDuelManager().getSession(victim.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        var opponent = session.getOpponent(victim.getUniqueId());
        if (opponent != null) {
            plugin.getDuelManager().endDuel(session.getId(), opponent);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getSpectatorManager().isSpectating(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        DuelSession session = plugin.getDuelManager().getSession(event.getPlayer().getUniqueId());
        if (session != null && session.getPhase() != DuelSession.Phase.ACTIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getSpectatorManager().isSpectating(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        DuelSession session = plugin.getDuelManager().getSession(event.getPlayer().getUniqueId());
        if (session != null && session.getPhase() != DuelSession.Phase.ACTIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getSpectatorManager().isSpectating(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        DuelSession session = plugin.getDuelManager().getSession(event.getPlayer().getUniqueId());
        if (session != null && session.getPhase() != DuelSession.Phase.ACTIVE) {
            event.setCancelled(true);
        }
    }
}
