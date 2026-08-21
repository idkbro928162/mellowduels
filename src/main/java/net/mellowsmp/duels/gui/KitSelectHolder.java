package net.mellowsmp.duels.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Marks kit-select inventories so click handlers can tell them apart from
 * normal chests, and carries the action to run after a kit is chosen.
 */
public final class KitSelectHolder implements InventoryHolder {

    public enum Mode {
        QUEUE,
        CHALLENGE
    }

    private final Mode mode;
    private final UUID challengeTarget;
    private Inventory inventory;

    public KitSelectHolder(Mode mode, @Nullable UUID challengeTarget) {
        this.mode = mode;
        this.challengeTarget = challengeTarget;
    }

    public Mode getMode() {
        return mode;
    }

    public @Nullable UUID getChallengeTarget() {
        return challengeTarget;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
