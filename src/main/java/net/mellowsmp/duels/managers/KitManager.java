package net.mellowsmp.duels.managers;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.Kit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class KitManager {

    private final MellowDuels plugin;
    private final ConfigManager configManager;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final Map<String, String> idByLowercase = new LinkedHashMap<>();

    public KitManager(MellowDuels plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void loadKits() {
        kits.clear();
        idByLowercase.clear();
        File file = new File(plugin.getDataFolder(), "kits.yml");
        if (!file.exists()) {
            plugin.saveResource("kits.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("kits");
        if (section == null) {
            plugin.getLogger().warning("kits.yml has no 'kits' section - no kits loaded.");
            return;
        }

        for (String id : section.getKeys(false)) {
            try {
                Kit kit = parseKit(id, section.getConfigurationSection(id));
                kits.put(id, kit);
                idByLowercase.put(id.toLowerCase(Locale.ROOT), id);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to load kit '" + id + "': " + ex.getMessage());
            }
        }
    }

    private Kit parseKit(String id, ConfigurationSection sec) {
        if (sec == null) {
            throw new IllegalArgumentException("missing kit section");
        }
        String displayName = configManager.color(sec.getString("display-name", id));
        Material icon = Material.matchMaterial(sec.getString("icon", "STONE_SWORD"));
        if (icon == null) {
            icon = Material.STONE_SWORD;
        }
        GameMode gameMode;
        try {
            gameMode = GameMode.valueOf(sec.getString("game-mode", "SURVIVAL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            gameMode = GameMode.SURVIVAL;
        }
        double health = sec.getDouble("health", 20.0);
        int hunger = sec.getInt("hunger", 20);
        boolean disableRegen = sec.getBoolean("disable-natural-regen", false);

        Map<Integer, ItemStack> items = new LinkedHashMap<>();
        ConfigurationSection itemsSec = sec.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String slotKey : itemsSec.getKeys(false)) {
                ConfigurationSection itemSec = itemsSec.getConfigurationSection(slotKey);
                if (itemSec == null) {
                    continue;
                }
                try {
                    items.put(Integer.parseInt(slotKey), parseItem(itemSec));
                } catch (NumberFormatException ex) {
                    plugin.getLogger().warning("Kit '" + id + "' has a non-numeric item slot: " + slotKey);
                }
            }
        }

        Map<String, ItemStack> armor = new LinkedHashMap<>();
        ConfigurationSection armorSec = sec.getConfigurationSection("armor");
        if (armorSec != null) {
            for (String slotName : armorSec.getKeys(false)) {
                ConfigurationSection itemSec = armorSec.getConfigurationSection(slotName);
                if (itemSec != null) {
                    armor.put(slotName.toLowerCase(Locale.ROOT), parseItem(itemSec));
                }
            }
        }

        return new Kit(id, displayName, icon, gameMode, health, hunger, disableRegen, items, armor, parseEffects(sec));
    }

    private List<PotionEffect> parseEffects(ConfigurationSection sec) {
        List<PotionEffect> effects = new ArrayList<>();
        for (Map<?, ?> map : sec.getMapList("effects")) {
            if (map == null || map.get("type") == null) {
                continue;
            }
            String typeName = String.valueOf(map.get("type"));
            PotionEffectType type = potionType(typeName);
            if (type == null) {
                plugin.getLogger().warning("Unknown potion effect '" + typeName + "'");
                continue;
            }
            int duration = intFrom(map.get("duration"), 20 * 60 * 60);
            int amplifier = intFrom(map.get("amplifier"), 0);
            boolean ambient = boolFrom(map.get("ambient"), false);
            boolean particles = boolFrom(map.get("particles"), true);
            effects.add(new PotionEffect(type, duration, amplifier, ambient, particles));
        }
        return effects;
    }

    private static int intFrom(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static boolean boolFrom(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return fallback;
    }

    private static PotionEffectType potionType(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT));
        Registry<PotionEffectType> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT);
        return registry.get(key);
    }

    private ItemStack parseItem(ConfigurationSection itemSec) {
        Material mat = Material.matchMaterial(itemSec.getString("material", "STONE"));
        int amount = Math.max(1, itemSec.getInt("amount", 1));
        ItemStack stack = new ItemStack(mat != null ? mat : Material.STONE, amount);

        ConfigurationSection enchantsSec = itemSec.getConfigurationSection("enchants");
        if (enchantsSec != null) {
            for (String enchantKey : enchantsSec.getKeys(false)) {
                Enchantment ench = enchantment(enchantKey);
                int level = enchantsSec.getInt(enchantKey, 1);
                if (ench != null) {
                    stack.addUnsafeEnchantment(ench, level);
                }
            }
        }
        return stack;
    }

    private static Enchantment enchantment(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT));
        Registry<Enchantment> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        return registry.get(key);
    }

    /** Applies the kit's inventory, armor, health, hunger, game mode and effects to the player. */
    public void applyKit(Player player, Kit kit) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(null);
        inv.setItemInOffHand(null);
        for (Map.Entry<Integer, ItemStack> e : kit.cloneItems().entrySet()) {
            int slot = e.getKey();
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, e.getValue());
            }
        }
        Map<String, ItemStack> armor = kit.cloneArmor();
        if (armor.containsKey("helmet")) inv.setHelmet(armor.get("helmet"));
        if (armor.containsKey("chestplate")) inv.setChestplate(armor.get("chestplate"));
        if (armor.containsKey("leggings")) inv.setLeggings(armor.get("leggings"));
        if (armor.containsKey("boots")) inv.setBoots(armor.get("boots"));

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double health = Math.max(1.0, kit.getHealth());
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
        }
        player.setHealth(Math.min(health, maxHealth != null ? maxHealth.getValue() : health));
        player.setFoodLevel(Math.max(0, Math.min(20, kit.getHunger())));
        player.setSaturation(20f);
        player.setExhaustion(0f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setGameMode(kit.getGameMode());

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : kit.getEffects()) {
            player.addPotionEffect(effect);
        }
    }

    public Kit getKit(String id) {
        if (id == null) {
            return null;
        }
        Kit direct = kits.get(id);
        if (direct != null) {
            return direct;
        }
        String canonical = idByLowercase.get(id.toLowerCase(Locale.ROOT));
        return canonical != null ? kits.get(canonical) : null;
    }

    public Kit firstKit() {
        return kits.isEmpty() ? null : kits.values().iterator().next();
    }

    public Set<String> getKitNames() {
        return Collections.unmodifiableSet(kits.keySet());
    }

    public Map<String, Kit> getAllKits() {
        return Collections.unmodifiableMap(kits);
    }
}
