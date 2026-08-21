package com.based.itemesp.listeners;

import com.based.itemesp.BasedItemEsp;
import com.based.itemesp.managers.ConfigManager;
import com.based.itemesp.managers.VisibilityManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

/**
 * Cancels {@link PacketType.Play.Server#SPAWN_ENTITY} for dropped items when the
 * receiving player does not have line of sight.
 */
public final class ItemPacketListener {

    private final BasedItemEsp plugin;
    private final ConfigManager config;
    private final VisibilityManager visibilityManager;
    private PacketAdapter adapter;

    public ItemPacketListener(BasedItemEsp plugin, ConfigManager config, VisibilityManager visibilityManager) {
        this.plugin = plugin;
        this.config = config;
        this.visibilityManager = visibilityManager;
    }

    public void register() {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        adapter = new PacketAdapter(plugin, ListenerPriority.HIGH, PacketType.Play.Server.SPAWN_ENTITY) {
            @Override
            public void onPacketSending(PacketEvent event) {
                handleSpawn(event);
            }
        };
        manager.addPacketListener(adapter);
        config.debug("ItemPacketListener registered for SPAWN_ENTITY.");
    }

    public void unregister() {
        if (adapter != null) {
            ProtocolLibrary.getProtocolManager().removePacketListener(adapter);
            adapter = null;
            config.debug("ItemPacketListener unregistered.");
        }
    }

    private void handleSpawn(PacketEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (!config.isEnabled() || !config.isHideCompletely()) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }
        if (player.hasPermission("itemesp.bypass")) {
            return;
        }

        PacketContainer packet = event.getPacket();
        Entity entity = resolveEntity(packet, player);
        if (!(entity instanceof Item item) || !item.isValid()) {
            return;
        }

        boolean reveal = visibilityManager.shouldRevealItem(player, item);
        if (reveal) {
            visibilityManager.markShown(player, item);
            return;
        }

        event.setCancelled(true);
        visibilityManager.markHidden(player, item);
        config.debug("Cancelled item spawn#" + item.getEntityId() + " for " + player.getName());
    }

    private Entity resolveEntity(PacketContainer packet, Player player) {
        try {
            Entity fromModifier = packet.getEntityModifier(player.getWorld()).read(0);
            if (fromModifier != null) {
                return fromModifier;
            }
        } catch (Exception ignored) {
            // Fall through to ID lookup.
        }

        try {
            Integer id = packet.getIntegers().read(0);
            if (id == null) {
                return null;
            }
            return ProtocolLibrary.getProtocolManager().getEntityFromID(player.getWorld(), id);
        } catch (Exception ex) {
            config.debug("Failed to resolve SPAWN_ENTITY entity: " + ex.getMessage());
            return null;
        }
    }
}
