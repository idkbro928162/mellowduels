package com.based.itemesp.data;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player visibility cache and client-shown state.
 * All maps are concurrent for safe access from packet + scheduler threads
 * (mutations that touch Bukkit entities still run on the main thread).
 */
public final class PlayerData {

    public record CacheEntry(boolean visible, long checkedAtTick) {
    }

    private final UUID playerId;
    private final ConcurrentHashMap<Integer, CacheEntry> visibilityCache = new ConcurrentHashMap<>();
    /** Whether this player's client currently has the entity shown. */
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

    public void putCache(int entityId, boolean visible, long tick) {
        visibilityCache.put(entityId, new CacheEntry(visible, tick));
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
