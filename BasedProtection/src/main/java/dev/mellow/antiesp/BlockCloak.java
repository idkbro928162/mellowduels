/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.github.retrooper.packetevents.event.PacketListenerAbstract
 *  com.github.retrooper.packetevents.event.PacketListenerPriority
 *  com.github.retrooper.packetevents.event.PacketReceiveEvent
 *  com.github.retrooper.packetevents.event.PacketSendEvent
 *  com.github.retrooper.packetevents.protocol.nbt.NBTCompound
 *  com.github.retrooper.packetevents.protocol.packettype.PacketType$Play$Server
 *  com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon
 *  com.github.retrooper.packetevents.protocol.player.User
 *  com.github.retrooper.packetevents.protocol.teleport.RelativeFlag
 *  com.github.retrooper.packetevents.protocol.world.Location
 *  com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityType
 *  com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk
 *  com.github.retrooper.packetevents.protocol.world.chunk.Column
 *  com.github.retrooper.packetevents.protocol.world.chunk.TileEntity
 *  com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18
 *  com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette
 *  com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette
 *  com.github.retrooper.packetevents.protocol.world.chunk.palette.Palette
 *  com.github.retrooper.packetevents.protocol.world.chunk.palette.SingletonPalette
 *  com.github.retrooper.packetevents.util.Vector3i
 *  com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange$EncodedBlock
 *  com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook
 */
package dev.mellow.antiesp;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityType;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.Palette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.SingletonPalette;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import dev.mellow.antiesp.AntiESPConfig;
import dev.mellow.antiesp.AntiESPPlugin;
import dev.mellow.antiesp.BlockKey;
import dev.mellow.antiesp.PlayerState;
import dev.mellow.antiesp.TargetSet;
import dev.mellow.antiesp.WorldRule;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

final class BlockCloak
extends PacketListenerAbstract {
    private static final int SECTION_VOLUME = 4096;
    private static final int AIR = 0;
    private static final int KEEP = -1;
    private final AntiESPPlugin plugin;

    BlockCloak(AntiESPPlugin antiESPPlugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = antiESPPlugin;
    }

    public void onPacketSend(PacketSendEvent packetSendEvent) {
        PacketTypeCommon packetTypeCommon = packetSendEvent.getPacketType();
        if (packetTypeCommon == PacketType.Play.Server.PLAYER_POSITION_AND_LOOK) {
            this.handleServerTeleport(packetSendEvent);
            return;
        }
        AntiESPConfig antiESPConfig = this.plugin.config();
        if (!this.plugin.isActive() || !antiESPConfig.blocksEnabled && !antiESPConfig.targetsEnabled) {
            return;
        }
        PlayerState playerState = this.state(packetSendEvent.getUser());
        if (playerState == null || !playerState.protectedWorld()) {
            return;
        }
        if (packetTypeCommon == PacketType.Play.Server.CHUNK_DATA) {
            this.handleChunkData(packetSendEvent, playerState);
        } else if (packetTypeCommon == PacketType.Play.Server.BLOCK_CHANGE) {
            this.handleBlockChange(packetSendEvent, playerState);
        } else if (packetTypeCommon == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            this.handleMultiBlockChange(packetSendEvent, playerState);
        } else if (packetTypeCommon == PacketType.Play.Server.BLOCK_ENTITY_DATA) {
            this.handleBlockEntityData(packetSendEvent, playerState);
        }
    }

    private void handleChunkData(PacketSendEvent packetSendEvent, PlayerState playerState) {
        TileEntity[] tileEntityArray;
        WorldRule worldRule = playerState.rule;
        if (worldRule == null) {
            return;
        }
        AntiESPConfig antiESPConfig = this.plugin.config();
        TargetSet targetSet = this.plugin.targets();
        boolean bl = antiESPConfig.targetsEnabled && !targetSet.isEmpty();
        WrapperPlayServerChunkData wrapperPlayServerChunkData = new WrapperPlayServerChunkData(packetSendEvent);
        Column column = wrapperPlayServerChunkData.getColumn();
        boolean bl2 = this.hides(playerState, column.getX(), column.getZ());
        if (!bl2 && !bl) {
            return;
        }
        int n = column.getX() << 4;
        int n2 = column.getZ() << 4;
        boolean bl3 = false;
        BaseChunk[] baseChunkArray = column.getChunks();
        for (int i = 0; i < baseChunkArray.length; ++i) {
            BaseChunk baseChunk = baseChunkArray[i];
            if (baseChunk == null) continue;
            int n3 = worldRule.minY + (i << 4);
            int n4 = bl2 && n3 < worldRule.hideBelowY ? 1 : 0;
            int n5 = n4;
            if (n4 != 0) {
                BlockCloak.fill(baseChunk, Math.min(16, worldRule.hideBelowY - n3), worldRule.fakeStateId);
                bl3 = true;
                if (n3 + 16 <= worldRule.hideBelowY) continue;
            }
            if (!bl) continue;
            bl3 |= this.maskTargets(baseChunk, n, n3, n2, playerState, targetSet);
        }
        if ((antiESPConfig.hideBlockEntities || bl) && (tileEntityArray = column.getTileEntities()) != null && tileEntityArray.length > 0) {
            ArrayList<TileEntity> arrayList = new ArrayList<TileEntity>(tileEntityArray.length);
            for (TileEntity tileEntity : tileEntityArray) {
                boolean bl4;
                long l = BlockKey.of(tileEntity.getX(), tileEntity.getY(), tileEntity.getZ());
                if (!playerState.revealed.isEmpty() && playerState.revealed.contains(l)) {
                    arrayList.add(tileEntity);
                    continue;
                }
                boolean bl5 = bl4 = bl2 && antiESPConfig.hideBlockEntities && tileEntity.getY() < worldRule.hideBelowY;
                if (bl4 || bl && BlockCloak.isMasked(column, worldRule, targetSet, tileEntity)) continue;
                arrayList.add(tileEntity);
            }
            if (arrayList.size() != tileEntityArray.length) {
                wrapperPlayServerChunkData.setColumn(BlockCloak.rebuild(column, arrayList.toArray(new TileEntity[0])));
                bl3 = true;
            }
        }
        if (bl3) {
            packetSendEvent.markForReEncode(true);
        }
    }

    private static boolean isMasked(Column column, WorldRule worldRule, TargetSet targetSet, TileEntity tileEntity) {
        int n = tileEntity.getY() - worldRule.minY >> 4;
        BaseChunk[] baseChunkArray = column.getChunks();
        if (n < 0 || n >= baseChunkArray.length || baseChunkArray[n] == null) {
            return false;
        }
        int n2 = baseChunkArray[n].getBlockId(tileEntity.getX() & 0xF, tileEntity.getY() & 0xF, tileEntity.getZ() & 0xF);
        return n2 == targetSet.fakeStateFor(tileEntity.getY());
    }

    private boolean maskTargets(BaseChunk baseChunk, int n, int n2, int n3, PlayerState playerState, TargetSet targetSet) {
        if (!(baseChunk instanceof Chunk_v1_18)) {
            return this.maskTargetsSlow(baseChunk, n, n2, n3, playerState, targetSet);
        }
        Chunk_v1_18 chunk_v1_18 = (Chunk_v1_18)baseChunk;
        DataPalette dataPalette = chunk_v1_18.getChunkData();
        Palette palette = dataPalette.palette;
        if (palette instanceof SingletonPalette || dataPalette.storage == null) {
            int n4 = palette.idToState(0);
            if (!targetSet.isTargetState(n4)) {
                return false;
            }
            dataPalette.palette = new SingletonPalette(targetSet.fakeStateFor(n2));
            dataPalette.storage = null;
            return true;
        }
        if (!(palette instanceof GlobalPalette) && !BlockCloak.paletteHasTarget(palette, targetSet)) {
            return false;
        }
        return this.maskTargetsSlow(baseChunk, n, n2, n3, playerState, targetSet);
    }

    private static boolean paletteHasTarget(Palette palette, TargetSet targetSet) {
        int n = palette.size();
        for (int i = 0; i < n; ++i) {
            if (!targetSet.isTargetState(palette.idToState(i))) continue;
            return true;
        }
        return false;
    }

    private boolean maskTargetsSlow(BaseChunk baseChunk, int n, int n2, int n3, PlayerState playerState, TargetSet targetSet) {
        int n4;
        int n5;
        int n6;
        int[] nArray = null;
        int n7 = 0;
        for (n6 = 0; n6 < 16; ++n6) {
            for (n5 = 0; n5 < 16; ++n5) {
                for (n4 = 0; n4 < 16; ++n4) {
                    if (!targetSet.isTargetState(baseChunk.getBlockId(n4, n6, n5)) || !playerState.revealed.isEmpty() && playerState.revealed.contains(BlockKey.of(n + n4, n2 + n6, n3 + n5))) continue;
                    if (nArray == null) {
                        nArray = new int[64];
                    } else if (n7 == nArray.length) {
                        int[] nArray2 = new int[nArray.length << 1];
                        System.arraycopy(nArray, 0, nArray2, 0, nArray.length);
                        nArray = nArray2;
                    }
                    nArray[n7++] = n6 << 8 | n5 << 4 | n4;
                }
            }
        }
        if (n7 == 0) {
            return false;
        }
        for (n6 = 0; n6 < n7; ++n6) {
            n5 = nArray[n6];
            n4 = n5 >> 8;
            int n8 = n5 >> 4 & 0xF;
            int n9 = n5 & 0xF;
            baseChunk.set(n9, n4, n8, targetSet.fakeStateFor(n2 + n4));
        }
        return true;
    }

    private static void fill(BaseChunk baseChunk, int n, int n2) {
        if (n <= 0) {
            return;
        }
        boolean bl = n2 == 0;
        boolean bl2 = bl;
        if (baseChunk instanceof Chunk_v1_18) {
            int n3;
            Chunk_v1_18 chunk_v1_18 = (Chunk_v1_18)baseChunk;
            DataPalette dataPalette = chunk_v1_18.getChunkData();
            if (n >= 16) {
                dataPalette.palette = new SingletonPalette(n2);
                dataPalette.storage = null;
                chunk_v1_18.setBlockCount(bl ? 0 : 4096);
                return;
            }
            int n4 = 0;
            for (n3 = 0; n3 < n; ++n3) {
                for (int i = 0; i < 16; ++i) {
                    for (int j = 0; j < 16; ++j) {
                        boolean bl3 = dataPalette.getAndSet(j, n3, i, n2) == 0;
                        boolean bl4 = bl3;
                        if (bl3 && !bl) {
                            ++n4;
                            continue;
                        }
                        if (bl3 || !bl) continue;
                        --n4;
                    }
                }
            }
            n3 = chunk_v1_18.getBlockCount() + n4;
            chunk_v1_18.setBlockCount(Math.max(0, Math.min(4096, n3)));
            return;
        }
        for (int i = 0; i < n; ++i) {
            for (int j = 0; j < 16; ++j) {
                for (int k = 0; k < 16; ++k) {
                    baseChunk.set(k, i, j, n2);
                }
            }
        }
    }

    private static Column rebuild(Column column, TileEntity[] tileEntityArray) {
        int n = column.getX();
        int n2 = column.getZ();
        boolean bl = column.isFullChunk();
        BaseChunk[] baseChunkArray = column.getChunks();
        NBTCompound nBTCompound = null;
        try {
            nBTCompound = column.hasHeightMaps() ? column.getHeightMaps() : null;
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        if (nBTCompound != null) {
            int[] nArray = BlockCloak.biomeInts(column);
            if (nArray != null) {
                return new Column(n, n2, bl, baseChunkArray, tileEntityArray, nBTCompound, nArray);
            }
            byte[] byArray = BlockCloak.biomeBytes(column);
            if (byArray != null) {
                return new Column(n, n2, bl, baseChunkArray, tileEntityArray, nBTCompound, byArray);
            }
            return new Column(n, n2, bl, baseChunkArray, tileEntityArray, nBTCompound);
        }
        Map map = null;
        try {
            map = column.getHeightmaps();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        if (map != null && !map.isEmpty()) {
            return new Column(n, n2, bl, baseChunkArray, tileEntityArray, map);
        }
        int[] nArray = BlockCloak.biomeInts(column);
        if (nArray != null) {
            return new Column(n, n2, bl, baseChunkArray, tileEntityArray, nArray);
        }
        byte[] byArray = BlockCloak.biomeBytes(column);
        if (byArray != null) {
            return new Column(n, n2, bl, baseChunkArray, tileEntityArray, byArray);
        }
        return new Column(n, n2, bl, baseChunkArray, tileEntityArray);
    }

    private static int[] biomeInts(Column column) {
        try {
            return column.hasBiomeData() ? column.getBiomeDataInts() : null;
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    private static byte[] biomeBytes(Column column) {
        try {
            return column.hasBiomeData() ? column.getBiomeDataBytes() : null;
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    private void handleBlockChange(PacketSendEvent packetSendEvent, PlayerState playerState) {
        WorldRule worldRule = playerState.rule;
        if (worldRule == null) {
            return;
        }
        WrapperPlayServerBlockChange wrapperPlayServerBlockChange = new WrapperPlayServerBlockChange(packetSendEvent);
        Vector3i vector3i = wrapperPlayServerBlockChange.getBlockPosition();
        int n = this.replacementFor(playerState, worldRule, vector3i.getX(), vector3i.getY(), vector3i.getZ(), wrapperPlayServerBlockChange.getBlockId());
        if (n == -1) {
            return;
        }
        wrapperPlayServerBlockChange.setBlockID(n);
        packetSendEvent.markForReEncode(true);
    }

    private void handleMultiBlockChange(PacketSendEvent packetSendEvent, PlayerState playerState) {
        WorldRule worldRule = playerState.rule;
        if (worldRule == null) {
            return;
        }
        WrapperPlayServerMultiBlockChange wrapperPlayServerMultiBlockChange = new WrapperPlayServerMultiBlockChange(packetSendEvent);
        boolean bl = false;
        for (WrapperPlayServerMultiBlockChange.EncodedBlock encodedBlock : wrapperPlayServerMultiBlockChange.getBlocks()) {
            int n = this.replacementFor(playerState, worldRule, encodedBlock.getX(), encodedBlock.getY(), encodedBlock.getZ(), encodedBlock.getBlockId());
            if (n == -1) continue;
            encodedBlock.setBlockId(n);
            bl = true;
        }
        if (bl) {
            packetSendEvent.markForReEncode(true);
        }
    }

    private int replacementFor(PlayerState playerState, WorldRule worldRule, int n, int n2, int n3, int n4) {
        AntiESPConfig antiESPConfig = this.plugin.config();
        if (!playerState.revealed.isEmpty() && playerState.revealed.contains(BlockKey.of(n, n2, n3))) {
            return -1;
        }
        if (antiESPConfig.hideBlockUpdates && n2 < worldRule.hideBelowY && n4 != worldRule.fakeStateId && this.hides(playerState, n >> 4, n3 >> 4)) {
            return worldRule.fakeStateId;
        }
        if (!antiESPConfig.targetsEnabled) {
            return -1;
        }
        TargetSet targetSet = this.plugin.targets();
        if (!targetSet.isTargetState(n4)) {
            return -1;
        }
        return targetSet.fakeStateFor(n2);
    }

    private void handleBlockEntityData(PacketSendEvent packetSendEvent, PlayerState playerState) {
        BlockEntityType blockEntityType;
        boolean bl;
        AntiESPConfig antiESPConfig = this.plugin.config();
        WorldRule worldRule = playerState.rule;
        if (worldRule == null) {
            return;
        }
        WrapperPlayServerBlockEntityData wrapperPlayServerBlockEntityData = new WrapperPlayServerBlockEntityData(packetSendEvent);
        Vector3i vector3i = wrapperPlayServerBlockEntityData.getPosition();
        boolean bl2 = bl = !playerState.revealed.isEmpty() && playerState.revealed.contains(BlockKey.of(vector3i.getX(), vector3i.getY(), vector3i.getZ()));
        if (bl) {
            return;
        }
        if (antiESPConfig.hideBlockEntities && vector3i.getY() < worldRule.hideBelowY && this.hides(playerState, vector3i.getX() >> 4, vector3i.getZ() >> 4)) {
            packetSendEvent.setCancelled(true);
            return;
        }
        if (!antiESPConfig.targetsEnabled || !antiESPConfig.targetHideNbt) {
            return;
        }
        try {
            blockEntityType = wrapperPlayServerBlockEntityData.getBlockEntityType();
        }
        catch (Throwable throwable) {
            return;
        }
        if (this.plugin.targets().isTargetBlockEntity(blockEntityType)) {
            packetSendEvent.setCancelled(true);
        }
    }

    private void handleServerTeleport(PacketSendEvent packetSendEvent) {
        PlayerState playerState = this.state(packetSendEvent.getUser());
        if (playerState == null) {
            return;
        }
        WrapperPlayServerPlayerPositionAndLook wrapperPlayServerPlayerPositionAndLook = new WrapperPlayServerPlayerPositionAndLook(packetSendEvent);
        if (wrapperPlayServerPlayerPositionAndLook.isRelativeFlag(RelativeFlag.Y)) {
            return;
        }
        this.plugin.tracker().move(playerState, wrapperPlayServerPlayerPositionAndLook.getX(), wrapperPlayServerPlayerPositionAndLook.getY(), wrapperPlayServerPlayerPositionAndLook.getZ());
    }

    public void onPacketReceive(PacketReceiveEvent packetReceiveEvent) {
        if (!WrapperPlayClientPlayerFlying.isFlying((PacketTypeCommon)packetReceiveEvent.getPacketType())) {
            return;
        }
        PlayerState playerState = this.state(packetReceiveEvent.getUser());
        if (playerState == null) {
            return;
        }
        WrapperPlayClientPlayerFlying wrapperPlayClientPlayerFlying = new WrapperPlayClientPlayerFlying(packetReceiveEvent);
        if (!wrapperPlayClientPlayerFlying.hasPositionChanged()) {
            return;
        }
        Location location = wrapperPlayClientPlayerFlying.getLocation();
        this.plugin.tracker().move(playerState, location.getX(), location.getY(), location.getZ());
    }

    private PlayerState state(User user) {
        if (user == null) {
            return null;
        }
        UUID uUID = user.getUUID();
        return uUID == null ? null : this.plugin.tracker().get(uUID);
    }

    private boolean hides(PlayerState playerState, int n, int n2) {
        if (!this.plugin.config().blocksEnabled) {
            return false;
        }
        if (playerState.hidden) {
            return true;
        }
        int n3 = this.plugin.config().revealRadius;
        if (n3 < 0) {
            return false;
        }
        int n4 = Math.max(Math.abs(n - playerState.chunkX), Math.abs(n2 - playerState.chunkZ));
        return n4 > n3;
    }
}

