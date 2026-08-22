/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChunkSnapshot
 *  org.bukkit.Material
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.event.block.BlockFormEvent
 *  org.bukkit.event.block.BlockGrowEvent
 *  org.bukkit.event.block.BlockPlaceEvent
 *  org.bukkit.event.world.ChunkUnloadEvent
 *  org.bukkit.plugin.Plugin
 */
package dev.mellow.antiesp;

import dev.mellow.antiesp.AntiESPPlugin;
import dev.mellow.antiesp.BlockKey;
import dev.mellow.antiesp.ChunkRefresher;
import dev.mellow.antiesp.Schedulers;
import dev.mellow.antiesp.TargetSet;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;

final class ScanIndex
implements Listener {
    private static final long[] EMPTY = new long[0];
    private final AntiESPPlugin plugin;
    private final Map<UUID, Map<Long, Entry>> worlds = new ConcurrentHashMap<UUID, Map<Long, Entry>>();

    ScanIndex(AntiESPPlugin antiESPPlugin) {
        this.plugin = antiESPPlugin;
    }

    private Map<Long, Entry> world(UUID uUID2) {
        return this.worlds.computeIfAbsent(uUID2, uUID -> new ConcurrentHashMap());
    }

    long[] positions(World world, int n, int n2, int[] nArray) {
        boolean bl;
        Map<Long, Entry> map = this.world(world.getUID());
        long l2 = ChunkRefresher.key(n, n2);
        Entry entry = map.computeIfAbsent(l2, l -> new Entry());
        long l3 = System.currentTimeMillis();
        long l4 = this.plugin.config().scanCacheMillis;
        boolean bl2 = bl = entry.scanned && l4 > 0L && l3 - entry.scannedAt > l4;
        if (!(entry.scanned && !bl || entry.scanning || nArray[0] <= 0)) {
            nArray[0] = nArray[0] - 1;
            this.startScan(world, n, n2, entry);
        }
        return entry.positions;
    }

    private void startScan(World world, int n, int n2, Entry entry) {
        ChunkSnapshot chunkSnapshot;
        Object object;
        try {
            if (!world.isChunkLoaded(n, n2)) {
                return;
            }
            object = world.getChunkAt(n, n2);
            chunkSnapshot = object.getChunkSnapshot(false, false, false);
        }
        catch (Throwable throwable) {
            return;
        }
        entry.scanning = true;
        object = this.plugin.config();
        int n3 = Math.max(world.getMinHeight(), object.scanMinY);
        int n4 = Math.min(world.getMaxHeight() - 1, object.scanMaxY);
        TargetSet targetSet = this.plugin.targets();
        Schedulers.async((Plugin)this.plugin, () -> {
            try {
                entry.positions = ScanIndex.scan(chunkSnapshot, n, n2, n3, n4, targetSet);
                entry.scanned = true;
                entry.scannedAt = System.currentTimeMillis();
            }
            catch (Throwable throwable) {
                if (this.plugin.config().debug) {
                    this.plugin.getLogger().warning("scan of chunk " + n + "," + n2 + " failed: " + String.valueOf(throwable));
                }
            }
            finally {
                entry.scanning = false;
            }
        });
    }

    private static long[] scan(ChunkSnapshot chunkSnapshot, int n, int n2, int n3, int n4, TargetSet targetSet) {
        long[] lArray = EMPTY;
        int n5 = 0;
        int n6 = n << 4;
        int n7 = n2 << 4;
        for (int i = n3; i <= n4; ++i) {
            for (int j = 0; j < 16; ++j) {
                for (int k = 0; k < 16; ++k) {
                    if (!targetSet.isScanMaterial(chunkSnapshot.getBlockType(k, i, j))) continue;
                    if (n5 == lArray.length) {
                        lArray = Arrays.copyOf(lArray, Math.max(16, lArray.length << 1));
                    }
                    lArray[n5++] = BlockKey.of(n6 + k, i, n7 + j);
                }
            }
        }
        return n5 == lArray.length ? lArray : Arrays.copyOf(lArray, n5);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onBreak(BlockBreakEvent blockBreakEvent) {
        this.remove(blockBreakEvent.getBlock());
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPlace(BlockPlaceEvent blockPlaceEvent) {
        this.add(blockPlaceEvent.getBlock(), blockPlaceEvent.getBlock().getType());
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onGrow(BlockGrowEvent blockGrowEvent) {
        this.add(blockGrowEvent.getBlock(), blockGrowEvent.getNewState().getType());
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onForm(BlockFormEvent blockFormEvent) {
        this.add(blockFormEvent.getBlock(), blockFormEvent.getNewState().getType());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent chunkUnloadEvent) {
        Map<Long, Entry> map = this.worlds.get(chunkUnloadEvent.getWorld().getUID());
        if (map != null) {
            map.remove(ChunkRefresher.key(chunkUnloadEvent.getChunk().getX(), chunkUnloadEvent.getChunk().getZ()));
        }
    }

    private void add(Block block, Material material) {
        long[] lArray;
        TargetSet targetSet = this.plugin.targets();
        if (targetSet == null || !targetSet.isScanMaterial(material)) {
            return;
        }
        Entry entry = this.entryFor(block);
        if (entry == null || !entry.scanned) {
            return;
        }
        long l = BlockKey.of(block.getX(), block.getY(), block.getZ());
        for (long l2 : lArray = entry.positions) {
            if (l2 != l) continue;
            return;
        }
        long[] lArray2 = Arrays.copyOf(lArray, lArray.length + 1);
        lArray2[lArray.length] = l;
        entry.positions = lArray2;
    }

    private void remove(Block block) {
        Entry entry = this.entryFor(block);
        if (entry == null || !entry.scanned) {
            return;
        }
        long l = BlockKey.of(block.getX(), block.getY(), block.getZ());
        long[] lArray = entry.positions;
        for (int i = 0; i < lArray.length; ++i) {
            if (lArray[i] != l) continue;
            long[] lArray2 = new long[lArray.length - 1];
            System.arraycopy(lArray, 0, lArray2, 0, i);
            System.arraycopy(lArray, i + 1, lArray2, i, lArray.length - i - 1);
            entry.positions = lArray2;
            return;
        }
    }

    private Entry entryFor(Block block) {
        Map<Long, Entry> map = this.worlds.get(block.getWorld().getUID());
        return map == null ? null : map.get(ChunkRefresher.key(block.getX() >> 4, block.getZ() >> 4));
    }

    void clear() {
        this.worlds.clear();
    }

    int indexedChunks() {
        int n = 0;
        for (Map<Long, Entry> map : this.worlds.values()) {
            n += map.size();
        }
        return n;
    }

    private static final class Entry {
        volatile long[] positions = EMPTY;
        volatile boolean scanned;
        volatile boolean scanning;
        volatile long scannedAt;

        private Entry() {
        }
    }
}

