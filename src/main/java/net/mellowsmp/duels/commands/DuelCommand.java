package net.mellowsmp.duels.commands;

import net.mellowsmp.duels.BasedDuels;
import net.mellowsmp.duels.gui.KitSelectGui;
import net.mellowsmp.duels.managers.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DuelCommand implements TabExecutor {

    private final BasedDuels plugin;

    public DuelCommand(BasedDuels plugin) {
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
                switch (result) {
                    case "already-in-duel" ->
                            player.sendMessage(plugin.getConfigManager().message("already-in-duel"));
                    case "already-spectating" ->
                            player.sendMessage(plugin.getConfigManager().message("already-spectating"));
                    case "duplicate-request" ->
                            player.sendMessage(plugin.getConfigManager().message("duplicate-request"));
                    case "invalid-kit" ->
                            player.sendMessage(plugin.getConfigManager().message("invalid-kit"));
                    case "self-challenge" ->
                            player.sendMessage(plugin.getConfigManager().message("self-challenge"));
                    default -> {
                        // RequestManager sends the success message.
                    }
                }
                return true;
            }
            case "accept" -> {
                String result = plugin.getRequestManager().accept(player);
                if (result.equals("no-request")) {
                    player.sendMessage(plugin.getConfigManager().message("no-pending-request"));
                } else if (result.equals("expired")) {
                    player.sendMessage(plugin.getConfigManager().message("request-expired"));
                } else if (result.equals("challenger-offline")) {
                    player.sendMessage(plugin.getConfigManager().message("challenger-offline"));
                } else if (result.equals("unavailable")) {
                    player.sendMessage(plugin.getConfigManager().message("request-unavailable"));
                }
                return true;
            }
            case "deny" -> {
                if (!plugin.getRequestManager().deny(player)) {
                    player.sendMessage(plugin.getConfigManager().message("no-pending-request"));
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
                if (result.equals("already-spectating")) player.sendMessage(plugin.getConfigManager().message("already-spectating"));
                if (result.equals("invalid-kit")) player.sendMessage(plugin.getConfigManager().message("invalid-kit"));
                return true;
            }
            case "leave" -> {
                if (plugin.getQueueManager().isQueued(player.getUniqueId())) {
                    plugin.getQueueManager().leave(player);
                } else if (plugin.getSpectatorManager().isSpectating(player.getUniqueId())) {
                    plugin.getSpectatorManager().stopSpectating(player);
                    player.sendMessage(plugin.getConfigManager().message("spectating-stopped"));
                } else {
                    var session = plugin.getDuelManager().getSession(player.getUniqueId());
                    if (session == null) {
                        player.sendMessage(plugin.getConfigManager().message("nothing-to-leave"));
                    } else {
                        var opponent = session.getOpponent(player.getUniqueId());
                        if (opponent != null) {
                            plugin.getDuelManager().endDuel(session.getId(), opponent);
                        }
                    }
                }
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
                if (plugin.getSpectatorManager().startSpectating(player, session.getId(), session.getArena())) {
                    player.sendMessage(plugin.getConfigManager().message("spectating-started")
                            .replace("%player%", target.getName()));
                } else {
                    player.sendMessage(plugin.getConfigManager().message("cannot-spectate"));
                }
                return true;
            }
            case "stats" -> {
                if (!plugin.getStatsManager().isAvailable()) {
                    player.sendMessage(plugin.getConfigManager().message("stats-unavailable"));
                    return true;
                }
                OfflinePlayer target = args.length >= 2 ? Bukkit.getOfflinePlayerIfCached(args[1]) : player;
                if (target == null) {
                    player.sendMessage(plugin.getConfigManager().message("player-not-found"));
                    return true;
                }
                StatsManager.PlayerStats stats = plugin.getStatsManager().getStats(target.getUniqueId(), "__all__");
                String targetName = target.getName() == null ? target.getUniqueId().toString() : target.getName();
                player.sendMessage("§b" + targetName + "'s stats: §f"
                        + stats.wins + "W / " + stats.losses + "L ("
                        + String.format(Locale.US, "%.1f", stats.winRate()) + "%), "
                        + "streak: " + stats.currentStreak + " (best " + stats.bestStreak + ")");
                return true;
            }
            case "top" -> {
                if (!plugin.getStatsManager().isAvailable()) {
                    player.sendMessage(plugin.getConfigManager().message("stats-unavailable"));
                    return true;
                }
                String kit = "__all__";
                if (args.length >= 2) {
                    var configuredKit = plugin.getKitManager().getKit(args[1]);
                    if (configuredKit == null) {
                        player.sendMessage(plugin.getConfigManager().message("invalid-kit"));
                        return true;
                    }
                    kit = configuredKit.getId();
                }
                var top = plugin.getStatsManager().topByWins(kit, 10);
                player.sendMessage("§b--- Top players (" + kit + ") ---");
                int rank = 1;
                for (Object[] row : top) {
                    String name = Bukkit.getOfflinePlayer(java.util.UUID.fromString((String) row[0])).getName();
                    if (name == null) {
                        name = ((String) row[0]).substring(0, 8);
                    }
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return matches(args[0], List.of("challenge", "accept", "deny", "queue", "leave",
                    "spectate", "stats", "top", "gui"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("challenge")
                || args[0].equalsIgnoreCase("spectate"))) {
            return matches(args[1], Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName).toList());
        }
        if ((args.length == 2 && args[0].equalsIgnoreCase("queue"))
                || (args.length == 3 && args[0].equalsIgnoreCase("challenge"))
                || (args.length == 2 && args[0].equalsIgnoreCase("top"))) {
            int index = args.length - 1;
            return matches(args[index], plugin.getKitManager().getKitNames().stream().toList());
        }
        return List.of();
    }

    private List<String> matches(String prefix, List<String> candidates) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(candidate);
            }
        }
        return matches;
    }
}
