package net.mellowsmp.duels.listeners;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.gui.KitSelectGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles clicks in the kit-selection GUI (opened by /duel or /duel gui).
 * Selecting a kit joins that kit's matchmaking queue.
 */
public class KitGuiListener implements Listener {

    private final MellowDuels plugin;

    public KitGuiListener(MellowDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().title() == null) return;

        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        if (!title.startsWith(KitSelectGui.TITLE_PREFIX)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        String kitId = meta.getPersistentDataContainer().get(KitSelectGui.kitKey(plugin), PersistentDataType.STRING);
        if (kitId == null) return;

        player.closeInventory();
        String result = plugin.getQueueManager().join(player, kitId);
        switch (result) {
            case "already-in-queue" -> player.sendMessage(plugin.getConfigManager().message("already-in-queue")
                    .replace("%kit%", kitId));
            case "already-in-duel" -> player.sendMessage(plugin.getConfigManager().message("already-in-duel"));
            case "spectating" -> player.sendMessage("§cLeave spectator mode before queuing.");
            case "invalid-kit" -> player.sendMessage("§cUnknown kit: " + kitId);
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        if (title.startsWith(KitSelectGui.TITLE_PREFIX)) {
            event.setCancelled(true);
        }
    }
}
