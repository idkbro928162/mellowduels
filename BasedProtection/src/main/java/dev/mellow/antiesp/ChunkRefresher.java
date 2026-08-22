/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.World
 *  org.bukkit.plugin.Plugin
 */
package dev.mellow.antiesp;

import dev.mellow.antiesp.AntiESPPlugin;
import dev.mellow.antiesp.Schedulers;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

final class ChunkRefresher {
    private final AntiESPPlugin plugin;
    private final Map<UUID, LinkedHashSet<Long>> queues = new ConcurrentHashMap<UUID, LinkedHashSet<Long>>();

    ChunkRefresher(AntiESPPlugin antiESPPlugin) {
        this.plugin = antiESPPlugin;
    }

    static long key(int n, int n2) {
        return (long)n << 32 | (long)n2 & 0xFFFFFFFFL;
    }

    private static int keyX(long l) {
        return (int)(l >> 32);
    }

    private static int keyZ(long l) {
        return (int)l;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void queueSquare(UUID uUID2, int n, int n2, int n3) {
        if (uUID2 == null) {
            return;
        }
        LinkedHashSet linkedHashSet = this.queues.computeIfAbsent(uUID2, uUID -> new LinkedHashSet());
        int n4 = this.plugin.config().maxQueuedChunks;
        LinkedHashSet linkedHashSet2 = linkedHashSet;
        synchronized (linkedHashSet2) {
            for (int i = 0; i <= n3; ++i) {
                for (int j = -i; j <= i; ++j) {
                    for (int k = -i; k <= i; ++k) {
                        if (Math.max(Math.abs(j), Math.abs(k)) != i) continue;
                        if (linkedHashSet.size() >= n4) {
                            return;
                        }
                        linkedHashSet.add(ChunkRefresher.key(n + j, n2 + k));
                    }
                }
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void queueRadiusDiff(UUID uUID2, int n, int n2, int n3, int n4, int n5) {
        if (uUID2 == null) {
            return;
        }
        LinkedHashSet linkedHashSet = this.queues.computeIfAbsent(uUID2, uUID -> new LinkedHashSet());
        int n6 = this.plugin.config().maxQueuedChunks;
        LinkedHashSet linkedHashSet2 = linkedHashSet;
        synchronized (linkedHashSet2) {
            for (int i = -n5; i <= n5; ++i) {
                for (int j = -n5; j <= n5; ++j) {
                    if (linkedHashSet.size() >= n6) {
                        return;
                    }
                    int n7 = n3 + i;
                    int n8 = n4 + j;
                    if (Math.max(Math.abs(n7 - n), Math.abs(n8 - n2)) > n5) {
                        linkedHashSet.add(ChunkRefresher.key(n7, n8));
                    }
                    int n9 = n + i;
                    int n10 = n2 + j;
                    if (Math.max(Math.abs(n9 - n3), Math.abs(n10 - n4)) <= n5) continue;
                    linkedHashSet.add(ChunkRefresher.key(n9, n10));
                }
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    int queued() {
        int n = 0;
        Iterator<LinkedHashSet<Long>> iterator = this.queues.values().iterator();
        while (iterator.hasNext()) {
            LinkedHashSet<Long> linkedHashSet;
            LinkedHashSet<Long> linkedHashSet2 = linkedHashSet = iterator.next();
            synchronized (linkedHashSet2) {
                n += linkedHashSet.size();
            }
        }
        return n;
    }

    void clear() {
        this.queues.clear();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void tick() {
        int n = this.plugin.config().refreshesPerTick;
        if (n <= 0 || this.queues.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, LinkedHashSet<Long>> entry : this.queues.entrySet()) {
            if (n <= 0) {
                return;
            }
            World world = Bukkit.getWorld((UUID)entry.getKey());
            LinkedHashSet<Long> linkedHashSet = entry.getValue();
            if (world == null) {
                linkedHashSet.clear();
                continue;
            }
            ArrayList<Long> arrayList = new ArrayList<Long>();
            Object object = linkedHashSet;
            synchronized (object) {
                Iterator iterator = linkedHashSet.iterator();
                while (iterator.hasNext() && arrayList.size() < n) {
                    arrayList.add((Long)iterator.next());
                    iterator.remove();
                }
            }
            n -= arrayList.size();
            object = arrayList.iterator();
            while (object.hasNext()) {
                long l = (Long)object.next();
                int n2 = ChunkRefresher.keyX(l);
                int n3 = ChunkRefresher.keyZ(l);
                Schedulers.region((Plugin)this.plugin, world, n2, n3, () -> {
                    if (world.isChunkLoaded(n2, n3)) {
                        world.refreshChunk(n2, n3);
                    }
                });
            }
        }
    }
}

