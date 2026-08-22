/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 */
package dev.mellow.antiesp;

import dev.mellow.antiesp.AntiESPConfig;
import dev.mellow.antiesp.AntiESPPlugin;
import dev.mellow.antiesp.PlayerState;
import dev.mellow.antiesp.Schedulers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

final class AntiESPCommand
implements CommandExecutor,
TabCompleter {
    private static final String PREFIX = String.valueOf(ChatColor.DARK_AQUA) + "[AntiESP] " + String.valueOf(ChatColor.GRAY);
    private final AntiESPPlugin plugin;

    AntiESPCommand(AntiESPPlugin antiESPPlugin) {
        this.plugin = antiESPPlugin;
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        String string2;
        if (!commandSender.hasPermission("antiesp.admin")) {
            commandSender.sendMessage(PREFIX + String.valueOf(ChatColor.RED) + "No permission.");
            return true;
        }
        switch (string2 = stringArray.length == 0 ? "status" : stringArray[0].toLowerCase(Locale.ROOT)) {
            case "reload": {
                this.plugin.reload();
                commandSender.sendMessage(PREFIX + "Config reloaded, chunks queued for refresh.");
                return true;
            }
            case "toggle": {
                this.plugin.setActive(!this.plugin.isActive());
                commandSender.sendMessage(PREFIX + "Now " + (this.plugin.isActive() ? String.valueOf(ChatColor.GREEN) + "enabled" : String.valueOf(ChatColor.RED) + "disabled") + String.valueOf(ChatColor.GRAY) + " for everyone.");
                return true;
            }
            case "status": {
                this.status(commandSender);
                return true;
            }
        }
        commandSender.sendMessage(PREFIX + "/" + string + " reload | status | toggle");
        return true;
    }

    private void status(CommandSender commandSender) {
        AntiESPConfig antiESPConfig = this.plugin.config();
        commandSender.sendMessage(PREFIX + "active: " + (this.plugin.isActive() ? String.valueOf(ChatColor.GREEN) + "yes" : String.valueOf(ChatColor.RED) + "no") + String.valueOf(ChatColor.GRAY) + (Schedulers.isFolia() ? " (Folia)" : ""));
        commandSender.sendMessage(PREFIX + "blocks: " + String.valueOf(ChatColor.WHITE) + (antiESPConfig.blocksEnabled ? "on" : "off") + String.valueOf(ChatColor.GRAY) + ", below Y " + String.valueOf(ChatColor.WHITE) + antiESPConfig.hideBelowY + String.valueOf(ChatColor.GRAY) + " as " + String.valueOf(ChatColor.WHITE) + String.valueOf(antiESPConfig.fakeMaterial) + String.valueOf(ChatColor.GRAY) + " (state " + antiESPConfig.fakeStateId + ")");
        commandSender.sendMessage(PREFIX + "margins: reveal " + String.valueOf(ChatColor.WHITE) + antiESPConfig.revealMargin + String.valueOf(ChatColor.GRAY) + ", hide " + String.valueOf(ChatColor.WHITE) + antiESPConfig.hideMargin + String.valueOf(ChatColor.GRAY) + ", reveal radius " + String.valueOf(ChatColor.WHITE) + (String)(antiESPConfig.revealRadius < 0 ? "view distance" : antiESPConfig.revealRadius + " chunks"));
        commandSender.sendMessage(PREFIX + "entities: " + String.valueOf(ChatColor.WHITE) + (antiESPConfig.entitiesEnabled ? "on" : "off") + String.valueOf(ChatColor.GRAY) + ", players " + String.valueOf(ChatColor.WHITE) + (antiESPConfig.hidePlayers ? "hidden" : "shown") + String.valueOf(ChatColor.GRAY) + ", line of sight " + String.valueOf(ChatColor.WHITE) + (antiESPConfig.losEnabled ? "on" : "off") + String.valueOf(ChatColor.GRAY) + ", every " + String.valueOf(ChatColor.WHITE) + antiESPConfig.entityCheckInterval + String.valueOf(ChatColor.GRAY) + " ticks");
        commandSender.sendMessage(PREFIX + "hidden blocks: " + String.valueOf(ChatColor.WHITE) + (String)(antiESPConfig.targetsEnabled ? this.plugin.targets().materials().size() + " types world-wide" : "off") + String.valueOf(ChatColor.GRAY) + ", reveal distance " + String.valueOf(ChatColor.WHITE) + (int)antiESPConfig.targetRevealDistance + String.valueOf(ChatColor.GRAY) + ", every " + String.valueOf(ChatColor.WHITE) + antiESPConfig.targetCheckInterval + String.valueOf(ChatColor.GRAY) + " ticks");
        commandSender.sendMessage(PREFIX + "scanned blocks: " + String.valueOf(ChatColor.WHITE) + this.plugin.targets().scanMaterials().size() + " types" + String.valueOf(ChatColor.GRAY) + " (Y " + antiESPConfig.scanMinY + ".." + antiESPConfig.scanMaxY + "), " + String.valueOf(ChatColor.WHITE) + this.plugin.scanIndex().indexedChunks() + String.valueOf(ChatColor.GRAY) + " chunks indexed");
        commandSender.sendMessage(PREFIX + "currently hidden entities: " + String.valueOf(ChatColor.WHITE) + this.plugin.visibility().hiddenCount() + String.valueOf(ChatColor.GRAY) + ", revealed blocks: " + String.valueOf(ChatColor.WHITE) + this.plugin.revealer().revealedCount());
        commandSender.sendMessage(PREFIX + "worlds: " + String.valueOf(ChatColor.WHITE) + (antiESPConfig.allWorlds ? "*" : String.join((CharSequence)", ", antiESPConfig.worlds)));
        commandSender.sendMessage(PREFIX + "refresh queue: " + String.valueOf(ChatColor.WHITE) + this.plugin.refresher().queued() + String.valueOf(ChatColor.GRAY) + " chunks (" + antiESPConfig.refreshesPerTick + "/tick)");
        commandSender.sendMessage(PREFIX + "tracked players: " + String.valueOf(ChatColor.WHITE) + this.plugin.tracker().all().size());
        if (commandSender instanceof Player) {
            PlayerState playerState = this.plugin.tracker().get(((Player)commandSender).getUniqueId());
            if (playerState == null) {
                return;
            }
            String string = playerState.bypass ? String.valueOf(ChatColor.YELLOW) + "bypassing" : (playerState.rule == null ? String.valueOf(ChatColor.YELLOW) + "world not protected" : (playerState.hidden ? String.valueOf(ChatColor.GREEN) + "cloaked (fake blocks, entities below hidden)" : String.valueOf(ChatColor.AQUA) + "revealed (real world)"));
            commandSender.sendMessage(PREFIX + "you: " + string + String.valueOf(ChatColor.GRAY) + " at Y=" + String.format(Locale.ROOT, "%.1f", playerState.y) + ", hiding " + playerState.hiddenEntities.size() + " entities, showing " + playerState.revealed.size() + " blocks");
        }
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (stringArray.length != 1 || !commandSender.hasPermission("antiesp.admin")) {
            return new ArrayList<String>();
        }
        ArrayList<String> arrayList = new ArrayList<String>();
        for (String string2 : Arrays.asList("reload", "status", "toggle")) {
            if (!string2.startsWith(stringArray[0].toLowerCase(Locale.ROOT))) continue;
            arrayList.add(string2);
        }
        return arrayList;
    }
}

