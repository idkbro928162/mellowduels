/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.World
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 */
package dev.mellow.antiesp;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class Schedulers {
    private static boolean modern = true;

    private Schedulers() {
    }

    static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        }
        catch (ClassNotFoundException classNotFoundException) {
            return false;
        }
    }

    static void repeat(Plugin plugin, Runnable runnable, long l, long l2) {
        if (modern) {
            try {
                Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> runnable.run(), Math.max(1L, l), Math.max(1L, l2));
                return;
            }
            catch (Throwable throwable) {
                modern = false;
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, runnable, l, l2);
    }

    static void async(Plugin plugin, Runnable runnable) {
        if (modern) {
            try {
                Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> runnable.run());
                return;
            }
            catch (Throwable throwable) {
                modern = false;
            }
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    static void region(Plugin plugin, World world, int n, int n2, Runnable runnable) {
        if (modern) {
            try {
                Bukkit.getRegionScheduler().execute(plugin, world, n, n2, runnable);
                return;
            }
            catch (Throwable throwable) {
                modern = false;
            }
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    static void player(Plugin plugin, Player player, Runnable runnable) {
        if (modern) {
            try {
                player.getScheduler().run(plugin, scheduledTask -> runnable.run(), null);
                return;
            }
            catch (Throwable throwable) {
                modern = false;
            }
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    static void entity(Plugin plugin, Entity entity, Runnable runnable) {
        if (modern) {
            try {
                entity.getScheduler().run(plugin, scheduledTask -> runnable.run(), null);
                return;
            }
            catch (Throwable throwable) {
                modern = false;
            }
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    static void playerLater(Plugin plugin, Player player, Runnable runnable, long l) {
        if (modern) {
            try {
                player.getScheduler().runDelayed(plugin, scheduledTask -> runnable.run(), null, Math.max(1L, l));
                return;
            }
            catch (Throwable throwable) {
                modern = false;
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, runnable, l);
    }

    static void cancelAll(Plugin plugin) {
        if (modern) {
            try {
                Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}

