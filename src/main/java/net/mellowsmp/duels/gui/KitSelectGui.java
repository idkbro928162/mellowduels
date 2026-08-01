package net.mellowsmp.duels.gui;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.Kit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.Consumer;

/**
 * A simple chest GUI listing every configured kit as a clickable icon.
 * The provided callback is invoked with the chosen kit id when a player clicks one.
 */
public class KitSelectGui {

    public static final String TITLE_PREFIX = "Select a Kit";

    public static Inventory build(MellowDuels plugin) {
        var kits = plugin.getKitManager().getAllKits();
        int rows = Math.max(1, (int) Math.ceil(kits.size() / 9.0));
        Inventory inv = Bukkit.createInventory(null, rows * 9, net.kyori.adventure.text.Component.text(TITLE_PREFIX));

        int slot = 0;
        for (Kit kit : kits.values()) {
            ItemStack icon = new ItemStack(kit.getIcon());
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(net.kyori.adventure.text.Component.text(kit.getDisplayName()));
            meta.lore(java.util.List.of(net.kyori.adventure.text.Component.text("§7Click to select this kit")));
            icon.setItemMeta(meta);

            var container = meta.getPersistentDataContainer();
            container.set(kitKey(plugin), org.bukkit.persistence.PersistentDataType.STRING, kit.getId());
            icon.setItemMeta(meta);

            inv.setItem(slot++, icon);
        }
        return inv;
    }

    public static org.bukkit.NamespacedKey kitKey(MellowDuels plugin) {
        return new org.bukkit.NamespacedKey(plugin, "kit_id");
    }
}
