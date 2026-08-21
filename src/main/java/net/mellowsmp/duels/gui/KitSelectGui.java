package net.mellowsmp.duels.gui;

import net.mellowsmp.duels.BasedDuels;
import net.mellowsmp.duels.models.Kit;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * A tagged inventory holder lets the click listener identify this GUI without
 * relying on a spoofable title.
 */
public final class KitSelectGui implements InventoryHolder {

    public static final String TITLE_PREFIX = "Select a Kit";
    private final BasedDuels plugin;
    private final Inventory inventory;

    private KitSelectGui(BasedDuels plugin) {
        this.plugin = plugin;
        var kits = plugin.getKitManager().getAllKits();
        int rows = Math.max(1, Math.min(6, (int) Math.ceil(kits.size() / 9.0)));
        this.inventory = Bukkit.createInventory(this, rows * 9,
                net.kyori.adventure.text.Component.text(TITLE_PREFIX));

        int slot = 0;
        for (Kit kit : kits.values()) {
            if (slot >= inventory.getSize()) {
                plugin.getLogger().warning("Only the first 54 kits can be shown in the kit selector.");
                break;
            }
            ItemStack icon = new ItemStack(kit.getIcon());
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(plugin.getConfigManager().component(kit.getDisplayName()));
            meta.lore(java.util.List.of(plugin.getConfigManager().component("&7Click to join this kit's queue")));
            meta.getPersistentDataContainer().set(kitKey(plugin),
                    org.bukkit.persistence.PersistentDataType.STRING, kit.getId());
            icon.setItemMeta(meta);

            inventory.setItem(slot++, icon);
        }
    }

    public static Inventory build(BasedDuels plugin) {
        return new KitSelectGui(plugin).getInventory();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public BasedDuels getPlugin() {
        return plugin;
    }

    public static org.bukkit.NamespacedKey kitKey(BasedDuels plugin) {
        return new org.bukkit.NamespacedKey(plugin, "kit_id");
    }
}
