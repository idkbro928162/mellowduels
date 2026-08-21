package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.BasedDuels;
import net.mellowsmp.duels.models.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.structure.Structure;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class ArenaManager {

    private final BasedDuels plugin;
    private final ConfigManager configManager;
    private final Map<String, Structure> templates = new LinkedHashMap<>();
    private final Map<String, List<Arena>> arenasByTemplate = new LinkedHashMap<>();
    private final Map<String, Arena> arenasById = new LinkedHashMap<>();
    private final AtomicInteger nextGridSlot = new AtomicInteger(0);

    public ArenaManager(BasedDuels plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    private File templatesFolder() {
        File dir = new File(plugin.getDataFolder(), "arena-templates");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().severe("Could not create arena template directory: " + dir);
        }
        return dir;
    }

    private File arenasIndexFile() {
        return new File(plugin.getDataFolder(), "arenas.yml");
    }

    public void loadArenas() {
        templates.clear();
        arenasByTemplate.clear();
        arenasById.clear();
        nextGridSlot.set(0);
        ensureArenaWorld();

        StructureManager sm = Bukkit.getStructureManager();
        File[] files = templatesFolder().listFiles((d, name) -> name.endsWith(".nbt"));
        if (files != null) {
            for (File f : files) {
                String name = f.getName().substring(0, f.getName().length() - 4);
                try {
                    Structure structure = sm.loadStructure(f);
                    templates.put(name, structure);
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not load arena template '" + name + "': " + e.getMessage());
                }
            }
        }

        File index = arenasIndexFile();
        if (index.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(index);
            ConfigurationSection section = yaml.getConfigurationSection("arenas");
            if (section != null) {
                for (String id : section.getKeys(false)) {
                    ConfigurationSection s = section.getConfigurationSection(id);
                    if (s == null) continue;
                    String templateName = s.getString("template");
                    String worldName = s.getString("world");
                    if (templateName == null || worldName == null || !templates.containsKey(templateName)) {
                        plugin.getLogger().warning("Skipping arena '" + id + "' with a missing template or world.");
                        continue;
                    }
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        plugin.getLogger().warning("Skipping arena '" + id + "' because world '" + worldName
                                + "' is not loaded.");
                        continue;
                    }
                    Location origin = new Location(world, s.getDouble("origin.x"), s.getDouble("origin.y"), s.getDouble("origin.z"));
                    int sx = s.getInt("size.x");
                    int sy = s.getInt("size.y");
                    int sz = s.getInt("size.z");
                    Arena arena = new Arena(id, templateName, world, origin, sx, sy, sz);
                    arena.setSpawnA(loadLoc(s, "spawnA", world));
                    arena.setSpawnB(loadLoc(s, "spawnB", world));
                    arena.setSpectatorSpawn(loadLoc(s, "spectatorSpawn", world));
                    if (arena.getSpawnA() == null || arena.getSpawnB() == null) {
                        plugin.getLogger().warning("Skipping arena '" + id + "' because participant spawns are missing.");
                        continue;
                    }
                    int slot = s.getInt("gridSlot", inferGridSlot(origin));
                    arena.setGridSlot(slot);
                    registerArena(arena);
                    if (slot >= nextGridSlot.get()) nextGridSlot.set(slot + 1);
                }
            }
        }
    }

    private void ensureArenaWorld() {
        if (Bukkit.getWorld(configManager.arenaWorldName()) != null) {
            return;
        }
        World world = new WorldCreator(configManager.arenaWorldName())
                .type(WorldType.FLAT)
                .generateStructures(false)
                .createWorld();
        if (world == null) {
            plugin.getLogger().severe("Could not create arena world '" + configManager.arenaWorldName() + "'.");
        }
    }

    private int inferGridSlot(Location origin) {
        return Math.max(0, (int) Math.round(origin.getX() / configManager.arenaSpacing()));
    }

    private Location loadLoc(ConfigurationSection s, String key, World world) {
        if (!s.contains(key + ".x")) return null;
        return new Location(world, s.getDouble(key + ".x"), s.getDouble(key + ".y"), s.getDouble(key + ".z"),
                (float) s.getDouble(key + ".yaw", 0), (float) s.getDouble(key + ".pitch", 0));
    }

    private void registerArena(Arena arena) {
        arenasById.put(arena.getId(), arena);
        arenasByTemplate.computeIfAbsent(arena.getTemplateName(), k -> new ArrayList<>()).add(arena);
    }

    public boolean captureTemplate(String name, Location corner1, Location corner2,
                                     BlockVector relativeSpawnA, BlockVector relativeSpawnB) {
        if (!name.matches("[A-Za-z0-9_-]{1,48}") || corner1.getWorld() == null
                || !corner1.getWorld().equals(corner2.getWorld())) {
            return false;
        }
        World world = corner1.getWorld();
        StructureManager sm = Bukkit.getStructureManager();
        Structure structure = sm.createStructure();

        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        Location origin = new Location(world, minX, minY, minZ);
        int sizeX = (maxX - minX) + 1;
        int sizeY = (maxY - minY) + 1;
        int sizeZ = (maxZ - minZ) + 1;

        structure.fill(origin, new Location(world, maxX, maxY, maxZ), true);

        try {
            File out = new File(templatesFolder(), name + ".nbt");
            sm.saveStructure(out, structure);
            templates.put(name, structure);

            YamlConfiguration meta = new YamlConfiguration();
            meta.set("size.x", sizeX);
            meta.set("size.y", sizeY);
            meta.set("size.z", sizeZ);
            meta.set("spawnA.x", relativeSpawnA.getBlockX());
            meta.set("spawnA.y", relativeSpawnA.getBlockY());
            meta.set("spawnA.z", relativeSpawnA.getBlockZ());
            meta.set("spawnB.x", relativeSpawnB.getBlockX());
            meta.set("spawnB.y", relativeSpawnB.getBlockY());
            meta.set("spawnB.z", relativeSpawnB.getBlockZ());
            meta.save(new File(templatesFolder(), name + ".meta.yml"));
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save arena template '" + name + "': " + e.getMessage());
            return false;
        }
    }

    public Arena generateCopy(String templateName) {
        Structure structure = templates.get(templateName);
        if (structure == null) {
            plugin.getLogger().warning("No template named '" + templateName + "' exists.");
            return null;
        }

        World world = Bukkit.getWorld(configManager.arenaWorldName());
        if (world == null) {
            plugin.getLogger().warning("Arena world '" + configManager.arenaWorldName() + "' is not loaded.");
            return null;
        }

        File metaFile = new File(templatesFolder(), templateName + ".meta.yml");
        if (!metaFile.isFile()) {
            plugin.getLogger().warning("Template '" + templateName + "' has no metadata file.");
            return null;
        }
        YamlConfiguration meta = YamlConfiguration.loadConfiguration(metaFile);
        int sizeX = meta.getInt("size.x", structure.getSize().getBlockX());
        int sizeY = meta.getInt("size.y", structure.getSize().getBlockY());
        int sizeZ = meta.getInt("size.z", structure.getSize().getBlockZ());
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1
                || !meta.contains("spawnA.x") || !meta.contains("spawnB.x")) {
            plugin.getLogger().warning("Template '" + templateName + "' has invalid metadata.");
            return null;
        }

        int slot = nextGridSlot.getAndIncrement();
        int spacing = configManager.arenaSpacing();
        if (sizeX >= spacing || sizeZ >= spacing) {
            plugin.getLogger().warning("Template '" + templateName + "' is too large for arenas.spacing="
                    + spacing + ". Increase the configured spacing.");
            nextGridSlot.decrementAndGet();
            return null;
        }
        Location origin = new Location(world, slot * spacing, 100, 0);

        structure.place(origin, true, org.bukkit.block.structure.StructureRotation.NONE,
                org.bukkit.block.structure.Mirror.NONE, 0, 1.0f, new java.util.Random());

        String id = UUID.randomUUID().toString();
        Arena arena = new Arena(id, templateName, world, origin, sizeX, sizeY, sizeZ);
        arena.setGridSlot(slot);

        BlockVector relA = new BlockVector(meta.getInt("spawnA.x"), meta.getInt("spawnA.y"), meta.getInt("spawnA.z"));
        BlockVector relB = new BlockVector(meta.getInt("spawnB.x"), meta.getInt("spawnB.y"), meta.getInt("spawnB.z"));
        arena.setSpawnA(origin.clone().add(relA.getBlockX(), relA.getBlockY(), relA.getBlockZ()));
        arena.setSpawnB(origin.clone().add(relB.getBlockX(), relB.getBlockY(), relB.getBlockZ()));
        arena.setSpectatorSpawn(origin.clone().add(sizeX / 2.0, sizeY + 5, sizeZ / 2.0));

        registerArena(arena);
        saveArenaIndex();
        return arena;
    }

    public Arena reserveArena(String preferredTemplate) {
        List<Arena> candidates;
        if (preferredTemplate != null && arenasByTemplate.containsKey(preferredTemplate)) {
            candidates = arenasByTemplate.get(preferredTemplate);
        } else {
            candidates = new ArrayList<>(arenasById.values());
        }

        for (Arena arena : candidates) {
            if (arena.isFree()) {
                arena.setState(Arena.State.RESERVED);
                maybeTopUpSpares(arena.getTemplateName());
                return arena;
            }
        }

        String templateToUse = preferredTemplate != null ? preferredTemplate
                : (templates.isEmpty() ? null : templates.keySet().iterator().next());
        if (templateToUse == null) {
            return null;
        }
        Arena generated = generateCopy(templateToUse);
        if (generated != null) {
            generated.setState(Arena.State.RESERVED);
        }
        return generated;
    }

    private void maybeTopUpSpares(String templateName) {
        List<Arena> list = arenasByTemplate.getOrDefault(templateName, List.of());
        long free = list.stream().filter(Arena::isFree).count();
        int needed = configManager.minSpareCopies();
        for (long i = free; i < needed; i++) {
            generateCopy(templateName);
        }
    }

    public void resetAndRelease(Arena arena) {
        arena.setState(Arena.State.RESETTING);
        Structure structure = templates.get(arena.getTemplateName());
        if (structure != null) {
            structure.place(arena.getOrigin(), true, org.bukkit.block.structure.StructureRotation.NONE,
                    org.bukkit.block.structure.Mirror.NONE, 0, 1.0f, new java.util.Random());
        }
        arena.getWorld().getNearbyEntities(boundingBoxOf(arena)).forEach(e -> {
            if (!(e instanceof org.bukkit.entity.Player)) {
                e.remove();
            }
        });
        arena.setCurrentSessionId(null);
        arena.setState(Arena.State.AVAILABLE);
    }

    private org.bukkit.util.BoundingBox boundingBoxOf(Arena arena) {
        Location o = arena.getOrigin();
        return new org.bukkit.util.BoundingBox(o.getX(), o.getY(), o.getZ(),
                o.getX() + arena.getSizeX(), o.getY() + arena.getSizeY(), o.getZ() + arena.getSizeZ());
    }

    private void saveArenaIndex() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Arena arena : arenasById.values()) {
            String path = "arenas." + arena.getId();
            yaml.set(path + ".template", arena.getTemplateName());
            yaml.set(path + ".world", arena.getWorld().getName());
            yaml.set(path + ".origin.x", arena.getOrigin().getX());
            yaml.set(path + ".origin.y", arena.getOrigin().getY());
            yaml.set(path + ".origin.z", arena.getOrigin().getZ());
            yaml.set(path + ".size.x", arena.getSizeX());
            yaml.set(path + ".size.y", arena.getSizeY());
            yaml.set(path + ".size.z", arena.getSizeZ());
            yaml.set(path + ".gridSlot", arena.getGridSlot());
            if (arena.getSpawnA() != null) setLoc(yaml, path + ".spawnA", arena.getSpawnA());
            if (arena.getSpawnB() != null) setLoc(yaml, path + ".spawnB", arena.getSpawnB());
            if (arena.getSpectatorSpawn() != null) setLoc(yaml, path + ".spectatorSpawn", arena.getSpectatorSpawn());
        }
        try {
            yaml.save(arenasIndexFile());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save arenas.yml: " + e.getMessage());
        }
    }

    private void setLoc(YamlConfiguration yaml, String path, Location loc) {
        yaml.set(path + ".x", loc.getX());
        yaml.set(path + ".y", loc.getY());
        yaml.set(path + ".z", loc.getZ());
        yaml.set(path + ".yaw", loc.getYaw());
        yaml.set(path + ".pitch", loc.getPitch());
    }

    public int getArenaCount() {
        return arenasById.size();
    }

    public Arena getById(String id) {
        return arenasById.get(id);
    }

    public Arena getArenaAt(Location location) {
        for (Arena arena : arenasById.values()) {
            if (arena.contains(location)) {
                return arena;
            }
        }
        return null;
    }

    public List<String> getTemplateNames() {
        return new ArrayList<>(templates.keySet());
    }
}
