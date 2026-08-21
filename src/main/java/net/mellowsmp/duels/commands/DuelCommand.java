package net.mellowsmp.duels.commands;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.gui.KitSelectGui;
import net.mellowsmp.duels.managers.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class DuelCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "challenge", "accept", "deny", "queue", "leave", "spectate", "stats", "top", "gui");

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

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "challenge" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /duel challenge <player> <kit>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.getConfigManager().message("player-not-found"));
                    return true;
                }
                String result = plugin.getRequestManager().challenge(player, target, args[2]);
                switch (result) {
                    case "already-in-duel" -> player.sendMessage(plugin.getConfigManager().message("already-in-duel"));
                    case "self" -> player.sendMessage("§cYou cannot challenge yourself.");
                    case "spectating" -> player.sendMessage("§cOne of you is currently spectating.");
                    case "in-queue" -> player.sendMessage("§cLeave the queue first (/duel leave).");
                    case "duplicate-request" -> player.sendMessage("§cThat player already has a pending challenge.");
                    case "invalid-kit" -> player.sendMessage("§cUnknown kit: " + args[2]);
                    default -> {
                    }
                }
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
                    player.sendMessage("§cUsage: /duel queue <kit>");
                    return true;
                }
                String result = plugin.getQueueManager().join(player, args[1]);
                switch (result) {
                    case "already-in-queue" -> player.sendMessage(plugin.getConfigManager().message("already-in-queue")
                            .replace("%kit%", args[1]));
                    case "already-in-duel" -> player.sendMessage(plugin.getConfigManager().message("already-in-duel"));
                    case "spectating" -> player.sendMessage("§cLeave spectator mode before queuing.");
                    case "invalid-kit" -> player.sendMessage("§cUnknown kit: " + args[1]);
                    default -> {
                    }
                }
                return true;
            }
            case "leave" -> {
                if (plugin.getSpectatorManager().isSpectating(player.getUniqueId())) {
                    plugin.getSpectatorManager().stopSpectating(player);
                    player.sendMessage("§aStopped spectating.");
                    return true;
                }
                if (!plugin.getQueueManager().isQueued(player.getUniqueId())) {
                    player.sendMessage("§cYou are not in a queue.");
                    return true;
                }
                plugin.getQueueManager().leave(player);
                return true;
            }
            case "spectate" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /duel spectate <player>");
                    return true;
                }
                if (!plugin.getConfigManager().spectatorEnabled()) {
                    player.sendMessage("§cSpectating is disabled.");
                    return true;
                }
                if (plugin.getDuelManager().isInDuel(player.getUniqueId())) {
                    player.sendMessage(plugin.getConfigManager().message("already-in-duel"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.getConfigManager().message("player-not-found"));
                    return true;
                }
                var session = plugin.getDuelManager().getSession(target.getUniqueId());
                if (session == null) {
                    player.sendMessage("§cThat player is not currently in a duel.");
                    return true;
                }
                if (!plugin.getSpectatorManager().startSpectating(player, session.getId(), session.getArena())) {
                    player.sendMessage("§cCould not start spectating.");
                    return true;
                }
                player.sendMessage(plugin.getConfigManager().message("spectating-started")
                        .replace("%player%", target.getName()));
                return true;
            }
            case "stats" -> {
                OfflinePlayer target;
                if (args.length >= 2) {
                    Player online = Bukkit.getPlayerExact(args[1]);
                    target = online != null ? online : Bukkit.getOfflinePlayer(args[1]);
                    if (target.getName() == null && !target.hasPlayedBefore()) {
                        player.sendMessage(plugin.getConfigManager().message("player-not-found"));
                        return true;
                    }
                } else {
                    target = player;
                }
                StatsManager.PlayerStats stats = plugin.getStatsManager().getStats(target.getUniqueId(), "__all__");
                String name = target.getName() != null ? target.getName() : args[1];
                player.sendMessage("§b" + name + "'s stats: §f"
                        + stats.wins + "W / " + stats.losses + "L ("
                        + String.format(Locale.US, "%.1f", stats.winRate()) + "%), "
                        + "streak: " + stats.currentStreak + " (best " + stats.bestStreak + ")");
                return true;
            }
            case "top" -> {
                String kit = args.length >= 2 ? args[1] : "__all__";
                var top = plugin.getStatsManager().topByWins(kit, 10);
                player.sendMessage("§b--- Top players (" + kit + ") ---");
                if (top.isEmpty()) {
                    player.sendMessage("§7No stats recorded yet.");
                    return true;
                }
                int rank = 1;
                for (Object[] row : top) {
                    String name = Bukkit.getOfflinePlayer(UUID.fromString((String) row[0])).getName();
                    player.sendMessage("§f" + rank++ + ". " + (name != null ? name : row[0]) + " - " + row[1] + " wins");
                }
                return true;
            }
            case "gui" -> {
                player.openInventory(KitSelectGui.build(plugin));
                return true;
            }
            default -> {
                // Treat "/duel <player> [kit]" as a shorthand challenge
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target != null) {
                    String kitId = args.length >= 2 ? args[1] : defaultKitId();
                    if (kitId == null) {
                        player.sendMessage("§cNo kits are loaded. Ask an admin to check kits.yml.");
                        return true;
                    }
                    String result = plugin.getRequestManager().challenge(player, target, kitId);
                    switch (result) {
                        case "already-in-duel" -> player.sendMessage(plugin.getConfigManager().message("already-in-duel"));
                        case "self" -> player.sendMessage("§cYou cannot challenge yourself.");
                        case "spectating" -> player.sendMessage("§cOne of you is currently spectating.");
                        case "in-queue" -> player.sendMessage("§cLeave the queue first (/duel leave).");
                        case "duplicate-request" -> player.sendMessage("§cThat player already has a pending challenge.");
                        case "invalid-kit" -> player.sendMessage("§cUnknown kit: " + kitId);
                        default -> {
                        }
                    }
                    return true;
                }
                player.sendMessage("§cUnknown subcommand. Use challenge/accept/deny/queue/leave/spectate/stats/top/gui.");
                return true;
            }
        }
    }

    private String defaultKitId() {
        var names = plugin.getKitManager().getKitNames();
        return names.isEmpty() ? null : names.iterator().next();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(SUBCOMMANDS);
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "challenge", "spectate", "stats" -> filter(
                        Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()),
                        args[1]);
                case "queue", "top" -> filter(new ArrayList<>(plugin.getKitManager().getKitNames()), args[1]);
                default -> {
                    // /duel <player> <kit>
                    if (Bukkit.getPlayerExact(args[0]) != null) {
                        yield filter(new ArrayList<>(plugin.getKitManager().getKitNames()), args[1]);
                    }
                    yield List.of();
                }
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("challenge")) {
            return filter(new ArrayList<>(plugin.getKitManager().getKitNames()), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).sorted().toList();
    }
}
