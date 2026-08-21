package net.mellowsmp.duels.listeners;

import net.mellowsmp.duels.BasedDuels;
import net.mellowsmp.duels.models.Arena;
import net.mellowsmp.duels.models.DuelSession;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;

public class DuelCombatListener implements Listener {

    private final BasedDuels plugin;

    public DuelCombatListener(BasedDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Player attacker = event instanceof EntityDamageByEntityEvent byEntity
                ? responsiblePlayer(byEntity.getDamager()) : null;
        DuelSession attackerSession = attacker == null
                ? null : plugin.getDuelManager().getSession(attacker.getUniqueId());

        if (!(event.getEntity() instanceof Player victim)) {
            if (attackerSession != null && !attackerSession.getArena().contains(event.getEntity().getLocation())) {
                event.setCancelled(true);
            }
            return;
        }

        // Spectators can never take damage
        if (plugin.getSpectatorManager().isSpectating(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        DuelSession session = plugin.getDuelManager().getSession(victim.getUniqueId());
        if (session == null) {
            if (attackerSession != null) {
                event.setCancelled(true);
            }
            return; // not in a duel - normal server rules apply
        }
        if (session.getPhase() != DuelSession.Phase.ACTIVE) {
            event.setCancelled(true); // no damage during countdown/ending
            return;
        }

        if (attacker != null) {
            UUID expectedOpponent = session.getOpponent(victim.getUniqueId());
            if (attackerSession != session || !attacker.getUniqueId().equals(expectedOpponent)) {
                event.setCancelled(true);
                return;
            }
            plugin.getDuelManager().recordDamage(session.getId(), attacker.getUniqueId(),
                    Math.min(event.getFinalDamage(), victim.getHealth()));
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

    private Player responsiblePlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
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
        DuelSession session = plugin.getDuelManager().getSession(event.getPlayer().getUniqueId());
        Arena arenaAtBlock = plugin.getArenaManager().getArenaAt(event.getBlock().getLocation());
        if (plugin.getSpectatorManager().isSpectating(event.getPlayer().getUniqueId())
                || arenaAtBlock != null && (session == null || session.getArena() != arenaAtBlock
                || session.getPhase() != DuelSession.Phase.ACTIVE)
                || session != null && arenaAtBlock == null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        DuelSession session = plugin.getDuelManager().getSession(event.getPlayer().getUniqueId());
        Arena arenaAtBlock = plugin.getArenaManager().getArenaAt(event.getBlock().getLocation());
        if (plugin.getSpectatorManager().isSpectating(event.getPlayer().getUniqueId())
                || arenaAtBlock != null && (session == null || session.getArena() != arenaAtBlock
                || session.getPhase() != DuelSession.Phase.ACTIVE)
                || session != null && arenaAtBlock == null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getSpectatorManager().isSpectating(event.getPlayer().getUniqueId())
                || plugin.getDuelManager().isInDuel(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        DuelSession session = plugin.getDuelManager().getSession(player.getUniqueId());
        Arena itemArena = plugin.getArenaManager().getArenaAt(event.getItem().getLocation());
        if (plugin.getSpectatorManager().isSpectating(player.getUniqueId())
                || itemArena != null && (session == null || session.getArena() != itemArena)
                || session != null && itemArena == null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.getDuelManager().isInDuel(player.getUniqueId())
                && plugin.getConfigManager().disableHunger()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        DuelSession session = plugin.getDuelManager().getSession(player.getUniqueId());
        if (session == null) return;
        if ((plugin.getConfigManager().disableNaturalRegen() || session.getKit().isDisableNaturalRegen())
                && (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                || event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || event.getTo() == null) return;
        DuelSession session = plugin.getDuelManager().getSession(event.getPlayer().getUniqueId());
        if (session == null) return;
        if (session.getPhase() == DuelSession.Phase.COUNTDOWN) {
            event.setTo(event.getFrom());
        } else if (!session.getArena().contains(event.getTo())) {
            event.setTo(session.getPlayerA().equals(event.getPlayer().getUniqueId())
                    ? session.getArena().getSpawnA() : session.getArena().getSpawnB());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        DuelSession session = plugin.getDuelManager().getSession(event.getPlayer().getUniqueId());
        Arena clickedArena = event.getClickedBlock() == null ? null
                : plugin.getArenaManager().getArenaAt(event.getClickedBlock().getLocation());
        if (plugin.getSpectatorManager().isSpectating(event.getPlayer().getUniqueId())
                || clickedArena != null && (session == null || session.getArena() != clickedArena
                || session.getPhase() != DuelSession.Phase.ACTIVE)
                || session != null && clickedArena == null && event.getClickedBlock() != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        protectExplosionBlocks(plugin.getArenaManager().getArenaAt(event.getLocation()), event.blockList());
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        protectExplosionBlocks(plugin.getArenaManager().getArenaAt(event.getBlock().getLocation()), event.blockList());
    }

    private void protectExplosionBlocks(Arena sourceArena, java.util.List<org.bukkit.block.Block> blocks) {
        blocks.removeIf(block -> {
            Arena affectedArena = plugin.getArenaManager().getArenaAt(block.getLocation());
            if (sourceArena != null) {
                return affectedArena != sourceArena || sourceArena.getState() != Arena.State.IN_USE;
            }
            return affectedArena != null;
        });
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (plugin.getSpectatorManager().isSpectating(event.getPlayer().getUniqueId())
                && !plugin.getConfigManager().allowSpectatorChat()) {
            event.setCancelled(true);
        }
    }
}
