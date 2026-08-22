/*
 * Decompiled with CFR 0.152.
 */
package dev.mellow.antiesp;

import dev.mellow.antiesp.WorldRule;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PlayerState {
    final UUID uuid;
    volatile WorldRule rule;
    volatile UUID worldId;
    volatile boolean bypass;
    volatile boolean hidden;
    volatile double x;
    volatile double y;
    volatile double z;
    volatile int chunkX;
    volatile int chunkZ;
    final Set<Integer> hiddenEntities = ConcurrentHashMap.newKeySet();
    final Map<Integer, UUID> hiddenEntityUuids = new ConcurrentHashMap<Integer, UUID>();
    final Map<Integer, Integer> losFailures = new ConcurrentHashMap<Integer, Integer>();
    final Set<Long> revealed = ConcurrentHashMap.newKeySet();

    PlayerState(UUID uUID) {
        this.uuid = uUID;
    }

    boolean protectedWorld() {
        return this.rule != null && !this.bypass;
    }

    void clearEntities() {
        this.hiddenEntities.clear();
        this.hiddenEntityUuids.clear();
        this.losFailures.clear();
    }

    boolean isEntityHidden(int n) {
        return !this.hiddenEntities.isEmpty() && this.hiddenEntities.contains(n);
    }

    void markHidden(int n, UUID uUID) {
        this.hiddenEntities.add(n);
        if (uUID != null) {
            this.hiddenEntityUuids.put(n, uUID);
        }
    }

    void markVisible(int n) {
        this.hiddenEntities.remove(n);
        this.hiddenEntityUuids.remove(n);
        this.losFailures.remove(n);
    }
}

