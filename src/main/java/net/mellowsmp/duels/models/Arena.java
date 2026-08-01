package net.mellowsmp.duels.models;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Represents one physical, playable arena instance (a pasted copy of an arena
 * template). Tracks its bounding box, spawn points, and reservation state so
 * the ArenaManager can hand it out to duels and reclaim it afterwards.
 */
public class Arena {

    public enum State {
        AVAILABLE,
        RESERVED,
        IN_USE,
        RESETTING
    }

    private final String id;
    private final String templateName;
    private final World world;
    private final Location origin;      // paste origin (min corner) of this copy
    private final int sizeX, sizeY, sizeZ;
    private Location spawnA;
    private Location spawnB;
    private Location spectatorSpawn;
    private volatile State state = State.AVAILABLE;
    private String currentSessionId;

    public Arena(String id, String templateName, World world, Location origin, int sizeX, int sizeY, int sizeZ) {
        this.id = id;
        this.templateName = templateName;
        this.world = world;
        this.origin = origin;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    public String getId() {
        return id;
    }

    public String getTemplateName() {
        return templateName;
    }

    public World getWorld() {
        return world;
    }

    public Location getOrigin() {
        return origin;
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    public Location getSpawnA() {
        return spawnA;
    }

    public void setSpawnA(Location spawnA) {
        this.spawnA = spawnA;
    }

    public Location getSpawnB() {
        return spawnB;
    }

    public void setSpawnB(Location spawnB) {
        this.spawnB = spawnB;
    }

    public Location getSpectatorSpawn() {
        return spectatorSpawn;
    }

    public void setSpectatorSpawn(Location spectatorSpawn) {
        this.spectatorSpawn = spectatorSpawn;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public String getCurrentSessionId() {
        return currentSessionId;
    }

    public void setCurrentSessionId(String currentSessionId) {
        this.currentSessionId = currentSessionId;
    }

    public boolean isFree() {
        return state == State.AVAILABLE;
    }

    /** Returns true if the given location falls inside this arena's bounding box. */
    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().equals(world)) {
            return false;
        }
        double x = loc.getX() - origin.getX();
        double y = loc.getY() - origin.getY();
        double z = loc.getZ() - origin.getZ();
        return x >= 0 && x <= sizeX && y >= 0 && y <= sizeY && z >= 0 && z <= sizeZ;
    }
}
