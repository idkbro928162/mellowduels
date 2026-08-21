package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.Arena;
import net.mellowsmp.duels.world.VoidChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.BlockVector;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class ArenaManager {

    private final MellowDuels plugin;
    private final ConfigManager configManager;
    private final Map<String, Structure> templates = new LinkedHashMap<>();
    private final Map<String, List<Arena>> arenasByTemplate = new LinkedHashMap<>();
    private final Map<String, Arena> arenasById = new LinkedHashMap<>();
    private final AtomicInteger nextGridSlot = new AtomicInteger(0);

    public ArenaManager(MellowDuels plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    private File templatesFolder() {
        File dir = new File(plugin.getDataFolder(), "arena-templates");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create arena-templates folder.");
        }
        return dir;
    }

    private File arenasIndexFile() {
        return new File(plugin.getDataFolder(), "arenas.yml");
    }

    public World getOrCreateArenaWorld() {
        String name = configManager.arenaWorldName();
        World world = Bukkit.getWorld(name);
        if (world != null) {
            return world;
        }
        plugin.getLogger().info("Creating arena world '" + name + "' (void).");
        WorldCreator creator = new WorldCreator(name);
        creator.generator(new VoidChunkGenerator());
        creator.generateStructures(false);
        creator.type(WorldType.FLAT);
        world = creator.createWorld();
        if (world != null) {
            world.setSpawnLocation(0, 64, 0);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
            world.setTime(6000);
        } else {
            plugin.getLogger().warning("Failed to create arena world '" + name + "'; falling back to the default world.");
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        }
        return world;
    }

    public void loadArenas() {
        templates.clear();
        arenasByTemplate.clear();
        arenasById.clear();
        nextGridSlot.set(0);

        World arenaWorld = getOrCreateArenaWorld();
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
                    if (s == null) {
                        continue;
                    }
                    String templateName = s.getString("template");
                    String worldName = s.getString("world");
                    World world = worldName != null ? Bukkit.getWorld(worldName) : null;
                    if (world == null) {
                        world = arenaWorld;
                    }
                    if (world == null) {
                        continue;
                    }
                    Location origin = new Location(world, s.getDouble("origin.x"), s.getDouble("origin.y"), s.getDouble("origin.z"));
                    int sx = s.getInt("size.x");
                    int sy = s.getInt("size.y");
                    int sz = s.getInt("size.z");
                    int spacing = configManager.arenaSpacing();
                    int slot = s.contains("gridSlot")
                            ? s.getInt("gridSlot")
                            : (int) Math.round(origin.getX() / Math.max(1, spacing));
                    Arena arena = new Arena(id, templateName, world, origin, sx, sy, sz, slot);
                    arena.setSpawnA(loadLoc(s, "spawnA", world));
                    arena.setSpawnB(loadLoc(s, "spawnB", world));
                    arena.setSpectatorSpawn(loadLoc(s, "spectatorSpawn", world));
                    registerArena(arena);
                    if (slot >= nextGridSlot.get()) {
                        nextGridSlot.set(slot + 1);
                    }
                }
            }
        }

        int spare = configManager.minSpareCopies();
        if (templates.isEmpty()) {
            long freeBuiltin = arenasByTemplate.getOrDefault(Arena.BUILTIN_TEMPLATE, List.of())
                    .stream().filter(Arena::isFree).count();
            for (long i = freeBuiltin; i < Math.max(1, spare); i++) {
                generateBuiltinCopy();
            }
        } else {
            for (String template : templates.keySet()) {
                maybeTopUpSpares(template);
            }
        }
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
        World world = corner1.getWorld();
        if (world == null || corner2.getWorld() == null || !world.equals(corner2.getWorld())) {
            return false;
        }
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
        if (templateName == null || Arena.BUILTIN_TEMPLATE.equals(templateName) || !templates.containsKey(templateName)) {
            if (templateName != null && !Arena.BUILTIN_TEMPLATE.equals(templateName) && !templates.containsKey(templateName)) {
                plugin.getLogger().warning("No template named '" + templateName + "' exists.");
                return null;
            }
            return generateBuiltinCopy();
        }

        Structure structure = templates.get(templateName);
        World world = getOrCreateArenaWorld();
        if (world == null) {
            plugin.getLogger().warning("Arena world '" + configManager.arenaWorldName() + "' is not loaded.");
            return null;
        }

        File metaFile = new File(templatesFolder(), templateName + ".meta.yml");
        YamlConfiguration meta = YamlConfiguration.loadConfiguration(metaFile);
        int sizeX = meta.getInt("size.x", structure.getSize().getBlockX());
        int sizeY = meta.getInt("size.y", structure.getSize().getBlockY());
        int sizeZ = meta.getInt("size.z", structure.getSize().getBlockZ());

        int slot = nextGridSlot.getAndIncrement();
        int spacing = configManager.arenaSpacing();
        Location origin = new Location(world, slot * (double) spacing, 100, 0);

        structure.place(origin, true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, new Random());

        String id = UUID.randomUUID().toString();
        Arena arena = new Arena(id, templateName, world, origin, sizeX, sizeY, sizeZ, slot);

        BlockVector relA = new BlockVector(meta.getInt("spawnA.x", 1), meta.getInt("spawnA.y", 1), meta.getInt("spawnA.z", 1));
        BlockVector relB = new BlockVector(
                meta.getInt("spawnB.x", Math.max(1, sizeX - 2)),
                meta.getInt("spawnB.y", 1),
                meta.getInt("spawnB.z", Math.max(1, sizeZ / 2)));
        Location spawnA = origin.clone().add(relA.getBlockX() + 0.5, relA.getBlockY(), relA.getBlockZ() + 0.5);
        Location spawnB = origin.clone().add(relB.getBlockX() + 0.5, relB.getBlockY(), relB.getBlockZ() + 0.5);
        spawnA.setYaw(yawTowards(spawnA, spawnB));
        spawnB.setYaw(yawTowards(spawnB, spawnA));
        arena.setSpawnA(spawnA);
        arena.setSpawnB(spawnB);
        arena.setSpectatorSpawn(origin.clone().add(sizeX / 2.0, sizeY + 5.0, sizeZ / 2.0));

        registerArena(arena);
        saveArenaIndex();
        return arena;
    }

    public Arena generateBuiltinCopy() {
        World world = getOrCreateArenaWorld();
        if (world == null) {
            return null;
        }
        int size = configManager.builtinArenaSize();
        int height = 8;
        int slot = nextGridSlot.getAndIncrement();
        int spacing = configManager.arenaSpacing();
        Location origin = new Location(world, slot * (double) spacing, 100, 0);
        Arena arena = new Arena(UUID.randomUUID().toString(), Arena.BUILTIN_TEMPLATE, world, origin, size, height, size, slot);
        buildBuiltinPlatform(arena);

        Location spawnA = origin.clone().add(2.5, 1, size / 2.0 + 0.5);
        Location spawnB = origin.clone().add(size - 1.5, 1, size / 2.0 + 0.5);
        spawnA.setYaw(yawTowards(spawnA, spawnB));
        spawnB.setYaw(yawTowards(spawnB, spawnA));
        arena.setSpawnA(spawnA);
        arena.setSpawnB(spawnB);
        arena.setSpectatorSpawn(origin.clone().add(size / 2.0, height + 2.0, size / 2.0));

        registerArena(arena);
        saveArenaIndex();
        return arena;
    }

    private static float yawTowards(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private void buildBuiltinPlatform(Arena arena) {
        Location o = arena.getOrigin();
        World world = arena.getWorld();
        int sx = arena.getSizeX();
        int sy = arena.getSizeY();
        int sz = arena.getSizeZ();
        int ox = o.getBlockX();
        int oy = o.getBlockY();
        int oz = o.getBlockZ();

        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                boolean border = x == 0 || z == 0 || x == sx - 1 || z == sz - 1;
                world.getBlockAt(ox + x, oy, oz + z).setType(border ? Material.BEDROCK : Material.SMOOTH_STONE);
                for (int y = 1; y < sy; y++) {
                    Material type = Material.AIR;
                    if (border) {
                        type = y == sy - 1 ? Material.BARRIER : Material.GLASS;
                    }
                    world.getBlockAt(ox + x, oy + y, oz + z).setType(type);
                }
            }
        }
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

        String templateToUse = preferredTemplate;
        if (templateToUse == null || !templates.containsKey(templateToUse)) {
            templateToUse = templates.isEmpty() ? Arena.BUILTIN_TEMPLATE : templates.keySet().iterator().next();
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
        if (arena.isBuiltin()) {
            buildBuiltinPlatform(arena);
        } else {
            Structure structure = templates.get(arena.getTemplateName());
            if (structure != null) {
                structure.place(arena.getOrigin(), true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, new Random());
            }
        }
        arena.getWorld().getNearbyEntities(boundingBoxOf(arena)).forEach(e -> {
            if (!(e instanceof Player)) {
                e.remove();
            }
        });
        arena.setCurrentSessionId(null);
        arena.setState(Arena.State.AVAILABLE);
    }

    public boolean deleteTemplate(String templateName) {
        List<Arena> copies = arenasByTemplate.getOrDefault(templateName, List.of());
        for (Arena arena : copies) {
            if (!arena.isFree()) {
                return false;
            }
        }
        for (Arena arena : new ArrayList<>(copies)) {
            arenasById.remove(arena.getId());
        }
        arenasByTemplate.remove(templateName);
        templates.remove(templateName);
        File nbt = new File(templatesFolder(), templateName + ".nbt");
        File meta = new File(templatesFolder(), templateName + ".meta.yml");
        if (nbt.exists() && !nbt.delete()) {
            plugin.getLogger().warning("Could not delete " + nbt.getName());
        }
        if (meta.exists() && !meta.delete()) {
            plugin.getLogger().warning("Could not delete " + meta.getName());
        }
        saveArenaIndex();
        return true;
    }

    private BoundingBox boundingBoxOf(Arena arena) {
        Location o = arena.getOrigin();
        return new BoundingBox(o.getX(), o.getY(), o.getZ(),
                o.getX() + arena.getSizeX(), o.getY() + arena.getSizeY(), o.getZ() + arena.getSizeZ());
    }

    private void saveArenaIndex() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Arena arena : arenasById.values()) {
            String path = "arenas." + arena.getId();
            yaml.set(path + ".template", arena.getTemplateName());
            yaml.set(path + ".world", arena.getWorld().getName());
            yaml.set(path + ".gridSlot", arena.getGridSlot());
            yaml.set(path + ".origin.x", arena.getOrigin().getX());
            yaml.set(path + ".origin.y", arena.getOrigin().getY());
            yaml.set(path + ".origin.z", arena.getOrigin().getZ());
            yaml.set(path + ".size.x", arena.getSizeX());
            yaml.set(path + ".size.y", arena.getSizeY());
            yaml.set(path + ".size.z", arena.getSizeZ());
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

    public List<String> getTemplateNames() {
        List<String> names = new ArrayList<>(templates.keySet());
        if (names.isEmpty()) {
            names.add(Arena.BUILTIN_TEMPLATE);
        }
        return names;
    }
}
