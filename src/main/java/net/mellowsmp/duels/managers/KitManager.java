package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.Kit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        String displayName = configManager.color(sec.getString("display-name", id));
        Material icon = Material.matchMaterial(sec.getString("icon", "STONE_SWORD"));
        GameMode gameMode = GameMode.valueOf(sec.getString("game-mode", "SURVIVAL"));
        double health = sec.getDouble("health", 20.0);
        int hunger = sec.getInt("hunger", 20);
        boolean disableRegen = sec.getBoolean("disable-natural-regen", false);

        Map<Integer, ItemStack> items = new LinkedHashMap<>();
        ConfigurationSection itemsSec = sec.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String slotKey : itemsSec.getKeys(false)) {
                int slot = Integer.parseInt(slotKey);
                items.put(slot, parseItem(itemsSec.getConfigurationSection(slotKey)));
            }
        }

        Map<String, ItemStack> armor = new LinkedHashMap<>();
        ConfigurationSection armorSec = sec.getConfigurationSection("armor");
        if (armorSec != null) {
            for (String slotName : armorSec.getKeys(false)) {
                armor.put(slotName, parseItem(armorSec.getConfigurationSection(slotName)));
            }
        }

        List<PotionEffect> effects = new ArrayList<>(); // effects list parsing can be extended later

        return new Kit(id, displayName, icon, gameMode, health, hunger, disableRegen, items, armor, effects);
    }

    private ItemStack parseItem(ConfigurationSection itemSec) {
        Material mat = Material.matchMaterial(itemSec.getString("material", "STONE"));
        int amount = itemSec.getInt("amount", 1);
        ItemStack stack = new ItemStack(mat != null ? mat : Material.STONE, amount);

        ConfigurationSection enchantsSec = itemSec.getConfigurationSection("enchants");
        if (enchantsSec != null) {
            Set<String> keys = enchantsSec.getKeys(false);
            for (String enchantKey : keys) {
                Enchantment ench = Enchantment.getByName(enchantKey);
                int level = enchantsSec.getInt(enchantKey, 1);
                if (ench != null) {
                    stack.addUnsafeEnchantment(ench, level);
                }
            }
        }
        return stack;
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
        player.setHealth(Math.min(kit.getHealth(), player.getMaxHealth()));
        player.setFoodLevel(kit.getHunger());
        player.setSaturation(20f);

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
