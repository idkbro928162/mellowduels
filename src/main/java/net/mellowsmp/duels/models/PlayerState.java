package net.mellowsmp.duels.models;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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

    private final ItemStack[] storageContents;
    private final ItemStack[] armorContents;
    private final ItemStack offHand;
    private final double health;
    private final double maxHealth;
    private final int foodLevel;
    private final float saturation;
    private final float exhaustion;
    private final int totalExperience;
    private final int level;
    private final float exp;
    private final GameMode gameMode;
    private final Location location;
    private final List<PotionEffect> potionEffects;
    private final boolean allowFlight;
    private final boolean flying;
    private final float walkSpeed;
    private final float flySpeed;
    private final int fireTicks;
    private final float fallDistance;

    private PlayerState(ItemStack[] storageContents, ItemStack[] armorContents, ItemStack offHand,
                        double health, double maxHealth, int foodLevel, float saturation, float exhaustion,
                        int totalExperience, int level, float exp, GameMode gameMode, Location location,
                        List<PotionEffect> potionEffects, boolean allowFlight, boolean flying,
                        float walkSpeed, float flySpeed, int fireTicks, float fallDistance) {
        this.storageContents = storageContents;
        this.armorContents = armorContents;
        this.offHand = offHand;
        this.health = health;
        this.maxHealth = maxHealth;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.exhaustion = exhaustion;
        this.totalExperience = totalExperience;
        this.level = level;
        this.exp = exp;
        this.gameMode = gameMode;
        this.location = location;
        this.potionEffects = potionEffects;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.walkSpeed = walkSpeed;
        this.flySpeed = flySpeed;
        this.fireTicks = fireTicks;
        this.fallDistance = fallDistance;
    }

    public static PlayerState capture(Player player) {
        PlayerInventory inv = player.getInventory();
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getBaseValue() : 20.0;

        return new PlayerState(
                cloneArray(inv.getStorageContents()),
                cloneArray(inv.getArmorContents()),
                inv.getItemInOffHand() != null ? inv.getItemInOffHand().clone() : null,
                player.getHealth(),
                maxHealth,
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getTotalExperience(),
                player.getLevel(),
                player.getExp(),
                player.getGameMode(),
                player.getLocation().clone(),
                new ArrayList<>(player.getActivePotionEffects()),
                player.getAllowFlight(),
                player.isFlying(),
                player.getWalkSpeed(),
                player.getFlySpeed(),
                player.getFireTicks(),
                player.getFallDistance()
        );
    }

    /** Restores this snapshot onto the given player and clears anything duel-related first. */
    public void restore(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setStorageContents(cloneArray(storageContents));
        inv.setArmorContents(cloneArray(armorContents));
        inv.setItemInOffHand(offHand != null ? offHand.clone() : null);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : potionEffects) {
            player.addPotionEffect(effect);
        }

        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(maxHealth);
        }
        double clampedHealth = Math.max(0.1, Math.min(health, maxHealth));
        player.setHealth(clampedHealth);
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setExhaustion(exhaustion);
        player.setTotalExperience(totalExperience);
        player.setLevel(level);
        player.setExp(exp);
        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(flying && allowFlight);
        player.setWalkSpeed(walkSpeed);
        player.setFlySpeed(flySpeed);
        player.setFireTicks(fireTicks);
        player.setFallDistance(fallDistance);
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
