package net.mellowsmp.duels.models;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a configured dueling kit: inventory contents, armor, effects,
 * and starting player conditions applied when a duel begins.
 */
public class Kit {

    private final String id;
    private final String displayName;
    private final Material icon;
    private final GameMode gameMode;
    private final double health;
    private final int hunger;
    private final boolean disableNaturalRegen;
    private final Map<Integer, ItemStack> items;
    private final Map<String, ItemStack> armor;
    private final List<PotionEffect> effects;

    public Kit(String id, String displayName, Material icon, GameMode gameMode, double health, int hunger,
               boolean disableNaturalRegen, Map<Integer, ItemStack> items, Map<String, ItemStack> armor,
               List<PotionEffect> effects) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon != null ? icon : Material.STONE_SWORD;
        this.gameMode = gameMode != null ? gameMode : GameMode.SURVIVAL;
        this.health = health;
        this.hunger = hunger;
        this.disableNaturalRegen = disableNaturalRegen;
        this.items = items != null ? items : Map.of();
        this.armor = armor != null ? armor : Map.of();
        this.effects = effects != null ? effects : List.of();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public double getHealth() {
        return health;
    }

    public int getHunger() {
        return hunger;
    }

    public boolean isDisableNaturalRegen() {
        return disableNaturalRegen;
    }

    public Map<Integer, ItemStack> getItems() {
        return items;
    }

    public Map<String, ItemStack> getArmor() {
        return armor;
    }

    public List<PotionEffect> getEffects() {
        return effects;
    }

    /** Produces fresh clones of every item so repeated kit applications never share references. */
    public Map<Integer, ItemStack> cloneItems() {
        Map<Integer, ItemStack> copy = new HashMap<>();
        for (Map.Entry<Integer, ItemStack> e : items.entrySet()) {
            if (e.getValue() != null) {
                copy.put(e.getKey(), e.getValue().clone());
            }
        }
        return copy;
    }

    public Map<String, ItemStack> cloneArmor() {
        Map<String, ItemStack> copy = new HashMap<>();
        for (Map.Entry<String, ItemStack> e : armor.entrySet()) {
            if (e.getValue() != null) {
                copy.put(e.getKey(), e.getValue().clone());
            }
        }
        return copy;
    }

    public List<PotionEffect> cloneEffects() {
        return new ArrayList<>(effects);
    }
}
