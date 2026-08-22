/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState
 *  io.github.retrooper.packetevents.util.SpigotConversionUtil
 *  org.bukkit.Material
 *  org.bukkit.World
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.EntityType
 */
package dev.mellow.antiesp;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import dev.mellow.antiesp.TargetSet;
import dev.mellow.antiesp.WorldRule;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

final class AntiESPConfig {
    final boolean enabled;
    final String bypassPermission;
    final boolean debug;
    final boolean blocksEnabled;
    final Material fakeMaterial;
    final int fakeStateId;
    final int hideBelowY;
    final int revealMargin;
    final int hideMargin;
    final int revealRadius;
    final boolean hideBlockEntities;
    final boolean hideBlockUpdates;
    final boolean entitiesEnabled;
    final int entityCheckInterval;
    final int entityScanRadius;
    final int entityGrace;
    final double entityMaxDistance;
    final boolean hidePlayers;
    final Set<EntityType> ignoredTypes;
    final boolean targetsEnabled;
    final Set<Material> targetMaterials;
    final List<String> targetBlockEntityNames;
    final Material targetFakeMaterial;
    final Material targetDeepFakeMaterial;
    final int targetDeepBelowY;
    final double targetRevealDistance;
    final int targetCheckInterval;
    final int targetReassertInterval;
    final int targetRaycastsPerTick;
    final boolean targetHideNbt;
    final Set<Material> scanMaterials;
    final int scanMinY;
    final int scanMaxY;
    final double scanRevealDistance;
    final int scanChunksPerSweep;
    final long scanCacheMillis;
    final boolean losEnabled;
    final double losMaxDistance;
    final int losChecksPerTick;
    final int losFailsBeforeHiding;
    final int refreshRadius;
    final int refreshesPerTick;
    final int maxQueuedChunks;
    final boolean allWorlds;
    final Set<String> worlds;
    final Map<String, Integer> worldOverrides;

    private AntiESPConfig(FileConfiguration fileConfiguration, Logger logger) {
        String string3;
        this.enabled = fileConfiguration.getBoolean("enabled", true);
        this.bypassPermission = fileConfiguration.getString("bypass-permission", "antiesp.bypass");
        this.debug = fileConfiguration.getBoolean("debug", false);
        this.blocksEnabled = fileConfiguration.getBoolean("blocks.enabled", true);
        this.fakeMaterial = AntiESPConfig.block(fileConfiguration.getString("blocks.fake-block-material", "STONE"), Material.STONE, "blocks.fake-block-material", logger);
        this.fakeStateId = AntiESPConfig.resolveStateId(this.fakeMaterial);
        this.hideBelowY = fileConfiguration.getInt("blocks.hide-below-y", 0);
        this.revealMargin = Math.max(0, fileConfiguration.getInt("blocks.reveal-margin", 12));
        this.hideMargin = Math.max(this.revealMargin + 1, fileConfiguration.getInt("blocks.hide-margin", 32));
        this.revealRadius = fileConfiguration.getInt("blocks.reveal-radius", -1);
        this.hideBlockEntities = fileConfiguration.getBoolean("blocks.hide-block-entities", true);
        this.hideBlockUpdates = fileConfiguration.getBoolean("blocks.hide-block-updates", true);
        this.entitiesEnabled = fileConfiguration.getBoolean("entities.enabled", true);
        this.entityCheckInterval = Math.max(1, fileConfiguration.getInt("entities.check-interval-ticks", 10));
        this.entityScanRadius = Math.max(16, fileConfiguration.getInt("entities.scan-radius", 96));
        this.entityGrace = Math.max(0, fileConfiguration.getInt("entities.grace-blocks", 2));
        double d = fileConfiguration.getDouble("entities.max-distance", -1.0);
        this.entityMaxDistance = d <= 0.0 ? -1.0 : d;
        this.hidePlayers = fileConfiguration.getBoolean("entities.hide-players", true);
        EnumSet<EntityType> enumSet = EnumSet.noneOf(EntityType.class);
        for (String string2 : fileConfiguration.getStringList("entities.ignored-types")) {
            try {
                enumSet.add(EntityType.valueOf((String)string2.toUpperCase(Locale.ROOT)));
            }
            catch (IllegalArgumentException illegalArgumentException) {
                logger.warning("entities.ignored-types: unknown entity type '" + string2 + "'");
            }
        }
        this.ignoredTypes = enumSet;
        this.targetsEnabled = fileConfiguration.getBoolean("tile-entities.enabled", true);
        this.targetMaterials = TargetSet.parseMaterials(fileConfiguration.getStringList("tile-entities.materials"), "tile-entities.materials", logger);
        this.scanMaterials = TargetSet.parseMaterials(fileConfiguration.getStringList("tile-entities.scan-materials"), "tile-entities.scan-materials", logger);
        this.scanMinY = fileConfiguration.getInt("tile-entities.scan-min-y", -64);
        this.scanMaxY = Math.max(this.scanMinY, fileConfiguration.getInt("tile-entities.scan-max-y", 48));
        this.scanRevealDistance = Math.max(4.0, fileConfiguration.getDouble("tile-entities.scan-reveal-distance", 24.0));
        this.scanChunksPerSweep = Math.max(1, fileConfiguration.getInt("tile-entities.scan-chunks-per-sweep", 4));
        this.scanCacheMillis = Math.max(0L, fileConfiguration.getLong("tile-entities.scan-cache-seconds", 300L)) * 1000L;
        this.targetBlockEntityNames = fileConfiguration.getStringList("tile-entities.block-entity-types");
        this.targetFakeMaterial = AntiESPConfig.block(fileConfiguration.getString("tile-entities.fake-block-material", "STONE"), Material.STONE, "tile-entities.fake-block-material", logger);
        this.targetDeepFakeMaterial = AntiESPConfig.block(fileConfiguration.getString("tile-entities.deep-fake-block-material", "DEEPSLATE"), Material.DEEPSLATE, "tile-entities.deep-fake-block-material", logger);
        this.targetDeepBelowY = fileConfiguration.getInt("tile-entities.deep-below-y", 0);
        this.targetRevealDistance = Math.max(4.0, fileConfiguration.getDouble("tile-entities.reveal-distance", 48.0));
        this.targetCheckInterval = Math.max(1, fileConfiguration.getInt("tile-entities.check-interval-ticks", 10));
        this.targetReassertInterval = Math.max(0, fileConfiguration.getInt("tile-entities.reassert-interval-ticks", 40));
        this.targetRaycastsPerTick = Math.max(16, fileConfiguration.getInt("tile-entities.max-raycasts-per-tick", 1200));
        this.targetHideNbt = fileConfiguration.getBoolean("tile-entities.hide-block-entity-nbt", true);
        this.losEnabled = fileConfiguration.getBoolean("entities.line-of-sight.enabled", true);
        this.losMaxDistance = Math.max(8.0, fileConfiguration.getDouble("entities.line-of-sight.max-distance", 64.0));
        this.losChecksPerTick = Math.max(1, fileConfiguration.getInt("entities.line-of-sight.max-checks-per-tick", 400));
        this.losFailsBeforeHiding = Math.max(1, fileConfiguration.getInt("entities.line-of-sight.fails-before-hiding", 3));
        this.refreshRadius = Math.max(1, fileConfiguration.getInt("performance.refresh-radius", 12));
        this.refreshesPerTick = Math.max(1, fileConfiguration.getInt("performance.chunk-refreshes-per-tick", 16));
        this.maxQueuedChunks = Math.max(256, fileConfiguration.getInt("performance.max-queued-chunks", 20000));
        HashSet hashSet = new HashSet();
        boolean bl = false;
        for (String string3 : fileConfiguration.getStringList("worlds")) {
            if ("*".equals(string3)) {
                bl = true;
                continue;
            }
            hashSet.add(string3.toLowerCase(Locale.ROOT));
        }
        this.allWorlds = bl;
        this.worlds = hashSet;
        HashMap hashMap = new HashMap();
        string3 = fileConfiguration.getConfigurationSection("world-overrides");
        if (string3 != null) {
            for (String string4 : string3.getKeys(false)) {
                hashMap.put(string4.toLowerCase(Locale.ROOT), string3.getInt(string4));
            }
        }
        this.worldOverrides = hashMap;
    }

    static AntiESPConfig load(FileConfiguration fileConfiguration, Logger logger) {
        return new AntiESPConfig(fileConfiguration, logger);
    }

    private static Material block(String string, Material material, String string2, Logger logger) {
        Material material2;
        Material material3 = material2 = string == null ? null : Material.matchMaterial((String)string);
        if (material2 == null || !material2.isBlock()) {
            logger.warning(string2 + " '" + string + "' is not a block, using " + String.valueOf(material));
            return material;
        }
        return material2;
    }

    private static int resolveStateId(Material material) {
        WrappedBlockState wrappedBlockState = SpigotConversionUtil.fromBukkitBlockData((BlockData)material.createBlockData());
        return wrappedBlockState.getGlobalId();
    }

    WorldRule ruleFor(World world) {
        int n;
        if (!this.enabled || world == null) {
            return null;
        }
        String string = world.getName().toLowerCase(Locale.ROOT);
        if (!this.allWorlds && !this.worlds.contains(string)) {
            return null;
        }
        int n2 = this.worldOverrides.getOrDefault(string, this.hideBelowY);
        if (n2 <= (n = world.getMinHeight())) {
            return null;
        }
        return new WorldRule(n2, n, this.fakeStateId);
    }
}

