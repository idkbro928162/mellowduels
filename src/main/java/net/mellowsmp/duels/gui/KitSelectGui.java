package net.mellowsmp.duels.gui;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.Kit;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * A simple chest GUI listing every configured kit as a clickable icon.
 * Clicking a kit joins that kit's matchmaking queue (see KitGuiListener).
 */
public class KitSelectGui {

    public static final String TITLE_PREFIX = "Select a Kit";

    public static Inventory build(MellowDuels plugin) {
        var kits = plugin.getKitManager().getAllKits();
        int rows = Math.max(1, (int) Math.ceil(kits.size() / 9.0));
        rows = Math.min(6, rows);
        Inventory inv = Bukkit.createInventory(null, rows * 9,
                plugin.getConfigManager().component(TITLE_PREFIX));

        int slot = 0;
        for (Kit kit : kits.values()) {
            if (slot >= rows * 9) break;
            ItemStack icon = new ItemStack(kit.getIcon() != null ? kit.getIcon() : org.bukkit.Material.STONE_SWORD);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(plugin.getConfigManager().component(kit.getDisplayName()));
                meta.lore(List.of(plugin.getConfigManager().component("&7Click to queue for this kit")));
                meta.getPersistentDataContainer().set(
                        kitKey(plugin),
                        org.bukkit.persistence.PersistentDataType.STRING,
                        kit.getId());
                icon.setItemMeta(meta);
            }
            inv.setItem(slot++, icon);
        }
        return inv;
    }

    public static org.bukkit.NamespacedKey kitKey(MellowDuels plugin) {
        return new org.bukkit.NamespacedKey(plugin, "kit_id");
    }
}
