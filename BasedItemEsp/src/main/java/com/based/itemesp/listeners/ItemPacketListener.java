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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Intercepts {@link PacketType.Play.Server#SPAWN_ENTITY}.
 * <p>
 * Packet handlers often run off the main thread, so we only read packet fields
 * here, cancel suspicious spawns, then decide hide/show on the main thread.
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
        adapter = new PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.SPAWN_ENTITY) {
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

        PacketContainer packet = event.getPacket();
        EntityType type = readEntityType(packet);
        if (type == null) {
            return;
        }

        boolean droppedItem = VisibilityManager.isDroppedItemType(type);
        boolean hologram = config.isHideStackerHolograms() && VisibilityManager.isHologramType(type);
        if (!droppedItem && !hologram) {
            return;
        }

        Integer entityId = readEntityId(packet);
        if (entityId == null) {
            return;
        }

        Location packetLocation = readLocation(packet, player);
        // Cancel first — netty thread must not touch Bukkit entity APIs.
        event.setCancelled(true);
        visibilityManager.markHidden(player, entityId);

        final int id = entityId;
        final EntityType spawnType = type;
        final Location loc = packetLocation == null ? null : packetLocation.clone();

        Runnable decide = () -> visibilityManager.handleCancelledSpawn(player, id, loc, spawnType);
        if (Bukkit.isPrimaryThread()) {
            decide.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, decide);
        }

        config.debug("Queued spawn gate #" + id + " type=" + type + " for " + player.getName());
    }

    private EntityType readEntityType(PacketContainer packet) {
        try {
            return packet.getEntityTypeModifier().read(0);
        } catch (Exception ex) {
            config.debug("Could not read entity type: " + ex.getMessage());
            return null;
        }
    }

    private Integer readEntityId(PacketContainer packet) {
        try {
            return packet.getIntegers().read(0);
        } catch (Exception ex) {
            config.debug("Could not read entity id: " + ex.getMessage());
            return null;
        }
    }

    private Location readLocation(PacketContainer packet, Player player) {
        try {
            double x = packet.getDoubles().read(0);
            double y = packet.getDoubles().read(1);
            double z = packet.getDoubles().read(2);
            return new Location(player.getWorld(), x, y, z);
        } catch (Exception ex) {
            try {
                // Some versions use doubles at different indices; ignore if unavailable.
                return null;
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
