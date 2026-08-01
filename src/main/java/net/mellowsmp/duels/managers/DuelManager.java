package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.Arena;
import net.mellowsmp.duels.models.DuelSession;
import net.mellowsmp.duels.models.Kit;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the lifecycle of every active DuelSession: reserving an arena,
 * teleporting and freezing players through a countdown, starting combat,
 * detecting a winner, restoring both players, and releasing the arena for
 * reuse. This is the central coordinator the rest of the plugin talks to.
 */
public class DuelManager {

    private final MellowDuels plugin;
    private final ArenaManager arenaManager;
    private final KitManager kitManager;
    private final PlayerStateManager playerStateManager;
    private final StatsManager statsManager;
    private final SpectatorManager spectatorManager;
    private final ConfigManager configManager;

    private final Map<String, DuelSession> sessionsById = new ConcurrentHashMap<>();
    private final Map<UUID, String> sessionIdByPlayer = new ConcurrentHashMap<>();

    public DuelManager(MellowDuels plugin, ArenaManager arenaManager, KitManager kitManager,
                        PlayerStateManager playerStateManager, StatsManager statsManager,
                        SpectatorManager spectatorManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.kitManager = kitManager;
        this.playerStateManager = playerStateManager;
        this.statsManager = statsManager;
        this.spectatorManager = spectatorManager;
        this.configManager = configManager;
    }

    public boolean isInDuel(UUID uuid) {
        return sessionIdByPlayer.containsKey(uuid);
    }

    public DuelSession getSession(UUID uuid) {
        String id = sessionIdByPlayer.get(uuid);
        return id != null ? sessionsById.get(id) : null;
    }

    public DuelSession getSessionById(String id) {
        return sessionsById.get(id);
    }

    /**
     * Starts a new duel between two players using the given kit. Reserves an
     * arena, saves both players' current state, teleports them in, and begins
     * the countdown. Returns false (and messages the caller) if no arena is
     * available.
     */
    public boolean startDuel(Player a, Player b, Kit kit, String preferredTemplate) {
        Arena arena = arenaManager.reserveArena(preferredTemplate);
        if (arena == null) {
            a.sendMessage(configManager.message("no-arenas-available"));
            b.sendMessage(configManager.message("no-arenas-available"));
            return false;
        }

        DuelSession session = new DuelSession(a.getUniqueId(), b.getUniqueId(), arena, kit);
        arena.setState(Arena.State.IN_USE);
        arena.setCurrentSessionId(session.getId());
        sessionsById.put(session.getId(), session);
        sessionIdByPlayer.put(a.getUniqueId(), session.getId());
        sessionIdByPlayer.put(b.getUniqueId(), session.getId());

        playerStateManager.save(a);
        playerStateManager.save(b);

        prepareForDuel(a, arena.getSpawnA(), kit);
        prepareForDuel(b, arena.getSpawnB(), kit);

        a.sendMessage(configManager.message("duel-starting"));
        b.sendMessage(configManager.message("duel-starting"));

        runCountdown(session, a, b);
        return true;
    }

    private void prepareForDuel(Player player, org.bukkit.Location spawn, Kit kit) {
        player.teleport(spawn);
        kitManager.applyKit(player, kit);
        player.setGameMode(GameMode.ADVENTURE); // frozen during countdown; switched to kit mode on start
        player.setWalkSpeed(0f);
    }

    private void runCountdown(DuelSession session, Player a, Player b) {
        int seconds = configManager.countdownSeconds();
        String title = configManager.raw().getString("countdown.title", "&e&lDUEL STARTING");
        String subtitleTemplate = configManager.raw().getString("countdown.subtitle", "&f%seconds%");

        new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                Player pa = Bukkit.getPlayer(session.getPlayerA());
                Player pb = Bukkit.getPlayer(session.getPlayerB());
                if (pa == null || pb == null) {
                    cancel();
                    return;
                }
                if (remaining <= 0) {
                    beginCombat(session, pa, pb);
                    cancel();
                    return;
                }
                String subtitle = configManager.color(subtitleTemplate.replace("%seconds%", String.valueOf(remaining)));
                pa.showTitle(net.kyori.adventure.title.Title.title(
                        net.kyori.adventure.text.Component.text(configManager.color(title)),
                        net.kyori.adventure.text.Component.text(subtitle)));
                pb.showTitle(net.kyori.adventure.title.Title.title(
                        net.kyori.adventure.text.Component.text(configManager.color(title)),
                        net.kyori.adventure.text.Component.text(subtitle)));
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void beginCombat(DuelSession session, Player a, Player b) {
        session.setPhase(DuelSession.Phase.ACTIVE);
        a.setGameMode(session.getKit().getGameMode());
        b.setGameMode(session.getKit().getGameMode());
        a.setWalkSpeed(0.2f);
        b.setWalkSpeed(0.2f);

        if (configManager.maxDuelDurationSeconds() > 0) {
            long delayTicks = configManager.maxDuelDurationSeconds() * 20L;
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    DuelSession current = sessionsById.get(session.getId());
                    if (current != null && current.getPhase() == DuelSession.Phase.ACTIVE) {
                        endDuel(session.getId(), session.decideWinnerByDamage());
                    }
                }
            }.runTaskLater(plugin, delayTicks);
        }
    }

    /** Called by the combat listener when a player deals damage during an active duel. */
    public void recordDamage(String sessionId, UUID dealer, double amount) {
        DuelSession session = sessionsById.get(sessionId);
        if (session != null) {
            session.addDamage(dealer, amount);
        }
    }

    /** Ends a duel with the given winner (loser is the other participant), restoring both players. */
    public void endDuel(String sessionId, UUID winnerUuid) {
        DuelSession session = sessionsById.remove(sessionId);
        if (session == null) return;
        session.setPhase(DuelSession.Phase.ENDING);
        session.setWinner(winnerUuid);

        UUID loserUuid = session.getOpponent(winnerUuid);
        sessionIdByPlayer.remove(session.getPlayerA());
        sessionIdByPlayer.remove(session.getPlayerB());

        Player winner = Bukkit.getPlayer(winnerUuid);
        Player loser = loserUuid != null ? Bukkit.getPlayer(loserUuid) : null;

        if (winner != null) {
            playerStateManager.restore(winner);
            winner.sendMessage(configManager.message("duel-ended-win")
                    .replace("%opponent%", loser != null ? loser.getName() : "opponent")
                    .replace("%kit%", session.getKit().getDisplayName()));
        }
        if (loser != null) {
            playerStateManager.restore(loser);
            loser.sendMessage(configManager.message("duel-ended-loss")
                    .replace("%opponent%", winner != null ? winner.getName() : "opponent")
                    .replace("%kit%", session.getKit().getDisplayName()));
        }

        if (winnerUuid != null && loserUuid != null) {
            statsManager.recordResult(winnerUuid, loserUuid, session.getKit().getId(), session.getCombatDurationMillis());
        }

        spectatorManager.stopAllSpectatorsOf(session.getId());
        arenaManager.resetAndRelease(session.getArena());
    }

    /** Handles a player disconnecting mid-duel: counts as a forfeit if configured. */
    public void handleDisconnect(UUID uuid) {
        if (!configManager.forfeitOnQuit()) return;
        DuelSession session = getSession(uuid);
        if (session == null) return;
        UUID opponent = session.getOpponent(uuid);
        if (opponent != null) {
            Player op = Bukkit.getPlayer(opponent);
            if (op != null) {
                op.sendMessage(configManager.message("forfeit-quit").replace("%player%",
                        Bukkit.getOfflinePlayer(uuid).getName()));
            }
            endDuel(session.getId(), opponent);
        }
    }

    public void endAllDuelsForShutdown() {
        for (String id : Map.copyOf(sessionsById).keySet()) {
            DuelSession session = sessionsById.get(id);
            if (session != null) {
                endDuel(id, session.getPlayerA()); // arbitrary winner on shutdown, state is restored regardless
            }
        }
    }
}
