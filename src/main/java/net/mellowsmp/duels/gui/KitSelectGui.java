package net.mellowsmp.duels.gui;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.Kit;
import net.mellowsmp.duels.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/**
 * A simple chest GUI listing every configured kit as a clickable icon.
 */
public class KitSelectGui {

    public static Inventory build(MellowDuels plugin, KitSelectHolder.Mode mode, UUID challengeTarget) {
        var kits = plugin.getKitManager().getAllKits();
        int rows = Math.max(1, (int) Math.ceil(kits.size() / 9.0));
        KitSelectHolder holder = new KitSelectHolder(mode, challengeTarget);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Texts.component("Select a Kit"));
        holder.attach(inv);

        int slot = 0;
        for (Kit kit : kits.values()) {
            ItemStack icon = new ItemStack(kit.getIcon());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(Texts.component(kit.getDisplayName()));
                String action = mode == KitSelectHolder.Mode.CHALLENGE ? "Click to challenge with this kit" : "Click to queue with this kit";
                meta.lore(List.of(Texts.component("&7" + action)));
                meta.getPersistentDataContainer().set(kitKey(plugin), PersistentDataType.STRING, kit.getId());
                icon.setItemMeta(meta);
            }
            inv.setItem(slot++, icon);
        }
        return inv;
    }

    public static Inventory build(MellowDuels plugin) {
        return build(plugin, KitSelectHolder.Mode.QUEUE, null);
    }

    public static void open(MellowDuels plugin, Player player, KitSelectHolder.Mode mode, UUID challengeTarget) {
        player.openInventory(build(plugin, mode, challengeTarget));
    }

    public static NamespacedKey kitKey(MellowDuels plugin) {
        return new NamespacedKey(plugin, "kit_id");
    }
}
