package net.mellowsmp.duels.listeners;

import net.mellowsmp.duels.BasedDuels;
import net.mellowsmp.duels.gui.KitSelectGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class KitSelectListener implements Listener {

    private final BasedDuels plugin;

    public KitSelectListener(BasedDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof KitSelectGui)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) {
            return;
        }
        String kitId = clicked.getItemMeta().getPersistentDataContainer()
                .get(KitSelectGui.kitKey(plugin), PersistentDataType.STRING);
        if (kitId == null) {
            return;
        }

        player.closeInventory();
        String result = plugin.getQueueManager().join(player, kitId);
        switch (result) {
            case "already-in-queue" -> player.sendMessage(plugin.getConfigManager().message("already-in-queue"));
            case "already-in-duel" -> player.sendMessage(plugin.getConfigManager().message("already-in-duel"));
            case "already-spectating" ->
                    player.sendMessage(plugin.getConfigManager().message("already-spectating"));
            case "invalid-kit" -> player.sendMessage(plugin.getConfigManager().message("invalid-kit"));
            default -> {
                // QueueManager sends the success message.
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof KitSelectGui) {
            event.setCancelled(true);
        }
    }
}
