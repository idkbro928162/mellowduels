package net.mellowsmp.duels.commands;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.gui.KitSelectGui;
import net.mellowsmp.duels.gui.KitSelectHolder;
import net.mellowsmp.duels.managers.StatsManager;
import net.mellowsmp.duels.models.DuelSession;
import net.mellowsmp.duels.models.Kit;
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
            KitSelectGui.open(plugin, player, KitSelectHolder.Mode.QUEUE, null);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                player.sendMessage(plugin.getConfigManager().message("help"));
                return true;
            }
            case "challenge" -> {
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().message("usage-challenge"));
                    return true;
                }
                return handleChallenge(player, args[1], args.length >= 3 ? args[2] : null);
            }
            case "accept" -> {
                UUID expected = null;
                if (args.length >= 2) {
                    Player challenger = Bukkit.getPlayerExact(args[1]);
                    if (challenger == null) {
                        player.sendMessage(plugin.getConfigManager().message("player-not-found"));
                        return true;
                    }
                    expected = challenger.getUniqueId();
                }
                if (!plugin.getRequestManager().accept(player, expected)) {
                    player.sendMessage(plugin.getConfigManager().message("no-pending-request"));
                }
                return true;
            }
            case "deny", "decline" -> {
                if (!plugin.getRequestManager().deny(player)) {
                    player.sendMessage(plugin.getConfigManager().message("no-pending-request"));
                }
                return true;
            }
            case "queue" -> {
                if (args.length < 2) {
                    KitSelectGui.open(plugin, player, KitSelectHolder.Mode.QUEUE, null);
                    return true;
                }
                handleJoinResult(player, plugin.getQueueManager().join(player, args[1]), args[1]);
                return true;
            }
            case "leave", "forfeit" -> {
                if (plugin.getDuelManager().isInDuel(player.getUniqueId())) {
                    plugin.getDuelManager().forfeit(player.getUniqueId());
                    return true;
                }
                plugin.getQueueManager().leave(player);
                return true;
            }
            case "spectate" -> {
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().message("usage-spectate"));
                    return true;
                }
                if (!plugin.getConfigManager().spectatorEnabled()) {
                    player.sendMessage(plugin.getConfigManager().message("spectating-disabled"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.getConfigManager().message("player-not-found"));
                    return true;
                }
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    player.sendMessage(plugin.getConfigManager().message("cannot-spectate"));
                    return true;
                }
                DuelSession session = plugin.getDuelManager().getSession(target.getUniqueId());
                if (session == null) {
                    player.sendMessage(plugin.getConfigManager().message("spectating-not-in-duel"));
                    return true;
                }
                Player a = Bukkit.getPlayer(session.getPlayerA());
                Player b = Bukkit.getPlayer(session.getPlayerB());
                if (!plugin.getSpectatorManager().startSpectating(player, session.getId(), session.getArena(), a, b)) {
                    player.sendMessage(plugin.getConfigManager().message("cannot-spectate"));
                    return true;
                }
                player.sendMessage(plugin.getConfigManager().message("spectating-started", "%player%", target.getName()));
                return true;
            }
            case "stats" -> {
                OfflinePlayer target = player;
                if (args.length >= 2) {
                    Player online = Bukkit.getPlayerExact(args[1]);
                    if (online != null) {
                        target = online;
                    } else {
                        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(args[1]);
                        if (cached == null || cached.getUniqueId() == null) {
                            player.sendMessage(plugin.getConfigManager().message("player-not-found"));
                            return true;
                        }
                        target = cached;
                    }
                }
                StatsManager.PlayerStats stats = plugin.getStatsManager().getStats(target.getUniqueId(), "__all__");
                String name = target.getName() != null ? target.getName() : args.length >= 2 ? args[1] : player.getName();
                player.sendMessage("§b" + name + "'s stats: §f"
                        + stats.wins + "W / " + stats.losses + "L (" + String.format(Locale.US, "%.1f", stats.winRate()) + "%), "
                        + stats.kills + "K / " + stats.deaths + "D, "
                        + "streak: " + stats.currentStreak + " (best " + stats.bestStreak + ")");
                return true;
            }
            case "top" -> {
                String kitArg = args.length >= 2 ? args[1] : "__all__";
                Kit kit = plugin.getKitManager().getKit(kitArg);
                String kitKey = kit != null ? kit.getId() : ("__all__".equalsIgnoreCase(kitArg) ? "__all__" : kitArg);
                String kitLabel = kit != null ? kit.getDisplayName() : ("__all__".equals(kitKey) ? "overall" : kitKey);
                var top = plugin.getStatsManager().topByWins(kitKey, 10);
                player.sendMessage("§b--- Top players (" + kitLabel + ") ---");
                if (top.isEmpty()) {
                    player.sendMessage("§7No recorded matches yet.");
                }
                int rank = 1;
                for (StatsManager.LeaderboardEntry row : top) {
                    player.sendMessage("§f" + rank++ + ". " + row.name() + " - " + row.wins() + " wins");
                }
                return true;
            }
            case "gui" -> {
                KitSelectGui.open(plugin, player, KitSelectHolder.Mode.QUEUE, null);
                return true;
            }
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target != null) {
                    return handleChallenge(player, args[0], args.length >= 2 ? args[1] : null);
                }
                player.sendMessage(plugin.getConfigManager().message("help"));
                return true;
            }
        }
    }

    private boolean handleChallenge(Player player, String targetName, String kitId) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(plugin.getConfigManager().message("player-not-found"));
            return true;
        }
        if (kitId == null) {
            KitSelectGui.open(plugin, player, KitSelectHolder.Mode.CHALLENGE, target.getUniqueId());
            return true;
        }
        handleJoinResult(player, plugin.getRequestManager().challenge(player, target, kitId), kitId);
        return true;
    }

    public void handleJoinResult(Player player, String result, String kitId) {
        switch (result) {
            case "already-in-duel" -> player.sendMessage(plugin.getConfigManager().message("already-in-duel"));
            case "opponent-in-duel" -> player.sendMessage(plugin.getConfigManager().message("opponent-in-duel"));
            case "already-in-queue" -> {
                String queued = plugin.getQueueManager().getQueuedKit(player.getUniqueId());
                Kit kit = queued != null ? plugin.getKitManager().getKit(queued) : plugin.getKitManager().getKit(kitId);
                player.sendMessage(plugin.getConfigManager().message("already-in-queue",
                        "%kit%", kit != null ? kit.getDisplayName() : (queued != null ? queued : kitId)));
            }
            case "invalid-kit" -> player.sendMessage(plugin.getConfigManager().message("invalid-kit", "%kit%", kitId));
            case "cannot-challenge-self" -> player.sendMessage(plugin.getConfigManager().message("cannot-challenge-self"));
            case "duplicate-request" -> player.sendMessage(plugin.getConfigManager().message("duplicate-request"));
            case "cannot-spectate" -> player.sendMessage(plugin.getConfigManager().message("cannot-spectate"));
            default -> {
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of(
                    "challenge", "accept", "deny", "queue", "leave", "forfeit", "spectate", "stats", "top", "gui", "help"));
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("challenge") || sub.equals("accept") || sub.equals("spectate") || sub.equals("stats")) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
            }
            if (sub.equals("queue") || sub.equals("top")) {
                return filter(new ArrayList<>(plugin.getKitManager().getKitNames()), args[1]);
            }
            if (Bukkit.getPlayerExact(args[0]) != null) {
                return filter(new ArrayList<>(plugin.getKitManager().getKitNames()), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("challenge")) {
            return filter(new ArrayList<>(plugin.getKitManager().getKitNames()), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }
}
