package net.mellowsmp.duels.models;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * A full snapshot of a player's condition, taken immediately before a duel
 * starts and re-applied immediately after it ends, so duels never leak into
 * a player's normal survival progress.
 */
public class PlayerState {

    private final ItemStack[] inventoryContents;
    private final ItemStack[] armorContents;
    private final ItemStack offHand;
    private final double health;
    private final double maxHealth;
    private final int foodLevel;
    private final float saturation;
    private final int totalExperience;
    private final int level;
    private final float exp;
    private final GameMode gameMode;
    private final Location location;
    private final List<PotionEffect> potionEffects;
    private final boolean allowFlight;
    private final boolean flying;

    private PlayerState(ItemStack[] inventoryContents, ItemStack[] armorContents, ItemStack offHand,
                          double health, double maxHealth, int foodLevel, float saturation,
                          int totalExperience, int level, float exp, GameMode gameMode, Location location,
                          List<PotionEffect> potionEffects, boolean allowFlight, boolean flying) {
        this.inventoryContents = inventoryContents;
        this.armorContents = armorContents;
        this.offHand = offHand;
        this.health = health;
        this.maxHealth = maxHealth;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.totalExperience = totalExperience;
        this.level = level;
        this.exp = exp;
        this.gameMode = gameMode;
        this.location = location;
        this.potionEffects = potionEffects;
        this.allowFlight = allowFlight;
        this.flying = flying;
    }

    public static PlayerState capture(Player player) {
        PlayerInventory inv = player.getInventory();
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) != null
                ? player.getAttribute(Attribute.MAX_HEALTH).getBaseValue() : 20.0;

        return new PlayerState(
                cloneArray(inv.getContents()),
                cloneArray(inv.getArmorContents()),
                inv.getItemInOffHand() != null ? inv.getItemInOffHand().clone() : null,
                player.getHealth(),
                maxHealth,
                player.getFoodLevel(),
                player.getSaturation(),
                player.getTotalExperience(),
                player.getLevel(),
                player.getExp(),
                player.getGameMode(),
                player.getLocation().clone(),
                new ArrayList<>(player.getActivePotionEffects()),
                player.getAllowFlight(),
                player.isFlying()
        );
    }

    /** Restores this snapshot onto the given player and clears anything duel-related first. */
    public void restore(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setContents(cloneArray(inventoryContents));
        inv.setArmorContents(cloneArray(armorContents));
        inv.setItemInOffHand(offHand != null ? offHand.clone() : null);

        for (PotionEffect effect : player.getActivePotionEffects().toArray(new PotionEffect[0])) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : potionEffects) {
            player.addPotionEffect(effect);
        }

        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
        }
        player.setHealth(Math.min(health, maxHealth));
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setTotalExperience(totalExperience);
        player.setLevel(level);
        player.setExp(exp);
        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(flying);
        player.teleport(location);
    }

    private static ItemStack[] cloneArray(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] != null ? source[i].clone() : null;
        }
        return copy;
    }
}
