/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.FluidCollisionMode
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.util.Vector
 */
package dev.mellow.antiesp;

import dev.mellow.antiesp.AntiESPConfig;
import dev.mellow.antiesp.AntiESPPlugin;
import dev.mellow.antiesp.PlayerState;
import dev.mellow.antiesp.Schedulers;
import dev.mellow.antiesp.WorldRule;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

final class EntityVisibility {
    private final AntiESPPlugin plugin;
    private int tick;

    EntityVisibility(AntiESPPlugin antiESPPlugin) {
        this.plugin = antiESPPlugin;
    }

    void enforceHidden(PlayerState playerState, int n, UUID uUID) {
        if (uUID == null) {
            return;
        }
        Player player = this.plugin.getServer().getPlayer(playerState.uuid);
        if (player == null) {
            return;
        }
        Schedulers.player((Plugin)this.plugin, player, () -> {
            Entity entity = this.lookup(uUID);
            if (entity == null || !playerState.isEntityHidden(n)) {
                return;
            }
            player.hideEntity((Plugin)this.plugin, entity);
        });
    }

    void tick() {
        AntiESPConfig antiESPConfig = this.plugin.config();
        if (!this.plugin.isActive() || !antiESPConfig.entitiesEnabled) {
            return;
        }
        int n = antiESPConfig.entityCheckInterval;
        int n2 = this.tick++ % n;
        int[] nArray = new int[]{antiESPConfig.losChecksPerTick};
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            PlayerState playerState;
            UUID uUID = player.getUniqueId();
            if (Math.floorMod(uUID.hashCode(), n) != n2 || (playerState = this.plugin.tracker().get(uUID)) == null) continue;
            if (!playerState.protectedWorld()) {
                if (playerState.hiddenEntities.isEmpty()) continue;
                Schedulers.player((Plugin)this.plugin, player, () -> this.revealAll(player, playerState));
                continue;
            }
            Schedulers.player((Plugin)this.plugin, player, () -> this.sweep(player, playerState, nArray));
        }
    }

    private void sweep(Player player, PlayerState playerState, int[] nArray) {
        AntiESPConfig antiESPConfig = this.plugin.config();
        WorldRule worldRule = playerState.rule;
        if (worldRule == null) {
            return;
        }
        Location location = player.getEyeLocation();
        World world = player.getWorld();
        double d = antiESPConfig.entityScanRadius;
        for (Entity entity : world.getNearbyEntities(location, d, d, d)) {
            int n;
            boolean bl;
            boolean bl2;
            if (entity.getUniqueId().equals(player.getUniqueId()) || antiESPConfig.ignoredTypes.contains(entity.getType()) || entity instanceof Player && !antiESPConfig.hidePlayers || (bl2 = this.shouldHide(antiESPConfig, worldRule, playerState, player, location, entity, bl = playerState.isEntityHidden(n = entity.getEntityId()), nArray)) == bl) continue;
            if (bl2) {
                playerState.markHidden(n, entity.getUniqueId());
                player.hideEntity((Plugin)this.plugin, entity);
                continue;
            }
            playerState.markVisible(n);
            player.showEntity((Plugin)this.plugin, entity);
        }
        this.prune(player, playerState);
    }

    private boolean shouldHide(AntiESPConfig antiESPConfig, WorldRule worldRule, PlayerState playerState, Player player, Location location, Entity entity, boolean bl, int[] nArray) {
        double d;
        Location location2 = entity.getLocation();
        if (playerState.hidden) {
            double d2 = d = bl ? (double)(worldRule.hideBelowY + antiESPConfig.entityGrace) : (double)worldRule.hideBelowY;
            if (location2.getY() < d) {
                return true;
            }
        }
        d = location.distanceSquared(location2);
        if (antiESPConfig.entityMaxDistance > 0.0 && d > antiESPConfig.entityMaxDistance * antiESPConfig.entityMaxDistance) {
            return true;
        }
        if (!antiESPConfig.losEnabled || d > antiESPConfig.losMaxDistance * antiESPConfig.losMaxDistance) {
            return false;
        }
        if (nArray[0] <= 0) {
            return bl;
        }
        nArray[0] = nArray[0] - 1;
        if (EntityVisibility.hasLineOfSight(location, entity)) {
            playerState.losFailures.remove(entity.getEntityId());
            return false;
        }
        int n = playerState.losFailures.merge(entity.getEntityId(), 1, Integer::sum);
        return n >= antiESPConfig.losFailsBeforeHiding;
    }

    private static boolean hasLineOfSight(Location location, Entity entity) {
        Location location2 = entity.getLocation();
        double d = Math.max(0.5, entity.getHeight());
        return EntityVisibility.clear(location, location2.getX(), location2.getY() + d * 0.5, location2.getZ()) || EntityVisibility.clear(location, location2.getX(), location2.getY() + d, location2.getZ());
    }

    private static boolean clear(Location location, double d, double d2, double d3) {
        Vector vector = new Vector(d - location.getX(), d2 - location.getY(), d3 - location.getZ());
        double d4 = vector.length();
        if (d4 < 1.0E-4) {
            return true;
        }
        return location.getWorld().rayTraceBlocks(location, vector.multiply(1.0 / d4), d4, FluidCollisionMode.NEVER, true) == null;
    }

    private void prune(Player player, PlayerState playerState) {
        if (playerState.hiddenEntityUuids.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Integer, UUID>> iterator = playerState.hiddenEntityUuids.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, UUID> entry = iterator.next();
            Entity entity = this.lookup(entry.getValue());
            if (entity != null && !entity.isDead() && entity.getWorld().equals((Object)player.getWorld())) continue;
            playerState.hiddenEntities.remove(entry.getKey());
            playerState.losFailures.remove(entry.getKey());
            iterator.remove();
        }
    }

    void revealAll(Player player, PlayerState playerState) {
        if (playerState.hiddenEntityUuids.isEmpty()) {
            playerState.clearEntities();
            return;
        }
        ArrayList<UUID> arrayList = new ArrayList<UUID>(playerState.hiddenEntityUuids.values());
        playerState.clearEntities();
        for (UUID uUID : arrayList) {
            Entity entity = this.lookup(uUID);
            if (entity == null) continue;
            player.showEntity((Plugin)this.plugin, entity);
        }
    }

    void revealAllNow() {
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            PlayerState playerState = this.plugin.tracker().get(player.getUniqueId());
            if (playerState == null || playerState.hiddenEntities.isEmpty()) continue;
            try {
                this.revealAll(player, playerState);
            }
            catch (Throwable throwable) {}
        }
    }

    void revealAll() {
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            PlayerState playerState = this.plugin.tracker().get(player.getUniqueId());
            if (playerState == null || playerState.hiddenEntities.isEmpty()) continue;
            Schedulers.player((Plugin)this.plugin, player, () -> this.revealAll(player, playerState));
        }
    }

    private Entity lookup(UUID uUID) {
        try {
            return this.plugin.getServer().getEntity(uUID);
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    int hiddenCount() {
        int n = 0;
        for (PlayerState playerState : this.plugin.tracker().all().values()) {
            n += playerState.hiddenEntities.size();
        }
        return n;
    }
}

