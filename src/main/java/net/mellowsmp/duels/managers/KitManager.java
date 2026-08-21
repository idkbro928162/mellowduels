package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.Kit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class KitManager {

    private final MellowDuels plugin;
    private final ConfigManager configManager;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitManager(MellowDuels plugin, ConfigManager configManager) {
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
                kits.put(id, parseKit(id, section.getConfigurationSection(id)));
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
        if (icon == null) icon = Material.STONE_SWORD;
        GameMode gameMode = GameMode.valueOf(sec.getString("game-mode", "SURVIVAL").toUpperCase(Locale.ROOT));
        double health = sec.getDouble("health", 20.0);
        int hunger = sec.getInt("hunger", 20);
        boolean disableRegen = sec.getBoolean("disable-natural-regen", false);

        Map<Integer, ItemStack> items = new LinkedHashMap<>();
        ConfigurationSection itemsSec = sec.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String slotKey : itemsSec.getKeys(false)) {
                int slot = Integer.parseInt(slotKey);
                ConfigurationSection itemSec = itemsSec.getConfigurationSection(slotKey);
                if (itemSec != null) {
                    items.put(slot, parseItem(itemSec));
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

        List<PotionEffect> effects = new ArrayList<>();

        return new Kit(id, displayName, icon, gameMode, health, hunger, disableRegen, items, armor, effects);
    }

    private ItemStack parseItem(ConfigurationSection itemSec) {
        Material mat = Material.matchMaterial(itemSec.getString("material", "STONE"));
        int amount = Math.max(1, itemSec.getInt("amount", 1));
        ItemStack stack = new ItemStack(mat != null ? mat : Material.STONE, amount);

        ConfigurationSection enchantsSec = itemSec.getConfigurationSection("enchants");
        if (enchantsSec != null) {
            for (String enchantKey : enchantsSec.getKeys(false)) {
                Enchantment ench = resolveEnchantment(enchantKey);
                int level = enchantsSec.getInt(enchantKey, 1);
                if (ench != null) {
                    stack.addUnsafeEnchantment(ench, level);
                } else {
                    plugin.getLogger().warning("Unknown enchantment '" + enchantKey + "' in kits.yml");
                }
            }
        }
        return stack;
    }

    private Enchantment resolveEnchantment(String key) {
        if (key == null || key.isBlank()) return null;
        String normalized = key.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(normalized));
        if (ench != null) return ench;
        // Legacy Bukkit names (e.g. DAMAGE_ALL → sharpness)
        @SuppressWarnings("deprecation")
        Enchantment legacy = Enchantment.getByName(key.toUpperCase(Locale.ROOT));
        return legacy;
    }

    /** Applies the kit's inventory, armor, health, hunger, game mode and effects to the player. */
    public void applyKit(Player player, Kit kit) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        for (Map.Entry<Integer, ItemStack> e : kit.cloneItems().entrySet()) {
            inv.setItem(e.getKey(), e.getValue());
        }
        Map<String, ItemStack> armor = kit.cloneArmor();
        if (armor.containsKey("helmet")) inv.setHelmet(armor.get("helmet"));
        if (armor.containsKey("chestplate")) inv.setChestplate(armor.get("chestplate"));
        if (armor.containsKey("leggings")) inv.setLeggings(armor.get("leggings"));
        if (armor.containsKey("boots")) inv.setBoots(armor.get("boots"));

        player.setGameMode(kit.getGameMode());

        double maxHealth = kit.getHealth();
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(Math.max(1.0, maxHealth));
        }
        player.setHealth(Math.min(kit.getHealth(), player.getAttribute(Attribute.MAX_HEALTH) != null
                ? player.getAttribute(Attribute.MAX_HEALTH).getValue() : kit.getHealth()));
        player.setFoodLevel(kit.getHunger());
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFallDistance(0f);

        for (PotionEffect effect : player.getActivePotionEffects().toArray(new PotionEffect[0])) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : kit.getEffects()) {
            player.addPotionEffect(effect);
        }
    }

    public Kit getKit(String id) {
        return kits.get(id);
    }

    public Set<String> getKitNames() {
        return kits.keySet();
    }

    public Map<String, Kit> getAllKits() {
        return kits;
    }
}
