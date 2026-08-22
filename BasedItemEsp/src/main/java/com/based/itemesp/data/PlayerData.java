package com.based.itemesp.data;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player visibility cache and client-shown state.
 */
public final class PlayerData {

    public record CacheEntry(boolean visible, long checkedAtTick, double x, double y, double z) {
        public boolean movedFrom(double nx, double ny, double nz, double thresholdSq) {
            double dx = nx - x;
            double dy = ny - y;
            double dz = nz - z;
            return (dx * dx + dy * dy + dz * dz) >= thresholdSq;
        }
    }

    private final UUID playerId;
    private final ConcurrentHashMap<Integer, CacheEntry> visibilityCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Boolean> clientVisible = new ConcurrentHashMap<>();

    public PlayerData(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public ConcurrentHashMap<Integer, CacheEntry> getVisibilityCache() {
        return visibilityCache;
    }

    public ConcurrentHashMap<Integer, Boolean> getClientVisible() {
        return clientVisible;
    }

    public CacheEntry getCache(int entityId) {
        return visibilityCache.get(entityId);
    }

    public void putCache(int entityId, boolean visible, long tick, double x, double y, double z) {
        visibilityCache.put(entityId, new CacheEntry(visible, tick, x, y, z));
    }

    public void putCache(int entityId, boolean visible, long tick) {
        CacheEntry old = visibilityCache.get(entityId);
        if (old != null) {
            visibilityCache.put(entityId, new CacheEntry(visible, tick, old.x(), old.y(), old.z()));
        } else {
            visibilityCache.put(entityId, new CacheEntry(visible, tick, 0, 0, 0));
        }
    }

    public boolean isClientVisible(int entityId) {
        return Boolean.TRUE.equals(clientVisible.get(entityId));
    }

    public void setClientVisible(int entityId, boolean visible) {
        clientVisible.put(entityId, visible);
    }

    public void removeEntity(int entityId) {
        visibilityCache.remove(entityId);
        clientVisible.remove(entityId);
    }

    public void clear() {
        visibilityCache.clear();
        clientVisible.clear();
    }
}
