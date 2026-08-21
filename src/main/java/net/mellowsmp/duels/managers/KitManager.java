package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.BasedDuels;
import net.mellowsmp.duels.models.Kit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

public class KitManager {

    private final BasedDuels plugin;
    private final ConfigManager configManager;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitManager(BasedDuels plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void loadKits() {
        kits.clear();
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
                ConfigurationSection kitSection = section.getConfigurationSection(id);
                if (kitSection == null) {
                    throw new IllegalArgumentException("kit entry must be a section");
                }
                kits.put(normalize(id), parseKit(id, kitSection));
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to load kit '" + id + "': " + ex.getMessage());
            }
        }
    }

    private Kit parseKit(String id, ConfigurationSection sec) {
        String displayName = configManager.color(sec.getString("display-name", id));
        Material icon = Material.matchMaterial(sec.getString("icon", "STONE_SWORD"));
        if (icon == null || !icon.isItem()) {
            throw new IllegalArgumentException("invalid icon material");
        }
        GameMode gameMode = GameMode.valueOf(sec.getString("game-mode", "SURVIVAL").toUpperCase(Locale.ROOT));
        double health = Math.max(1.0, sec.getDouble("health", 20.0));
        int hunger = Math.max(0, Math.min(20, sec.getInt("hunger", 20)));
        boolean disableRegen = sec.getBoolean("disable-natural-regen", false);

        Map<Integer, ItemStack> items = new LinkedHashMap<>();
        ConfigurationSection itemsSec = sec.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String slotKey : itemsSec.getKeys(false)) {
                int slot = Integer.parseInt(slotKey);
                if (slot < 0 || slot >= 36) {
                    throw new IllegalArgumentException("inventory slot must be between 0 and 35: " + slot);
                }
                ConfigurationSection itemSection = itemsSec.getConfigurationSection(slotKey);
                if (itemSection == null) {
                    throw new IllegalArgumentException("item at slot " + slot + " must be a section");
                }
                items.put(slot, parseItem(itemSection));
            }
        }

        Map<String, ItemStack> armor = new LinkedHashMap<>();
        ConfigurationSection armorSec = sec.getConfigurationSection("armor");
        if (armorSec != null) {
            for (String slotName : armorSec.getKeys(false)) {
                if (!Set.of("helmet", "chestplate", "leggings", "boots").contains(slotName.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("invalid armor slot: " + slotName);
                }
                ConfigurationSection itemSection = armorSec.getConfigurationSection(slotName);
                if (itemSection == null) {
                    throw new IllegalArgumentException("armor entry " + slotName + " must be a section");
                }
                armor.put(slotName.toLowerCase(Locale.ROOT), parseItem(itemSection));
            }
        }

        List<PotionEffect> effects = new ArrayList<>();
        for (Map<?, ?> effectMap : sec.getMapList("effects")) {
            Object typeValue = effectMap.get("type");
            if (typeValue == null) {
                throw new IllegalArgumentException("potion effect is missing type");
            }
            PotionEffectType type = PotionEffectType.getByName(String.valueOf(typeValue).toUpperCase(Locale.ROOT));
            if (type == null) {
                throw new IllegalArgumentException("unknown potion effect: " + typeValue);
            }
            int duration = number(effectMap.get("duration-ticks"), 20 * 60);
            int amplifier = number(effectMap.get("amplifier"), 0);
            boolean ambient = bool(effectMap.get("ambient"), false);
            boolean particles = bool(effectMap.get("particles"), true);
            boolean iconVisible = bool(effectMap.get("icon"), true);
            effects.add(new PotionEffect(type, Math.max(1, duration), Math.max(0, amplifier),
                    ambient, particles, iconVisible));
        }

        return new Kit(id, displayName, icon, gameMode, health, hunger, disableRegen, items, armor, effects);
    }

    private ItemStack parseItem(ConfigurationSection itemSec) {
        Material mat = Material.matchMaterial(itemSec.getString("material", "STONE"));
        if (mat == null || !mat.isItem() || mat.isAir()) {
            throw new IllegalArgumentException("invalid item material: " + itemSec.getString("material"));
        }
        int amount = Math.max(1, Math.min(mat.getMaxStackSize(), itemSec.getInt("amount", 1)));
        ItemStack stack = new ItemStack(mat, amount);

        ConfigurationSection enchantsSec = itemSec.getConfigurationSection("enchants");
        if (enchantsSec != null) {
            Set<String> keys = enchantsSec.getKeys(false);
            for (String enchantKey : keys) {
                NamespacedKey key = NamespacedKey.fromString(enchantKey.toLowerCase(Locale.ROOT));
                if (key == null || key.getNamespace().equals(NamespacedKey.MINECRAFT) && key.getKey().isBlank()) {
                    throw new IllegalArgumentException("invalid enchantment: " + enchantKey);
                }
                Enchantment ench = org.bukkit.Registry.ENCHANTMENT.get(key);
                int level = enchantsSec.getInt(enchantKey, 1);
                if (ench == null) {
                    throw new IllegalArgumentException("unknown enchantment: " + enchantKey);
                }
                stack.addUnsafeEnchantment(ench, Math.max(1, level));
            }
        }
        return stack;
    }

    /** Applies the kit's inventory, armor, health, hunger, game mode and effects to the player. */
    public void applyKit(Player player, Kit kit) {
        PlayerInventory inv = player.getInventory();
        player.closeInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);
        for (Map.Entry<Integer, ItemStack> e : kit.cloneItems().entrySet()) {
            inv.setItem(e.getKey(), e.getValue());
        }
        Map<String, ItemStack> armor = kit.cloneArmor();
        if (armor.containsKey("helmet")) inv.setHelmet(armor.get("helmet"));
        if (armor.containsKey("chestplate")) inv.setChestplate(armor.get("chestplate"));
        if (armor.containsKey("leggings")) inv.setLeggings(armor.get("leggings"));
        if (armor.containsKey("boots")) inv.setBoots(armor.get("boots"));

        player.setGameMode(kit.getGameMode());
        player.setHealth(Math.max(1.0, Math.min(kit.getHealth(), player.getMaxHealth())));
        player.setFoodLevel(kit.getHunger());
        player.setSaturation(20f);
        player.setExhaustion(0f);
        player.setAbsorptionAmount(0);
        player.setNoDamageTicks(0);

        for (PotionEffect effect : player.getActivePotionEffects().toArray(new PotionEffect[0])) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : kit.getEffects()) {
            player.addPotionEffect(effect);
        }
    }

    public Kit getKit(String id) {
        return id == null ? null : kits.get(normalize(id));
    }

    public Set<String> getKitNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Kit kit : kits.values()) {
            names.add(kit.getId());
        }
        return java.util.Collections.unmodifiableSet(names);
    }

    public Map<String, Kit> getAllKits() {
        return java.util.Collections.unmodifiableMap(kits);
    }

    private String normalize(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    private int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }
}
