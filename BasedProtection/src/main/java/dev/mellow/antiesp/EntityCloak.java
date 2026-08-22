/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.github.retrooper.packetevents.event.PacketListenerAbstract
 *  com.github.retrooper.packetevents.event.PacketListenerPriority
 *  com.github.retrooper.packetevents.event.PacketSendEvent
 *  com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
 *  com.github.retrooper.packetevents.protocol.packettype.PacketType$Play$Server
 *  com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon
 *  com.github.retrooper.packetevents.protocol.player.User
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAttachEntity
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCollectItem
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnExperienceOrb
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes
 */
package dev.mellow.antiesp;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAttachEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCollectItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnExperienceOrb;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import dev.mellow.antiesp.AntiESPPlugin;
import dev.mellow.antiesp.PlayerState;
import dev.mellow.antiesp.WorldRule;
import java.util.UUID;

final class EntityCloak
extends PacketListenerAbstract {
    private final AntiESPPlugin plugin;

    EntityCloak(AntiESPPlugin antiESPPlugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = antiESPPlugin;
    }

    public void onPacketSend(PacketSendEvent packetSendEvent) {
        if (!this.plugin.isActive() || !this.plugin.config().entitiesEnabled) {
            return;
        }
        PacketTypeCommon packetTypeCommon = packetSendEvent.getPacketType();
        if (!EntityCloak.isEntityPacket(packetTypeCommon)) {
            return;
        }
        PlayerState playerState = this.state(packetSendEvent.getUser());
        if (playerState == null || !playerState.protectedWorld()) {
            return;
        }
        if (packetTypeCommon == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity wrapperPlayServerSpawnEntity = new WrapperPlayServerSpawnEntity(packetSendEvent);
            if (wrapperPlayServerSpawnEntity.getEntityType() == EntityTypes.PLAYER && !this.plugin.config().hidePlayers) {
                return;
            }
            this.handleSpawn(packetSendEvent, playerState, wrapperPlayServerSpawnEntity.getEntityId(), wrapperPlayServerSpawnEntity.getPosition().getY(), wrapperPlayServerSpawnEntity.getUUID().orElse(null));
            return;
        }
        if (packetTypeCommon == PacketType.Play.Server.SPAWN_PLAYER) {
            if (!this.plugin.config().hidePlayers) {
                return;
            }
            WrapperPlayServerSpawnPlayer wrapperPlayServerSpawnPlayer = new WrapperPlayServerSpawnPlayer(packetSendEvent);
            this.handleSpawn(packetSendEvent, playerState, wrapperPlayServerSpawnPlayer.getEntityId(), wrapperPlayServerSpawnPlayer.getPosition().getY(), wrapperPlayServerSpawnPlayer.getUUID());
            return;
        }
        if (packetTypeCommon == PacketType.Play.Server.SPAWN_EXPERIENCE_ORB) {
            WrapperPlayServerSpawnExperienceOrb wrapperPlayServerSpawnExperienceOrb = new WrapperPlayServerSpawnExperienceOrb(packetSendEvent);
            this.handleSpawn(packetSendEvent, playerState, wrapperPlayServerSpawnExperienceOrb.getEntityId(), wrapperPlayServerSpawnExperienceOrb.getY(), null);
            return;
        }
        if (packetTypeCommon == PacketType.Play.Server.DESTROY_ENTITIES) {
            this.handleDestroy(packetSendEvent, playerState);
            return;
        }
        if (playerState.hiddenEntities.isEmpty()) {
            return;
        }
        int n = EntityCloak.entityIdOf(packetSendEvent, packetTypeCommon);
        if (n != -1 && playerState.isEntityHidden(n)) {
            packetSendEvent.setCancelled(true);
        }
    }

    private void handleDestroy(PacketSendEvent packetSendEvent, PlayerState playerState) {
        if (playerState.hiddenEntities.isEmpty()) {
            return;
        }
        WrapperPlayServerDestroyEntities wrapperPlayServerDestroyEntities = new WrapperPlayServerDestroyEntities(packetSendEvent);
        int[] nArray = wrapperPlayServerDestroyEntities.getEntityIds();
        int n = 0;
        for (int n2 : nArray) {
            if (playerState.isEntityHidden(n2)) continue;
            ++n;
        }
        if (n == nArray.length) {
            return;
        }
        if (n == 0) {
            packetSendEvent.setCancelled(true);
            return;
        }
        int[] nArray2 = new int[n];
        int n3 = 0;
        for (int n4 : nArray) {
            if (playerState.isEntityHidden(n4)) continue;
            nArray2[n3++] = n4;
        }
        wrapperPlayServerDestroyEntities.setEntityIds(nArray2);
        packetSendEvent.markForReEncode(true);
    }

    private void handleSpawn(PacketSendEvent packetSendEvent, PlayerState playerState, int n, double d, UUID uUID) {
        WorldRule worldRule = playerState.rule;
        if (worldRule == null) {
            return;
        }
        if (!playerState.hidden || d >= (double)worldRule.hideBelowY) {
            playerState.markVisible(n);
            return;
        }
        playerState.markHidden(n, uUID);
        packetSendEvent.setCancelled(true);
        this.plugin.visibility().enforceHidden(playerState, n, uUID);
    }

    private static boolean isEntityPacket(PacketTypeCommon packetTypeCommon) {
        return packetTypeCommon == PacketType.Play.Server.SPAWN_ENTITY || packetTypeCommon == PacketType.Play.Server.SPAWN_PLAYER || packetTypeCommon == PacketType.Play.Server.SPAWN_EXPERIENCE_ORB || packetTypeCommon == PacketType.Play.Server.DESTROY_ENTITIES || packetTypeCommon == PacketType.Play.Server.ENTITY_METADATA || packetTypeCommon == PacketType.Play.Server.ENTITY_EQUIPMENT || packetTypeCommon == PacketType.Play.Server.ENTITY_RELATIVE_MOVE || packetTypeCommon == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION || packetTypeCommon == PacketType.Play.Server.ENTITY_ROTATION || packetTypeCommon == PacketType.Play.Server.ENTITY_TELEPORT || packetTypeCommon == PacketType.Play.Server.ENTITY_POSITION_SYNC || packetTypeCommon == PacketType.Play.Server.ENTITY_HEAD_LOOK || packetTypeCommon == PacketType.Play.Server.ENTITY_VELOCITY || packetTypeCommon == PacketType.Play.Server.ENTITY_ANIMATION || packetTypeCommon == PacketType.Play.Server.ENTITY_STATUS || packetTypeCommon == PacketType.Play.Server.ENTITY_EFFECT || packetTypeCommon == PacketType.Play.Server.UPDATE_ATTRIBUTES || packetTypeCommon == PacketType.Play.Server.SET_PASSENGERS || packetTypeCommon == PacketType.Play.Server.ATTACH_ENTITY || packetTypeCommon == PacketType.Play.Server.COLLECT_ITEM;
    }

    private static int entityIdOf(PacketSendEvent packetSendEvent, PacketTypeCommon packetTypeCommon) {
        if (packetTypeCommon == PacketType.Play.Server.ENTITY_METADATA) {
            return new WrapperPlayServerEntityMetadata(packetSendEvent).getEntityId();
        }
        if (packetTypeCommon == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            return new WrapperPlayServerEntityEquipment(packetSendEvent).getEntityId();
        }
        if (packetTypeCommon == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
            return new WrapperPlayServerEntityRelativeMove(packetSendEvent).getEntityId();
        }
        if (packetTypeCommon == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            return new WrapperPlayServerEntityRelativeMoveAndRotation(packetSendEvent).getEntityId();
        }
        if (packetTypeCommon == PacketType.Play.Server.ENTITY_ROTATION) {
            return new WrapperPlayServerEntityRotation(packetSendEvent).getEntityId();
        }
        if (packetTypeCommon == PacketType.Play.Server.ENTITY_TELEPORT) {
            return new WrapperPlayServerEntityTeleport(packetSendEvent).getEntityId();
        }
        if (packetTypeCommon == PacketType.Play.Server.ENTITY_POSITION_SYNC) {
            return new WrapperPlayServerEntityPositionSync(packetSendEvent).getId();
        }
        if (packetTypeCommon == PacketType.Play.Server.ENTITY_HEAD_LOOK) {
            return new WrapperPlayServerEntityHeadLook(packetSendEvent).getEntityId();
        }
        if (packetTypeCommon == PacketType.Play.Server.ENTITY_VELOCITY) {
            return new WrapperPlayServerEntityVelocity(packetSendEvent).getEntityId();
        }
        if (packetTypeCommon == PacketType.Play.Server.ENTITY_ANIMATION) {
            return new WrapperPlayServerEntityAnimation(packetSendEvent).getEntityId();
        }
        if (packetTypeCommon == PacketType.Play.Server.ENTITY_STATUS) {
            return new WrapperPlayServerEntityStatus(packetSendEvent).getEntityId();
        }
        if (packetTypeCommon == PacketType.Play.Server.ENTITY_EFFECT) {
            return new WrapperPlayServerEntityEffect(packetSendEvent).getEntityId();
        }
        if (packetTypeCommon == PacketType.Play.Server.UPDATE_ATTRIBUTES) {
            return new WrapperPlayServerUpdateAttributes(packetSendEvent).getEntityId();
        }
        if (packetTypeCommon == PacketType.Play.Server.SET_PASSENGERS) {
            return new WrapperPlayServerSetPassengers(packetSendEvent).getEntityId();
        }
        if (packetTypeCommon == PacketType.Play.Server.ATTACH_ENTITY) {
            return new WrapperPlayServerAttachEntity(packetSendEvent).getAttachedId();
        }
        if (packetTypeCommon == PacketType.Play.Server.COLLECT_ITEM) {
            return new WrapperPlayServerCollectItem(packetSendEvent).getCollectedEntityId();
        }
        return -1;
    }

    private PlayerState state(User user) {
        if (user == null) {
            return null;
        }
        UUID uUID = user.getUUID();
        return uUID == null ? null : this.plugin.tracker().get(uUID);
    }
}

