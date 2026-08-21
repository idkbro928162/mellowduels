package net.mellowsmp.duels.listeners;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.commands.DuelCommand;
import net.mellowsmp.duels.gui.KitSelectGui;
import net.mellowsmp.duels.gui.KitSelectHolder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class KitGuiListener implements Listener {

    private final MellowDuels plugin;
    private final DuelCommand duelCommand;

    public KitGuiListener(MellowDuels plugin, DuelCommand duelCommand) {
        this.plugin = plugin;
        this.duelCommand = duelCommand;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof KitSelectHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        String kitId = item.getItemMeta().getPersistentDataContainer()
                .get(KitSelectGui.kitKey(plugin), PersistentDataType.STRING);
        if (kitId == null) {
            return;
        }

        player.closeInventory();
        if (holder.getMode() == KitSelectHolder.Mode.CHALLENGE) {
            if (holder.getChallengeTarget() == null) {
                return;
            }
            Player target = Bukkit.getPlayer(holder.getChallengeTarget());
            if (target == null) {
                player.sendMessage(plugin.getConfigManager().message("player-not-found"));
                return;
            }
            duelCommand.handleJoinResult(player, plugin.getRequestManager().challenge(player, target, kitId), kitId);
        } else {
            duelCommand.handleJoinResult(player, plugin.getQueueManager().join(player, kitId), kitId);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof KitSelectHolder) {
            event.setCancelled(true);
        }
    }
}
