package net.mellowsmp.duels.commands;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.Arena;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;

import java.util.HashMap;
import java.util.Map;

public class DuelAdminCommand implements CommandExecutor {

    private final MellowDuels plugin;
    // Simple two-corner selection tool state, per admin, for capturing arena templates
    private final Map<java.util.UUID, org.bukkit.Location> corner1 = new HashMap<>();
    private final Map<java.util.UUID, org.bukkit.Location> corner2 = new HashMap<>();

    public DuelAdminCommand(MellowDuels plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /dueladmin <arena|kit|reload> ...");
            return true;
        }

        switch (args[0].toLowerCase()) {
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
                return true;
            }
            default -> {
                sender.sendMessage("Unknown subcommand.");
                return true;
            }
        }
    }

    private boolean handleArena(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /dueladmin arena <pos1|pos2|capture|generate|list|delete>");
            return true;
        }
        if (!(sender instanceof Player player) && !args[1].equalsIgnoreCase("list") && !args[1].equalsIgnoreCase("generate")) {
            sender.sendMessage("Only players can select positions in-world.");
            return true;
        }

        switch (args[1].toLowerCase()) {
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
                if (args.length < 2 + 1) {
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
                String name = args[2];
                // Spawn points default to each corner's ground level, offset toward the centre;
                // admins can refine exact spawn coordinates directly in the generated meta file.
                BlockVector relSpawnA = new BlockVector(1, 1, 1);
                BlockVector relSpawnB = new BlockVector(
                        Math.abs(c2.getBlockX() - c1.getBlockX()) - 1, 1, Math.abs(c2.getBlockZ() - c1.getBlockZ()) - 1);
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
                int count = args.length >= 4 ? Integer.parseInt(args[3]) : 1;
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
            default -> {
                sender.sendMessage("Unknown arena subcommand.");
                return true;
            }
        }
    }
}
