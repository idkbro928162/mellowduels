package net.mellowsmp.duels.commands;

import net.mellowsmp.duels.MellowDuels;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class DuelAdminCommand implements CommandExecutor, TabCompleter {

    private final MellowDuels plugin;
    private final Map<UUID, org.bukkit.Location> corner1 = new HashMap<>();
    private final Map<UUID, org.bukkit.Location> corner2 = new HashMap<>();

    public DuelAdminCommand(MellowDuels plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /dueladmin <arena|kit|reload>");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.getConfigManager().reload();
                plugin.getKitManager().loadKits();
                sender.sendMessage("§aMellowDuels configuration reloaded.");
                return true;
            }
            case "arena" -> {
                return handleArena(sender, args);
            }
            case "kit" -> {
                sender.sendMessage("§eKits are defined in kits.yml — edit that file and run /dueladmin reload.");
                sender.sendMessage("§7Loaded kits: " + String.join(", ", plugin.getKitManager().getKitNames()));
                return true;
            }
            default -> {
                sender.sendMessage("Unknown subcommand. Usage: /dueladmin <arena|kit|reload>");
                return true;
            }
        }
    }

    private boolean handleArena(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /dueladmin arena <pos1|pos2|capture|generate|list|delete>");
            return true;
        }
        if (!(sender instanceof Player) && !args[1].equalsIgnoreCase("list")
                && !args[1].equalsIgnoreCase("generate") && !args[1].equalsIgnoreCase("delete")) {
            sender.sendMessage("Only players can select positions in-world.");
            return true;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "pos1" -> {
                Player p = (Player) sender;
                corner1.put(p.getUniqueId(), p.getLocation());
                p.sendMessage("§aArena corner 1 set to your current location.");
                return true;
            }
            case "pos2" -> {
                Player p = (Player) sender;
                corner2.put(p.getUniqueId(), p.getLocation());
                p.sendMessage("§aArena corner 2 set to your current location.");
                return true;
            }
            case "capture" -> {
                if (args.length < 3) {
                    sender.sendMessage("Usage: /dueladmin arena capture <name>");
                    return true;
                }
                Player p = (Player) sender;
                org.bukkit.Location c1 = corner1.get(p.getUniqueId());
                org.bukkit.Location c2 = corner2.get(p.getUniqueId());
                if (c1 == null || c2 == null) {
                    p.sendMessage("§cSet both pos1 and pos2 first.");
                    return true;
                }
                if (c1.getWorld() == null || c2.getWorld() == null || !c1.getWorld().equals(c2.getWorld())) {
                    p.sendMessage("§cBoth corners must be in the same world.");
                    return true;
                }
                String name = args[2];
                BlockVector relSpawnA = new BlockVector(1, 1, 1);
                BlockVector relSpawnB = new BlockVector(
                        Math.max(1, Math.abs(c2.getBlockX() - c1.getBlockX()) - 1),
                        1,
                        Math.max(1, Math.abs(c2.getBlockZ() - c1.getBlockZ()) - 1));
                boolean ok = plugin.getArenaManager().captureTemplate(name, c1, c2, relSpawnA, relSpawnB);
                p.sendMessage(ok ? "§aArena template '" + name + "' captured." : "§cFailed to capture arena template.");
                return true;
            }
            case "generate" -> {
                if (args.length < 3) {
                    sender.sendMessage("Usage: /dueladmin arena generate <template> [count]");
                    return true;
                }
                String template = args[2];
                int count = 1;
                if (args.length >= 4) {
                    try {
                        count = Math.max(1, Math.min(32, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ex) {
                        sender.sendMessage("§cCount must be a number.");
                        return true;
                    }
                }
                int made = 0;
                for (int i = 0; i < count; i++) {
                    if (plugin.getArenaManager().generateCopy(template) != null) made++;
                }
                sender.sendMessage("§aGenerated " + made + "/" + count + " copies of template '" + template + "'.");
                return true;
            }
            case "list" -> {
                sender.sendMessage("§bTemplates: " + String.join(", ", plugin.getArenaManager().getTemplateNames()));
                sender.sendMessage("§bTotal arena copies: " + plugin.getArenaManager().getArenaCount());
                return true;
            }
            case "delete" -> {
                if (args.length < 3) {
                    sender.sendMessage("Usage: /dueladmin arena delete <template>");
                    return true;
                }
                boolean ok = plugin.getArenaManager().deleteTemplate(args[2]);
                sender.sendMessage(ok
                        ? "§aDeleted template '" + args[2] + "' and its unused copies."
                        : "§cCould not delete that template (unknown, or a copy is in use).");
                return true;
            }
            default -> {
                sender.sendMessage("Unknown arena subcommand.");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("arena", "kit", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("arena")) {
            return filter(List.of("pos1", "pos2", "capture", "generate", "list", "delete"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("arena")
                && (args[1].equalsIgnoreCase("generate") || args[1].equalsIgnoreCase("delete"))) {
            return filter(plugin.getArenaManager().getTemplateNames(), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
