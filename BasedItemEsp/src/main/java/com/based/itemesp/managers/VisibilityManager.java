package com.based.itemesp.managers;

import com.based.itemesp.BasedItemEsp;
import com.based.itemesp.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core LOS + cache + periodic hide/show logic.
 * <p>
 * SPAWN_ENTITY cancel alone is not enough: players move, so visibility must be
 * revalidated on a tick interval and applied with {@link Player#hideEntity} /
 * {@link Player#showEntity}.
 */
public final class VisibilityManager {

    private static final double ITEM_CENTER_Y_OFFSET = 0.2D;
    private static final double RAY_END_EPSILON = 0.35D;

    private final BasedItemEsp plugin;
    private final ConfigManager config;
    private final ConcurrentHashMap<UUID, PlayerData> playerData = new ConcurrentHashMap<>();

    private BukkitTask recheckTask;

    public VisibilityManager(BasedItemEsp plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        stopTask();
        int interval = config.getRecheckTicks();
        recheckTask = Bukkit.getScheduler().runTaskTimer(plugin, this::recheckAllPlayers, interval, interval);
        config.debug("Visibility recheck task started (every " + interval + " ticks).");
    }

    public void shutdown() {
        stopTask();
        // Paper clears per-plugin hideEntity state on disable; only drop our caches.
        playerData.clear();
        config.debug("VisibilityManager shut down; player data cleared.");
    }

    private void stopTask() {
        if (recheckTask != null) {
            recheckTask.cancel();
            recheckTask = null;
        }
    }

    public PlayerData getOrCreate(Player player) {
        return playerData.computeIfAbsent(player.getUniqueId(), PlayerData::new);
    }

    public void removePlayer(UUID uuid) {
        PlayerData data = playerData.remove(uuid);
        if (data != null) {
            data.clear();
        }
    }

    public void clearAll() {
        playerData.clear();
    }

    /**
     * Accurate block LOS check from the player's eye to the item.
     */
    public boolean hasLineOfSight(Player player, Item item) {
        if (player == null || item == null || !item.isValid()) {
            return false;
        }

        Location eye = player.getEyeLocation();
        Location itemLoc = item.getLocation().clone().add(0.0D, ITEM_CENTER_Y_OFFSET, 0.0D);

        if (eye.getWorld() == null || itemLoc.getWorld() == null
                || !eye.getWorld().equals(itemLoc.getWorld())) {
            return false;
        }

        Vector delta = itemLoc.toVector().subtract(eye.toVector());
        double distance = delta.length();
        double maxDistance = config.getMaxDistance();

        if (distance > maxDistance) {
            return false;
        }
        if (distance < 1.0E-4D) {
            return true;
        }

        Vector direction = delta.multiply(1.0D / distance);
        // Stop slightly before the item so floor/wall collision at the resting spot
        // does not falsely block LOS for items sitting on blocks.
        double traceDistance = Math.max(0.0D, distance - RAY_END_EPSILON);

        if (traceDistance <= 0.0D) {
            return true;
        }

        RayTraceResult hit = eye.getWorld().rayTraceBlocks(
                eye,
                direction,
                traceDistance,
                FluidCollisionMode.NEVER,
                true
        );

        return hit == null;
    }

    /**
     * Cached visibility decision. Re-raycasts only when the cache is older than
     * {@code recheck-ticks}.
     */
    public boolean shouldRevealItem(Player player, Item item) {
        if (!config.isEnabled() || !config.isHideCompletely()) {
            return true;
        }
        if (player.hasPermission("itemesp.bypass")) {
            return true;
        }
        if (item == null || !item.isValid()) {
            return false;
        }

        PlayerData data = getOrCreate(player);
        int entityId = item.getEntityId();
        long now = player.getWorld().getFullTime();
        int recheck = config.getRecheckTicks();

        PlayerData.CacheEntry cached = data.getCache(entityId);
        if (cached != null && (now - cached.checkedAtTick()) < recheck) {
            return cached.visible();
        }

        boolean visible = hasLineOfSight(player, item);
        data.putCache(entityId, visible, now);
        config.debug("LOS " + player.getName() + " -> item#" + entityId + " = " + visible);
        return visible;
    }

    /**
     * Apply hide/show for one player-item pair based on {@link #shouldRevealItem}.
     */
    public void applyVisibility(Player player, Item item) {
        if (!config.isEnabled() || !config.isHideCompletely()) {
            return;
        }
        if (player.hasPermission("itemesp.bypass")) {
            return;
        }

        boolean reveal = shouldRevealItem(player, item);
        PlayerData data = getOrCreate(player);
        int id = item.getEntityId();
        boolean currentlyShown = data.isClientVisible(id);

        if (reveal && !currentlyShown) {
            player.showEntity(plugin, item);
            data.setClientVisible(id, true);
            config.debug("Show item#" + id + " to " + player.getName());
        } else if (!reveal && currentlyShown) {
            player.hideEntity(plugin, item);
            data.setClientVisible(id, false);
            config.debug("Hide item#" + id + " from " + player.getName());
        } else if (!reveal && !currentlyShown) {
            // Ensure Paper tracking matches (e.g. spawn was cancelled).
            data.setClientVisible(id, false);
        } else {
            data.setClientVisible(id, true);
        }
    }

    /**
     * Called from the packet listener when a spawn is cancelled (never shown).
     */
    public void markHidden(Player player, Item item) {
        PlayerData data = getOrCreate(player);
        data.setClientVisible(item.getEntityId(), false);
        long now = player.getWorld().getFullTime();
        data.putCache(item.getEntityId(), false, now);
    }

    /**
     * Called when a spawn is allowed through.
     */
    public void markShown(Player player, Item item) {
        PlayerData data = getOrCreate(player);
        data.setClientVisible(item.getEntityId(), true);
        long now = player.getWorld().getFullTime();
        data.putCache(item.getEntityId(), true, now);
    }

    private void recheckAllPlayers() {
        if (!config.isEnabled() || !config.isHideCompletely()) {
            return;
        }

        double range = config.getMaxDistance();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("itemesp.bypass")) {
                continue;
            }

            PlayerData data = getOrCreate(player);
            Set<Integer> seen = new HashSet<>();

            for (Entity entity : player.getNearbyEntities(range, range, range)) {
                if (entity instanceof Item item && item.isValid()) {
                    seen.add(item.getEntityId());
                    // shouldRevealItem already respects recheck-ticks vs world time.
                    applyVisibility(player, item);
                }
            }

            // Drop cache for items that left range or despawned.
            data.getClientVisible().keySet().removeIf(id -> !seen.contains(id));
            data.getVisibilityCache().keySet().removeIf(id -> !seen.contains(id));
        }
    }
}
