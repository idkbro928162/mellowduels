package net.mellowsmp.duels.commands;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.gui.KitSelectGui;
import net.mellowsmp.duels.managers.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DuelCommand implements CommandExecutor {

    private final MellowDuels plugin;

    public DuelCommand(MellowDuels plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.openInventory(KitSelectGui.build(plugin));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "challenge" -> {
                if (args.length < 3) {
                    player.sendMessage("Usage: /duel challenge <player> <kit>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.getConfigManager().message("player-not-found"));
                    return true;
                }
                String result = plugin.getRequestManager().challenge(player, target, args[2]);
                if (result.equals("already-in-duel")) player.sendMessage(plugin.getConfigManager().message("already-in-duel"));
                return true;
            }
            case "accept" -> {
                if (!plugin.getRequestManager().accept(player)) {
                    player.sendMessage("§cYou have no pending duel request to accept.");
                }
                return true;
            }
            case "deny" -> {
                if (!plugin.getRequestManager().deny(player)) {
                    player.sendMessage("§cYou have no pending duel request to deny.");
                }
                return true;
            }
            case "queue" -> {
                if (args.length < 2) {
                    player.sendMessage("Usage: /duel queue <kit>");
                    return true;
                }
                String result = plugin.getQueueManager().join(player, args[1]);
                if (result.equals("already-in-queue")) player.sendMessage(plugin.getConfigManager().message("already-in-queue"));
                if (result.equals("already-in-duel")) player.sendMessage(plugin.getConfigManager().message("already-in-duel"));
                if (result.equals("invalid-kit")) player.sendMessage("§cUnknown kit: " + args[1]);
                return true;
            }
            case "leave" -> {
                plugin.getQueueManager().leave(player);
                return true;
            }
            case "spectate" -> {
                if (args.length < 2) {
                    player.sendMessage("Usage: /duel spectate <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.getConfigManager().message("player-not-found"));
                    return true;
                }
                var session = plugin.getDuelManager().getSession(target.getUniqueId());
                if (session == null) {
                    player.sendMessage("§cThat player is not currently in a duel.");
                    return true;
                }
                plugin.getSpectatorManager().startSpectating(player, session.getId(), session.getArena());
                player.sendMessage(plugin.getConfigManager().message("spectating-started").replace("%player%", target.getName()));
                return true;
            }
            case "stats" -> {
                Player target = args.length >= 2 ? Bukkit.getPlayer(args[1]) : player;
                if (target == null) {
                    player.sendMessage(plugin.getConfigManager().message("player-not-found"));
                    return true;
                }
                StatsManager.PlayerStats stats = plugin.getStatsManager().getStats(target.getUniqueId(), "__all__");
                player.sendMessage("§b" + target.getName() + "'s stats: §f"
                        + stats.wins + "W / " + stats.losses + "L (" + String.format("%.1f", stats.winRate()) + "%), "
                        + "streak: " + stats.currentStreak + " (best " + stats.bestStreak + ")");
                return true;
            }
            case "top" -> {
                String kit = args.length >= 2 ? args[1] : "__all__";
                var top = plugin.getStatsManager().topByWins(kit, 10);
                player.sendMessage("§b--- Top players (" + kit + ") ---");
                int rank = 1;
                for (Object[] row : top) {
                    String name = Bukkit.getOfflinePlayer(java.util.UUID.fromString((String) row[0])).getName();
                    player.sendMessage("§f" + rank++ + ". " + name + " - " + row[1] + " wins");
                }
                return true;
            }
            case "gui" -> {
                player.openInventory(KitSelectGui.build(plugin));
                return true;
            }
            default -> {
                player.sendMessage("Unknown subcommand. See /duel for the GUI, or use challenge/accept/deny/queue/leave/spectate/stats/top.");
                return true;
            }
        }
    }
}
