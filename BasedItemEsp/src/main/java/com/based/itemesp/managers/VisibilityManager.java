package com.based.itemesp.managers;

import com.based.itemesp.BasedItemEsp;
import com.based.itemesp.data.PlayerData;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hides EVERY dropped item (any material) without LOS — no bypass.
 * Full world height. Hard-hides with hideEntity + ENTITY_DESTROY.
 */
public final class VisibilityManager {

    private static final double TARGET_Y_OFFSET = 0.15D;
    private static final double RAY_END_EPSILON = 0.2D;
    private static final double MOVE_INVALIDATE_SQ = 0.25D; // 0.5 blocks

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

    public static double fullWorldHeightRadius(World world) {
        if (world == null) {
            return 512.0D;
        }
        return Math.max(64.0D, (world.getMaxHeight() - world.getMinHeight()) + 16.0D);
    }

    public boolean isStackerHologram(Entity entity) {
        if (!config.isHideStackerHolograms() || entity == null || !entity.isValid()) {
            return false;
        }

        if (entity instanceof TextDisplay) {
            return isNearDroppedItem(entity, config.getHologramItemRadius()) || hasVisibleName(entity);
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

    /**
     * Strict LOS: rayTraceBlocks AND BlockIterator must both clear.
     * On any error → hidden (fail closed).
     */
    public boolean hasLineOfSight(Player player, Location target) {
        try {
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

            if (!config.isUnlimitedDistance() && distance > config.getMaxDistance()) {
                return false;
            }
            // Even when "unlimited", don't raycast absurd lengths
            double hardCap = config.getHorizontalScanRadius() * 2.0D;
            if (distance > hardCap) {
                return false;
            }
            if (distance < 1.0E-4D) {
                return true;
            }

            Vector direction = delta.clone().multiply(1.0D / distance);
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
            if (hit != null) {
                return false;
            }

            // Backup solid-block walk (catches corner/edge cases rayTrace can miss)
            int blockDist = Math.max(1, (int) Math.ceil(traceDistance));
            BlockIterator iterator = new BlockIterator(eye.getWorld(), eye.toVector(), direction, 0.0D, blockDist);
            while (iterator.hasNext()) {
                Block block = iterator.next();
                if (block.getType().isSolid() && !block.getType().isTransparent()) {
                    // Ignore the block extremely close to the item resting spot
                    if (block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(aim) < 0.6D) {
                        continue;
                    }
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            config.debug("LOS error (fail closed): " + ex.getMessage());
            return false;
        }
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
        if (entity == null || !entity.isValid()) {
            return false;
        }

        PlayerData data = getOrCreate(player);
        int entityId = entity.getEntityId();
        Location loc = entity.getLocation();
        long now = player.getWorld().getFullTime();
        int recheck = config.getRecheckTicks();

        PlayerData.CacheEntry cached = data.getCache(entityId);
        if (cached != null
                && (now - cached.checkedAtTick()) < recheck
                && !cached.movedFrom(loc.getX(), loc.getY(), loc.getZ(), MOVE_INVALIDATE_SQ)) {
            return cached.visible();
        }

        boolean visible = hasLineOfSight(player, entity);
        data.putCache(entityId, visible, now, loc.getX(), loc.getY(), loc.getZ());
        config.debug("LOS " + player.getName() + " -> #" + entityId + " (" + entity.getType() + ") = " + visible);
        return visible;
    }

    public boolean shouldRevealLocation(Player player, int entityId, Location location) {
        if (!config.isEnabled() || !config.isHideCompletely()) {
            return true;
        }
        if (location == null) {
            return false;
        }

        PlayerData data = getOrCreate(player);
        long now = player.getWorld().getFullTime();
        int recheck = config.getRecheckTicks();

        PlayerData.CacheEntry cached = data.getCache(entityId);
        if (cached != null
                && (now - cached.checkedAtTick()) < recheck
                && !cached.movedFrom(location.getX(), location.getY(), location.getZ(), MOVE_INVALIDATE_SQ)) {
            return cached.visible();
        }

        boolean visible = hasLineOfSight(player, location);
        data.putCache(entityId, visible, now, location.getX(), location.getY(), location.getZ());
        return visible;
    }

    public void applyVisibility(Player player, Entity entity) {
        if (!config.isEnabled() || !config.isHideCompletely()) {
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
        Location loc = entity.getLocation();
        long now = player.getWorld().getFullTime();

        if (reveal) {
            player.showEntity(plugin, entity);
            data.setClientVisible(id, true);
            data.putCache(id, true, now, loc.getX(), loc.getY(), loc.getZ());
            config.debug("Show #" + id + " to " + player.getName());
        } else {
            // ALWAYS hard-hide — do not trust clientVisible tracking alone
            hardHide(player, entity);
            data.setClientVisible(id, false);
            data.putCache(id, false, now, loc.getX(), loc.getY(), loc.getZ());
            config.debug("Hard-hide #" + id + " from " + player.getName());
        }
    }

    /**
     * Paper hideEntity + ProtocolLib ENTITY_DESTROY so ESP clients drop it.
     */
    public void hardHide(Player player, Entity entity) {
        try {
            player.hideEntity(plugin, entity);
        } catch (Exception ignored) {
            // ignore
        }
        sendDestroyPacket(player, entity.getEntityId());
    }

    public void sendDestroyPacket(Player player, int entityId) {
        try {
            ProtocolManager manager = ProtocolLibrary.getProtocolManager();
            PacketContainer destroy = manager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroy.getIntLists().write(0, Collections.singletonList(entityId));
            manager.sendServerPacket(player, destroy, false);
        } catch (Exception ex) {
            config.debug("Destroy packet failed for #" + entityId + ": " + ex.getMessage());
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
            // netty thread
        }
        data.putCache(entityId, false, tick);
        sendDestroyPacket(player, entityId);
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

    public void handleCancelledSpawn(Player player, int entityId, Location packetLocation, EntityType type) {
        if (!player.isOnline()) {
            return;
        }

        Entity entity = findEntityById(player, entityId, packetLocation);

        // Default: stay hidden. Only reveal with confirmed entity + LOS.
        if (entity == null) {
            markHidden(player, entityId);
            if (packetLocation != null && shouldRevealLocation(player, entityId, packetLocation)) {
                // Can't show without entity handle — wait for recheck loop
                config.debug("Spawn #" + entityId + " has LOS but entity not resolved yet");
            }
            return;
        }

        if (isDroppedItemType(type) || entity instanceof Item) {
            // Every item type (glow ink sac, grass, etc.) — same rules
            applyVisibility(player, entity);
            return;
        }

        if (config.isHideStackerHolograms() && (isHologramType(type) || isStackerHologram(entity))) {
            if (isStackerHologram(entity) || isNearDroppedItem(entity, config.getHologramItemRadius())) {
                applyVisibility(player, entity);
            } else {
                // Non-stacker armor stand: still require LOS (no free wallhack via stands)
                applyVisibility(player, entity);
            }
        }
    }

    private Entity findEntityById(Player player, int entityId, Location hint) {
        World world = player.getWorld();
        // Fast path: all items in world
        for (Item item : world.getEntitiesByClass(Item.class)) {
            if (item.getEntityId() == entityId) {
                return item;
            }
        }

        double horizontal = config.getHorizontalScanRadius();
        double vertical = fullWorldHeightRadius(world);
        Location center = hint != null ? hint : player.getLocation();
        for (Entity entity : world.getNearbyEntities(center, horizontal, vertical, horizontal)) {
            if (entity.getEntityId() == entityId) {
                return entity;
            }
        }
        return null;
    }

    /**
     * Process EVERY loaded dropped item in the world for each player (within scan radius).
     */
    private void recheckAllPlayers() {
        if (!config.isEnabled() || !config.isHideCompletely()) {
            return;
        }

        double horizontal = config.getHorizontalScanRadius();
        double horizontalSq = horizontal * horizontal;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) {
                continue;
            }

            PlayerData data = getOrCreate(player);
            Set<Integer> seen = new HashSet<>();
            World world = player.getWorld();
            Location origin = player.getLocation();

            // EVERY Item entity — any material
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (!item.isValid()) {
                    continue;
                }
                if (origin.distanceSquared(item.getLocation()) > horizontalSq) {
                    // Out of scan window: ensure destroyed on client if we had tracked it
                    if (data.isClientVisible(item.getEntityId()) || data.getCache(item.getEntityId()) != null) {
                        hardHide(player, item);
                        data.setClientVisible(item.getEntityId(), false);
                    }
                    continue;
                }
                seen.add(item.getEntityId());
                applyVisibility(player, item);
            }

            if (config.isHideStackerHolograms()) {
                double vertical = fullWorldHeightRadius(world);
                for (Entity entity : player.getNearbyEntities(horizontal, vertical, horizontal)) {
                    if (!entity.isValid() || !isStackerHologram(entity)) {
                        continue;
                    }
                    seen.add(entity.getEntityId());
                    applyVisibility(player, entity);
                }
            }

            // Anything we tracked but didn't see this pass → hard hide leftover IDs
            for (Integer id : new HashSet<>(data.getClientVisible().keySet())) {
                if (!seen.contains(id) && data.isClientVisible(id)) {
                    sendDestroyPacket(player, id);
                    data.setClientVisible(id, false);
                }
            }

            data.getClientVisible().keySet().removeIf(id -> !seen.contains(id));
            data.getVisibilityCache().keySet().removeIf(id -> !seen.contains(id));
        }
    }
}
