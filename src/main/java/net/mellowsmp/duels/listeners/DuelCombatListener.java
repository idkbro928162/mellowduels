package net.mellowsmp.duels.listeners;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.DuelSession;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.projectiles.ProjectileSource;

public class DuelCombatListener implements Listener {

    private final MellowDuels plugin;

    public DuelCombatListener(MellowDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        if (plugin.getSpectatorManager().isSpectating(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        DuelSession session = plugin.getDuelManager().getSession(victim.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.getPhase() != DuelSession.Phase.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        Player attacker = resolveAttacker(event);
        if (attacker != null) {
            // Only the duel opponent may deal damage; block third-party interference
            if (!session.hasPlayer(attacker.getUniqueId())
                    || attacker.getUniqueId().equals(victim.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            plugin.getDuelManager().recordDamage(session.getId(), attacker.getUniqueId(), event.getFinalDamage());
        }

        // End the duel cleanly before a real death (avoids drops / respawn screen)
        if (victim.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            victim.setHealth(Math.max(1.0, victim.getHealth()));
            var opponent = session.getOpponent(victim.getUniqueId());
            if (opponent != null) {
                plugin.getDuelManager().endDuel(session.getId(), opponent, true);
            }
        }
    }

    private Player resolveAttacker(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        DuelSession session = plugin.getDuelManager().getSession(victim.getUniqueId());
        if (session == null) return;

        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.deathMessage(null);

        var opponent = session.getOpponent(victim.getUniqueId());
        if (opponent != null) {
            plugin.getDuelManager().endDuel(session.getId(), opponent, true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Freeze players during countdown (block position changes, allow look)
        if (event.getTo() == null) return;
        DuelSession session = plugin.getDuelManager().getSession(event.getPlayer().getUniqueId());
        if (session == null || session.getPhase() != DuelSession.Phase.COUNTDOWN) return;
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom().clone().setDirection(event.getTo().getDirection()));
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        DuelSession session = plugin.getDuelManager().getSession(player.getUniqueId());
        if (session == null) return;
        if (plugin.getConfigManager().disableHunger() || session.getPhase() != DuelSession.Phase.ACTIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        DuelSession session = plugin.getDuelManager().getSession(player.getUniqueId());
        if (session == null) return;
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                || event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) {
            boolean kitDisable = session.getKit() != null && session.getKit().isDisableNaturalRegen();
            if (plugin.getConfigManager().disableNaturalRegen() || kitDisable) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getSpectatorManager().isSpectating(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        DuelSession session = plugin.getDuelManager().getSession(event.getPlayer().getUniqueId());
        if (session != null && session.getPhase() == DuelSession.Phase.COUNTDOWN) {
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
        if (session != null && session.getPhase() == DuelSession.Phase.COUNTDOWN) {
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
        if (session != null && session.getPhase() == DuelSession.Phase.COUNTDOWN) {
            event.setCancelled(true);
        }
    }
}
