/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.github.retrooper.packetevents.PacketEvents
 *  com.github.retrooper.packetevents.event.PacketListenerCommon
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.event.Listener
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package dev.mellow.antiesp;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import dev.mellow.antiesp.AntiESPCommand;
import dev.mellow.antiesp.AntiESPConfig;
import dev.mellow.antiesp.BlockCloak;
import dev.mellow.antiesp.ChunkRefresher;
import dev.mellow.antiesp.EntityCloak;
import dev.mellow.antiesp.EntityVisibility;
import dev.mellow.antiesp.PlayerState;
import dev.mellow.antiesp.PlayerTracker;
import dev.mellow.antiesp.ScanIndex;
import dev.mellow.antiesp.Schedulers;
import dev.mellow.antiesp.TargetRevealer;
import dev.mellow.antiesp.TargetSet;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class AntiESPPlugin
extends JavaPlugin {
    private volatile AntiESPConfig config;
    private volatile TargetSet targets;
    private volatile boolean active;
    private PlayerTracker tracker;
    private ChunkRefresher refresher;
    private EntityVisibility visibility;
    private TargetRevealer revealer;
    private ScanIndex scanIndex;
    private BlockCloak blockCloak;
    private EntityCloak entityCloak;

    public void onEnable() {
        this.saveDefaultConfig();
        this.config = AntiESPConfig.load(this.getConfig(), this.getLogger());
        this.targets = this.buildTargets(this.config);
        this.active = this.config.enabled;
        if (PacketEvents.getAPI() == null) {
            this.getLogger().severe("PacketEvents is not available, disabling.");
            this.getServer().getPluginManager().disablePlugin((Plugin)this);
            return;
        }
        this.tracker = new PlayerTracker(this);
        this.refresher = new ChunkRefresher(this);
        this.visibility = new EntityVisibility(this);
        this.revealer = new TargetRevealer(this);
        this.scanIndex = new ScanIndex(this);
        this.blockCloak = new BlockCloak(this);
        this.entityCloak = new EntityCloak(this);
        PacketEvents.getAPI().getEventManager().registerListener((PacketListenerCommon)this.blockCloak);
        PacketEvents.getAPI().getEventManager().registerListener((PacketListenerCommon)this.entityCloak);
        this.getServer().getPluginManager().registerEvents((Listener)this.tracker, (Plugin)this);
        this.getServer().getPluginManager().registerEvents((Listener)this.scanIndex, (Plugin)this);
        AntiESPCommand antiESPCommand = new AntiESPCommand(this);
        this.getCommand("antiesp").setExecutor((CommandExecutor)antiESPCommand);
        this.getCommand("antiesp").setTabCompleter((TabCompleter)antiESPCommand);
        Schedulers.repeat((Plugin)this, () -> this.refresher.tick(), 20L, 1L);
        Schedulers.repeat((Plugin)this, () -> this.visibility.tick(), 40L, 1L);
        Schedulers.repeat((Plugin)this, () -> this.revealer.tick(), 40L, 1L);
        Schedulers.repeat((Plugin)this, () -> this.tracker.refreshPermissions(), 200L, 200L);
        this.tracker.applyAll();
        this.getLogger().info("BasedProtection active: NO BYPASS, ore anti-xray world-wide, blocks below Y=" + this.config.hideBelowY + " as " + String.valueOf(this.config.fakeMaterial) + ", entity hiding " + (this.config.entitiesEnabled ? "on" : "off") + ", cloaking " + this.targets.materials().size() + " block types" + (Schedulers.isFolia() ? " (Folia)" : ""));
    }

    public void onDisable() {
        if (PacketEvents.getAPI() != null) {
            if (this.blockCloak != null) {
                PacketEvents.getAPI().getEventManager().unregisterListener((PacketListenerCommon)this.blockCloak);
            }
            if (this.entityCloak != null) {
                PacketEvents.getAPI().getEventManager().unregisterListener((PacketListenerCommon)this.entityCloak);
            }
        }
        if (this.visibility != null) {
            this.visibility.revealAllNow();
        }
        if (this.refresher != null) {
            this.refresher.clear();
        }
        if (this.scanIndex != null) {
            this.scanIndex.clear();
        }
        Schedulers.cancelAll((Plugin)this);
    }

    AntiESPConfig config() {
        return this.config;
    }

    PlayerTracker tracker() {
        return this.tracker;
    }

    ChunkRefresher refresher() {
        return this.refresher;
    }

    TargetSet targets() {
        return this.targets;
    }

    ScanIndex scanIndex() {
        return this.scanIndex;
    }

    TargetRevealer revealer() {
        return this.revealer;
    }

    EntityVisibility visibility() {
        return this.visibility;
    }

    boolean isActive() {
        return this.active && this.config.enabled;
    }

    void setActive(boolean bl) {
        this.active = bl;
        if (!bl) {
            this.visibility.revealAll();
            for (PlayerState playerState : this.tracker.all().values()) {
                this.revealer.clear(playerState);
            }
        }
        this.tracker.applyAll();
    }

    void reload() {
        this.reloadConfig();
        this.config = AntiESPConfig.load(this.getConfig(), this.getLogger());
        this.targets = this.buildTargets(this.config);
        this.active = this.config.enabled;
        this.refresher.clear();
        this.visibility.revealAll();
        this.scanIndex.clear();
        for (PlayerState playerState : this.tracker.all().values()) {
            this.revealer.clear(playerState);
        }
        this.tracker.applyAll();
    }

    private TargetSet buildTargets(AntiESPConfig antiESPConfig) {
        return new TargetSet(antiESPConfig.targetMaterials, antiESPConfig.scanMaterials, antiESPConfig.targetBlockEntityNames, antiESPConfig.targetFakeMaterial, antiESPConfig.targetDeepFakeMaterial, antiESPConfig.targetDeepBelowY, this.getLogger());
    }
}

