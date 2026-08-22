package com.based.itemesp.managers;

import com.based.itemesp.BasedItemEsp;
import com.based.itemesp.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core LOS + cache + periodic hide/show logic for dropped items and stacker holograms.
 */
public final class VisibilityManager {

    private static final double TARGET_Y_OFFSET = 0.2D;
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

    public static boolean isDroppedItemType(EntityType type) {
        if (type == null) {
            return false;
        }
        String name = type.name();
        return "DROPPED_ITEM".equals(name) || "ITEM".equals(name);
    }

    public static boolean isHologramType(EntityType type) {
        if (type == null) {
            return false;
        }
        String name = type.name();
        return "ARMOR_STAND".equals(name) || "TEXT_DISPLAY".equals(name);
    }

    /**
     * Stacker labels: named armor stands / text displays, especially near a dropped item.
     */
    public boolean isStackerHologram(Entity entity) {
        if (!config.isHideStackerHolograms() || entity == null || !entity.isValid()) {
            return false;
        }

        if (entity instanceof TextDisplay) {
            return isNearDroppedItem(entity, config.getHologramItemRadius())
                    || hasVisibleName(entity);
        }

        if (entity instanceof ArmorStand stand) {
            boolean named = stand.isCustomNameVisible() && stand.getCustomName() != null;
            boolean markerLike = stand.isMarker() || !stand.isVisible() || stand.isSmall();
            if (named || (markerLike && hasVisibleName(stand))) {
                return true;
            }
            return isNearDroppedItem(stand, config.getHologramItemRadius());
        }

        return false;
    }

    private static boolean hasVisibleName(Entity entity) {
        return entity.isCustomNameVisible() && entity.getCustomName() != null;
    }

    private boolean isNearDroppedItem(Entity entity, double radius) {
        for (Entity nearby : entity.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Item) {
                return true;
            }
        }
        return false;
    }

    public boolean hasLineOfSight(Player player, Location target) {
        if (player == null || target == null || target.getWorld() == null) {
            return false;
        }

        Location eye = player.getEyeLocation();
        if (eye.getWorld() == null || !eye.getWorld().equals(target.getWorld())) {
            return false;
        }

        Location aim = target.clone().add(0.0D, TARGET_Y_OFFSET, 0.0D);
        Vector delta = aim.toVector().subtract(eye.toVector());
        double distance = delta.length();
        double maxDistance = config.getMaxDistance();

        if (distance > maxDistance) {
            return false;
        }
        if (distance < 1.0E-4D) {
            return true;
        }

        Vector direction = delta.multiply(1.0D / distance);
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

    public boolean hasLineOfSight(Player player, Entity entity) {
        if (entity == null || !entity.isValid()) {
            return false;
        }
        return hasLineOfSight(player, entity.getLocation());
    }

    public boolean shouldReveal(Player player, Entity entity) {
        if (!config.isEnabled() || !config.isHideCompletely()) {
            return true;
        }
        if (player.hasPermission("itemesp.bypass")) {
            return true;
        }
        if (entity == null || !entity.isValid()) {
            return false;
        }

        PlayerData data = getOrCreate(player);
        int entityId = entity.getEntityId();
        long now = player.getWorld().getFullTime();
        int recheck = config.getRecheckTicks();

        PlayerData.CacheEntry cached = data.getCache(entityId);
        if (cached != null && (now - cached.checkedAtTick()) < recheck) {
            return cached.visible();
        }

        boolean visible = hasLineOfSight(player, entity);
        data.putCache(entityId, visible, now);
        config.debug("LOS " + player.getName() + " -> #" + entityId + " (" + entity.getType() + ") = " + visible);
        return visible;
    }

    public boolean shouldRevealLocation(Player player, int entityId, Location location) {
        if (!config.isEnabled() || !config.isHideCompletely()) {
            return true;
        }
        if (player.hasPermission("itemesp.bypass")) {
            return true;
        }

        PlayerData data = getOrCreate(player);
        long now = player.getWorld().getFullTime();
        int recheck = config.getRecheckTicks();

        PlayerData.CacheEntry cached = data.getCache(entityId);
        if (cached != null && (now - cached.checkedAtTick()) < recheck) {
            return cached.visible();
        }

        boolean visible = hasLineOfSight(player, location);
        data.putCache(entityId, visible, now);
        return visible;
    }

    public void applyVisibility(Player player, Entity entity) {
        if (!config.isEnabled() || !config.isHideCompletely()) {
            return;
        }
        if (player.hasPermission("itemesp.bypass")) {
            return;
        }
        if (entity == null || !entity.isValid()) {
            return;
        }

        boolean reveal = shouldReveal(player, entity);
        setClientVisibility(player, entity, reveal);

        if (entity instanceof Item) {
            syncNearbyHolograms(player, entity, reveal);
        }
    }

    private void syncNearbyHolograms(Player player, Entity item, boolean reveal) {
        if (!config.isHideStackerHolograms()) {
            return;
        }
        double r = config.getHologramItemRadius();
        for (Entity nearby : item.getNearbyEntities(r, r, r)) {
            if (isStackerHologram(nearby)) {
                setClientVisibility(player, nearby, reveal && shouldReveal(player, nearby));
            }
        }
    }

    public void setClientVisibility(Player player, Entity entity, boolean reveal) {
        PlayerData data = getOrCreate(player);
        int id = entity.getEntityId();
        boolean currentlyShown = data.isClientVisible(id);

        if (reveal && !currentlyShown) {
            player.showEntity(plugin, entity);
            data.setClientVisible(id, true);
            data.putCache(id, true, player.getWorld().getFullTime());
            config.debug("Show #" + id + " to " + player.getName());
        } else if (!reveal && currentlyShown) {
            player.hideEntity(plugin, entity);
            data.setClientVisible(id, false);
            data.putCache(id, false, player.getWorld().getFullTime());
            config.debug("Hide #" + id + " from " + player.getName());
        } else {
            data.setClientVisible(id, reveal);
            data.putCache(id, reveal, player.getWorld().getFullTime());
        }
    }

    public void markHidden(Player player, int entityId) {
        PlayerData data = getOrCreate(player);
        data.setClientVisible(entityId, false);
        long tick = 0L;
        try {
            if (Bukkit.isPrimaryThread() && player.getWorld() != null) {
                tick = player.getWorld().getFullTime();
            }
        } catch (Exception ignored) {
            // Called from netty thread — cache tick is best-effort.
        }
        data.putCache(entityId, false, tick);
    }

    public void markShown(Player player, int entityId) {
        PlayerData data = getOrCreate(player);
        data.setClientVisible(entityId, true);
        long tick = 0L;
        try {
            if (Bukkit.isPrimaryThread() && player.getWorld() != null) {
                tick = player.getWorld().getFullTime();
            }
        } catch (Exception ignored) {
            // ignore
        }
        data.putCache(entityId, true, tick);
    }

    /**
     * After a spawn packet was cancelled on the netty thread, resolve on the main
     * thread and show the entity if the player has LOS.
     */
    public void handleCancelledSpawn(Player player, int entityId, Location packetLocation, EntityType type) {
        if (!player.isOnline()) {
            return;
        }

        Entity entity = findEntityById(player, entityId);
        if (entity == null) {
            // Entity not tracked yet — if packet coords have no LOS, stay hidden.
            if (packetLocation != null && !shouldRevealLocation(player, entityId, packetLocation)) {
                markHidden(player, entityId);
            }
            return;
        }

        if (isDroppedItemType(type) || entity instanceof Item) {
            applyVisibility(player, entity);
            return;
        }

        if (config.isHideStackerHolograms() && (isHologramType(type) || isStackerHologram(entity))) {
            if (isStackerHologram(entity) || isNearDroppedItem(entity, config.getHologramItemRadius())) {
                applyVisibility(player, entity);
            } else {
                // Decorative armor stand — leave alone, undo cancel by showing.
                player.showEntity(plugin, entity);
                markShown(player, entityId);
            }
        }
    }

    private Entity findEntityById(Player player, int entityId) {
        // Prefer a local search — full world scans are too expensive.
        for (Entity entity : player.getNearbyEntities(
                config.getMaxDistance(), config.getMaxDistance(), config.getMaxDistance())) {
            if (entity.getEntityId() == entityId) {
                return entity;
            }
        }
        for (Entity entity : player.getWorld().getNearbyEntities(
                player.getLocation(),
                config.getMaxDistance(),
                config.getMaxDistance(),
                config.getMaxDistance())) {
            if (entity.getEntityId() == entityId) {
                return entity;
            }
        }
        return null;
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
                if (!entity.isValid()) {
                    continue;
                }

                if (entity instanceof Item) {
                    seen.add(entity.getEntityId());
                    applyVisibility(player, entity);
                } else if (config.isHideStackerHolograms() && isStackerHologram(entity)) {
                    seen.add(entity.getEntityId());
                    applyVisibility(player, entity);
                }
            }

            data.getClientVisible().keySet().removeIf(id -> !seen.contains(id));
            data.getVisibilityCache().keySet().removeIf(id -> !seen.contains(id));
        }
    }
}
