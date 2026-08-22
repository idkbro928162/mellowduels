/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityType
 *  com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityTypes
 *  com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState
 *  com.github.retrooper.packetevents.protocol.world.states.type.StateType
 *  io.github.retrooper.packetevents.util.SpigotConversionUtil
 *  org.bukkit.Material
 *  org.bukkit.block.data.BlockData
 */
package dev.mellow.antiesp;

import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityType;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityTypes;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

final class TargetSet {
    private static final byte UNKNOWN = 0;
    private static final byte TARGET = 1;
    private static final byte PLAIN = 2;
    private final Set<Material> materials;
    private final Set<Material> scanMaterials;
    private final Set<StateType> stateTypes;
    private final Set<BlockEntityType> blockEntityTypes;
    private final Material fakeMaterial;
    private final Material deepFakeMaterial;
    private final int fakeStateId;
    private final int deepFakeStateId;
    private final int deepBelowY;
    private volatile byte[] cache = new byte[8192];

    TargetSet(Set<Material> set, Set<Material> set2, Iterable<String> iterable, Material material, Material material2, int n, Logger logger) {
        EnumSet<Material> enumSet = EnumSet.noneOf(Material.class);
        enumSet.addAll(set);
        enumSet.addAll(set2);
        this.materials = enumSet;
        this.scanMaterials = set2.isEmpty() ? EnumSet.noneOf(Material.class) : EnumSet.copyOf(set2);
        this.fakeMaterial = material;
        this.deepFakeMaterial = material2;
        this.deepBelowY = n;
        this.fakeStateId = TargetSet.stateId(material);
        this.deepFakeStateId = TargetSet.stateId(material2);
        HashSet<StateType> hashSet = new HashSet<StateType>();
        for (Material object : this.materials) {
            try {
                hashSet.add(SpigotConversionUtil.fromBukkitBlockData((BlockData)object.createBlockData()).getType());
            }
            catch (Throwable string) {
                logger.warning("tile-entities.materials: " + String.valueOf(object) + " has no packet block state, skipping");
            }
        }
        this.stateTypes = hashSet;
        HashSet hashSet2 = new HashSet();
        for (String string : iterable) {
            BlockEntityType blockEntityType = null;
            try {
                blockEntityType = BlockEntityTypes.getByName((String)string.toLowerCase(Locale.ROOT));
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            if (blockEntityType == null) {
                logger.warning("tile-entities.block-entity-types: unknown type '" + string + "', skipping");
                continue;
            }
            hashSet2.add(blockEntityType);
        }
        this.blockEntityTypes = hashSet2;
    }

    boolean isTargetBlockEntity(BlockEntityType blockEntityType) {
        return blockEntityType != null && this.blockEntityTypes.contains(blockEntityType);
    }

    private static int stateId(Material material) {
        return SpigotConversionUtil.fromBukkitBlockData((BlockData)material.createBlockData()).getGlobalId();
    }

    boolean isEmpty() {
        return this.stateTypes.isEmpty();
    }

    Set<Material> materials() {
        return this.materials;
    }

    boolean isTargetMaterial(Material material) {
        return this.materials.contains(material);
    }

    boolean isScanMaterial(Material material) {
        return this.scanMaterials.contains(material);
    }

    boolean hasScanMaterials() {
        return !this.scanMaterials.isEmpty();
    }

    Set<Material> scanMaterials() {
        return this.scanMaterials;
    }

    int fakeStateFor(int n) {
        return n < this.deepBelowY ? this.deepFakeStateId : this.fakeStateId;
    }

    Material fakeMaterialFor(int n) {
        return n < this.deepBelowY ? this.deepFakeMaterial : this.fakeMaterial;
    }

    BlockData fakeDataFor(int n) {
        return this.fakeMaterialFor(n).createBlockData();
    }

    boolean isTargetState(int n) {
        boolean bl;
        if (n < 0 || this.stateTypes.isEmpty()) {
            return false;
        }
        byte[] byArray = this.cache;
        if (n < byArray.length && (bl = byArray[n])) {
            return bl;
        }
        bl = this.resolve(n);
        this.store(n, bl);
        return bl;
    }

    private boolean resolve(int n) {
        try {
            WrappedBlockState wrappedBlockState = WrappedBlockState.getByGlobalId((int)n, (boolean)false);
            return wrappedBlockState != null && this.stateTypes.contains(wrappedBlockState.getType());
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void store(int n, boolean bl) {
        byte[] byArray = this.cache;
        if (n >= byArray.length) {
            TargetSet targetSet = this;
            synchronized (targetSet) {
                byArray = this.cache;
                if (n >= byArray.length) {
                    byte[] byArray2 = new byte[Math.max(n + 1, byArray.length * 2)];
                    System.arraycopy(byArray, 0, byArray2, 0, byArray.length);
                    this.cache = byArray2;
                    byArray = byArray2;
                }
            }
        }
        byArray[n] = bl ? 1 : 2;
    }

    static Set<Material> parseMaterials(Iterable<String> iterable, String string, Logger logger) {
        EnumSet<Material> enumSet = EnumSet.noneOf(Material.class);
        for (String string2 : iterable) {
            Material material = Material.matchMaterial((String)string2.toUpperCase(Locale.ROOT));
            if (material == null || !material.isBlock()) {
                logger.warning(string + ": '" + string2 + "' is not a block on this version, skipping");
                continue;
            }
            enumSet.add(material);
        }
        return enumSet;
    }
}

