/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Chunk
 *  org.bukkit.FluidCollisionMode
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.block.BlockState
 *  org.bukkit.block.TileState
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.util.RayTraceResult
 *  org.bukkit.util.Vector
 */
package dev.mellow.antiesp;

import dev.mellow.antiesp.AntiESPConfig;
import dev.mellow.antiesp.AntiESPPlugin;
import dev.mellow.antiesp.BlockKey;
import dev.mellow.antiesp.PlayerState;
import dev.mellow.antiesp.Schedulers;
import dev.mellow.antiesp.TargetSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

final class TargetRevealer {
    private static final int SWEEP_BUDGET_PER_PLAYER = 512;
    private final AntiESPPlugin plugin;
    private int tick;

    TargetRevealer(AntiESPPlugin antiESPPlugin) {
        this.plugin = antiESPPlugin;
    }

    void tick() {
        AntiESPConfig antiESPConfig = this.plugin.config();
        if (!this.plugin.isActive() || !antiESPConfig.targetsEnabled || this.plugin.targets().isEmpty()) {
            return;
        }
        int n = antiESPConfig.targetCheckInterval;
        int n2 = this.tick % n;
        boolean bl = antiESPConfig.targetReassertInterval > 0 && this.tick % antiESPConfig.targetReassertInterval == 0;
        ++this.tick;
        int[] nArray = new int[]{antiESPConfig.targetRaycastsPerTick};
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            PlayerState playerState = this.plugin.tracker().get(player.getUniqueId());
            if (playerState == null) continue;
            if (!playerState.protectedWorld()) {
                if (playerState.revealed.isEmpty()) continue;
                playerState.revealed.clear();
                continue;
            }
            boolean bl2 = Math.floorMod(player.getUniqueId().hashCode(), n) == n2;
            boolean bl3 = bl2;
            if (!bl2 && !bl) continue;
            Schedulers.player((Plugin)this.plugin, player, () -> {
                if (bl2) {
                    this.sweep(player, playerState, nArray);
                }
                if (bl) {
                    this.reassert(player, playerState);
                }
            });
        }
    }

    private void sweep(Player player, PlayerState playerState, int[] nArray) {
        int n;
        int n2;
        int n3;
        AntiESPConfig antiESPConfig = this.plugin.config();
        TargetSet targetSet = this.plugin.targets();
        Location location = player.getEyeLocation();
        World world = player.getWorld();
        double d = antiESPConfig.targetRevealDistance;
        double d2 = d * d;
        int n4 = (int)Math.ceil(d / 16.0);
        int n5 = location.getBlockX() >> 4;
        int n6 = location.getBlockZ() >> 4;
        double d3 = Math.min(d, antiESPConfig.scanRevealDistance);
        double d4 = d3 * d3;
        int n7 = (int)Math.ceil(d3 / 16.0);
        boolean bl = targetSet.hasScanMaterials();
        ArrayList<Block> arrayList = new ArrayList<Block>();
        ArrayList<Block> arrayList2 = new ArrayList<Block>();
        int[] nArray2 = new int[]{0};
        int[] nArray3 = new int[]{bl ? antiESPConfig.scanChunksPerSweep : 0};
        block2: for (n3 = -n4; n3 <= n4; ++n3) {
            for (n2 = -n4; n2 <= n4; ++n2) {
                Collection collection;
                n = n5 + n3;
                int n8 = n6 + n2;
                try {
                    if (!world.isChunkLoaded(n, n8)) continue;
                    Chunk chunk = world.getChunkAt(n, n8);
                    collection = chunk.getTileEntities(block -> targetSet.isTargetMaterial(block.getType()), false);
                }
                catch (Throwable throwable) {
                    continue;
                }
                for (Object e : collection) {
                    BlockState blockState = (BlockState)e;
                    int n9 = nArray2[0];
                    nArray2[0] = n9 + 1;
                    if (n9 > 512) break block2;
                    this.consider(blockState.getBlock(), playerState, location, d2, nArray, arrayList, arrayList2);
                }
            }
        }
        if (bl && nArray[0] > 0 && nArray2[0] <= 512) {
            block5: for (n3 = -n7; n3 <= n7; ++n3) {
                for (n2 = -n7; n2 <= n7; ++n2) {
                    int n10 = n5 + n3;
                    n = n6 + n2;
                    if (!world.isChunkLoaded(n10, n)) continue;
                    for (long l : this.plugin.scanIndex().positions(world, n10, n, nArray3)) {
                        int n11 = nArray2[0];
                        nArray2[0] = n11 + 1;
                        if (n11 > 512 || nArray[0] <= 0) break block5;
                        Block block2 = world.getBlockAt(BlockKey.x(l), BlockKey.y(l), BlockKey.z(l));
                        if (!targetSet.isScanMaterial(block2.getType())) continue;
                        this.consider(block2, playerState, location, d4, nArray, arrayList, arrayList2);
                    }
                }
            }
        }
        for (Block block3 : arrayList) {
            playerState.revealed.add(BlockKey.of(block3.getX(), block3.getY(), block3.getZ()));
            this.sendReal(player, block3);
        }
        for (Block block4 : arrayList2) {
            playerState.revealed.remove(BlockKey.of(block4.getX(), block4.getY(), block4.getZ()));
            this.sendFake(player, block4.getLocation());
        }
    }

    private void consider(Block block, PlayerState playerState, Location location, double d, int[] nArray, List<Block> list, List<Block> list2) {
        long l = BlockKey.of(block.getX(), block.getY(), block.getZ());
        boolean bl = playerState.revealed.contains(l);
        Location location2 = block.getLocation().add(0.5, 0.5, 0.5);
        if (location.distanceSquared(location2) > d) {
            if (bl) {
                list2.add(block);
            }
            return;
        }
        if (nArray[0] <= 0) {
            return;
        }
        nArray[0] = nArray[0] - 7;
        boolean bl2 = this.hasLineOfSight(location, block);
        if (bl2 && !bl) {
            list.add(block);
        } else if (!bl2 && bl) {
            list2.add(block);
        }
    }

    private void reassert(Player player, PlayerState playerState) {
        if (playerState.revealed.isEmpty()) {
            return;
        }
        World world = player.getWorld();
        TargetSet targetSet = this.plugin.targets();
        int n = player.getClientViewDistance() + 1 << 4;
        long l = (long)n * (long)n;
        Location location = player.getLocation();
        for (Long l2 : playerState.revealed) {
            double d;
            double d2;
            int n2 = BlockKey.x(l2);
            int n3 = BlockKey.y(l2);
            int n4 = BlockKey.z(l2);
            double d3 = (double)n2 - location.getX();
            if (d3 * d3 + d2 * (d = (double)n4 - location.getZ()) > (double)l) {
                playerState.revealed.remove(l2);
                continue;
            }
            if (!world.isChunkLoaded(n2 >> 4, n4 >> 4)) continue;
            Block block = world.getBlockAt(n2, n3, n4);
            if (!targetSet.isTargetMaterial(block.getType())) {
                playerState.revealed.remove(l2);
                continue;
            }
            this.sendReal(player, block);
        }
    }

    private boolean hasLineOfSight(Location location, Block block) {
        double d;
        double d2;
        double d3 = (double)block.getX() + 0.5;
        if (TargetRevealer.clear(location, d3, d2 = (double)block.getY() + 0.5, d = (double)block.getZ() + 0.5, block)) {
            return true;
        }
        return TargetRevealer.clear(location, d3, d2 + 0.5, d, block) || TargetRevealer.clear(location, d3, d2 - 0.5, d, block) || TargetRevealer.clear(location, d3 + 0.5, d2, d, block) || TargetRevealer.clear(location, d3 - 0.5, d2, d, block) || TargetRevealer.clear(location, d3, d2, d + 0.5, block) || TargetRevealer.clear(location, d3, d2, d - 0.5, block);
    }

    private static boolean clear(Location location, double d, double d2, double d3, Block block) {
        Vector vector = new Vector(d - location.getX(), d2 - location.getY(), d3 - location.getZ());
        double d4 = vector.length();
        if (d4 < 1.0E-4) {
            return true;
        }
        RayTraceResult rayTraceResult = location.getWorld().rayTraceBlocks(location, vector.multiply(1.0 / d4), d4, FluidCollisionMode.NEVER, true);
        return rayTraceResult == null || rayTraceResult.getHitBlock() == null || block.equals((Object)rayTraceResult.getHitBlock());
    }

    private void sendReal(Player player, Block block) {
        player.sendBlockChange(block.getLocation(), block.getBlockData());
        if (!this.plugin.config().targetHideNbt) {
            return;
        }
        try {
            BlockState blockState = block.getState();
            if (blockState instanceof TileState) {
                player.sendBlockUpdate(block.getLocation(), (TileState)blockState);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private void sendFake(Player player, Location location) {
        player.sendBlockChange(location, this.plugin.targets().fakeDataFor(location.getBlockY()));
    }

    void clear(PlayerState playerState) {
        playerState.revealed.clear();
    }

    int revealedCount() {
        int n = 0;
        for (PlayerState playerState : this.plugin.tracker().all().values()) {
            n += playerState.revealed.size();
        }
        return n;
    }
}

