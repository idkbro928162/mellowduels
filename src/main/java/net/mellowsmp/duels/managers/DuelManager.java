package net.mellowsmp.duels.managers;

import net.mellowsmp.duels.BasedDuels;
import net.mellowsmp.duels.models.Arena;
import net.mellowsmp.duels.models.DuelSession;
import net.mellowsmp.duels.models.Kit;
import net.mellowsmp.duels.models.PlayerState;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the lifecycle of every active DuelSession: reserving an arena,
 * teleporting and freezing players through a countdown, starting combat,
 * detecting a winner, restoring both players, and releasing the arena for
 * reuse. This is the central coordinator the rest of the plugin talks to.
 */
public class DuelManager {

    private final BasedDuels plugin;
    private final ArenaManager arenaManager;
    private final KitManager kitManager;
    private final PlayerStateManager playerStateManager;
    private final StatsManager statsManager;
    private final SpectatorManager spectatorManager;
    private final ConfigManager configManager;

    private final Map<String, DuelSession> sessionsById = new ConcurrentHashMap<>();
    private final Map<UUID, String> sessionIdByPlayer = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> countdownTasks = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> timeoutTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> disconnectTasks = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerState> disconnectedDuelStates = new ConcurrentHashMap<>();

    public DuelManager(BasedDuels plugin, ArenaManager arenaManager, KitManager kitManager,
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
        Objects.requireNonNull(a, "player a");
        Objects.requireNonNull(b, "player b");
        if (kit == null || a.getUniqueId().equals(b.getUniqueId()) || !a.isOnline() || !b.isOnline()) {
            return false;
        }
        if (isInDuel(a.getUniqueId()) || isInDuel(b.getUniqueId())
                || spectatorManager.isSpectating(a.getUniqueId())
                || spectatorManager.isSpectating(b.getUniqueId())) {
            a.sendMessage(configManager.message("already-in-duel"));
            b.sendMessage(configManager.message("already-in-duel"));
            return false;
        }

        Arena arena = arenaManager.reserveArena(preferredTemplate);
        if (arena == null) {
            a.sendMessage(configManager.message("no-arenas-available"));
            b.sendMessage(configManager.message("no-arenas-available"));
            return false;
        }
        if (arena.getSpawnA() == null || arena.getSpawnB() == null) {
            plugin.getLogger().severe("Arena '" + arena.getId() + "' has missing participant spawns.");
            arenaManager.resetAndRelease(arena);
            a.sendMessage(configManager.message("no-arenas-available"));
            b.sendMessage(configManager.message("no-arenas-available"));
            return false;
        }

        if (plugin.getQueueManager() != null) {
            plugin.getQueueManager().removeSilently(a.getUniqueId());
            plugin.getQueueManager().removeSilently(b.getUniqueId());
        }
        if (plugin.getRequestManager() != null) {
            plugin.getRequestManager().removeRequestsFor(a.getUniqueId());
            plugin.getRequestManager().removeRequestsFor(b.getUniqueId());
        }

        DuelSession session = new DuelSession(a.getUniqueId(), b.getUniqueId(), arena, kit);
        playerStateManager.save(a);
        playerStateManager.save(b);

        try {
            arena.setState(Arena.State.IN_USE);
            arena.setCurrentSessionId(session.getId());
            sessionsById.put(session.getId(), session);
            sessionIdByPlayer.put(a.getUniqueId(), session.getId());
            sessionIdByPlayer.put(b.getUniqueId(), session.getId());

            prepareForDuel(a, arena.getSpawnA(), kit);
            prepareForDuel(b, arena.getSpawnB(), kit);
        } catch (RuntimeException ex) {
            sessionsById.remove(session.getId());
            sessionIdByPlayer.remove(a.getUniqueId(), session.getId());
            sessionIdByPlayer.remove(b.getUniqueId(), session.getId());
            playerStateManager.restore(a);
            playerStateManager.restore(b);
            arenaManager.resetAndRelease(arena);
            plugin.getLogger().severe("Could not prepare duel: " + ex.getMessage());
            return false;
        }

        a.sendMessage(configManager.message("duel-starting"));
        b.sendMessage(configManager.message("duel-starting"));

        runCountdown(session, a, b);
        return true;
    }

    private void prepareForDuel(Player player, Location spawn, Kit kit) {
        if (!player.teleport(spawn)) {
            throw new IllegalStateException("Teleport failed for " + player.getName());
        }
        kitManager.applyKit(player, kit);
        player.setGameMode(GameMode.ADVENTURE); // frozen during countdown; switched to kit mode on start
        player.setWalkSpeed(0f);
        player.setFallDistance(0);
        player.setFireTicks(0);
        player.setInvulnerable(true);
        player.setCollidable(false);
    }

    private void runCountdown(DuelSession session, Player a, Player b) {
        int seconds = configManager.countdownSeconds();
        String title = configManager.raw().getString("countdown.title", "&e&lDUEL STARTING");
        String subtitleTemplate = configManager.raw().getString("countdown.subtitle", "&f%seconds%");

        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                DuelSession current = sessionsById.get(session.getId());
                if (current != session || session.getPhase() != DuelSession.Phase.COUNTDOWN) {
                    countdownTasks.remove(session.getId());
                    cancel();
                    return;
                }
                Player pa = Bukkit.getPlayer(session.getPlayerA());
                Player pb = Bukkit.getPlayer(session.getPlayerB());
                if (pa == null || pb == null) {
                    // A disconnect timer owns cleanup. Pause so reconnecting players
                    // resume the same countdown instead of starting combat offline.
                    return;
                }
                if (remaining <= 0) {
                    beginCombat(session, pa, pb);
                    countdownTasks.remove(session.getId());
                    cancel();
                    return;
                }
                String subtitle = configManager.color(subtitleTemplate.replace("%seconds%", String.valueOf(remaining)));
                Title countdownTitle = Title.title(configManager.component(title), configManager.component(subtitle));
                pa.showTitle(countdownTitle);
                pb.showTitle(countdownTitle);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        countdownTasks.put(session.getId(), task);
    }

    private void beginCombat(DuelSession session, Player a, Player b) {
        session.setPhase(DuelSession.Phase.ACTIVE);
        a.setGameMode(session.getKit().getGameMode());
        b.setGameMode(session.getKit().getGameMode());
        a.setWalkSpeed(0.2f);
        b.setWalkSpeed(0.2f);
        a.setInvulnerable(false);
        b.setInvulnerable(false);
        a.setCollidable(true);
        b.setCollidable(true);

        if (configManager.maxDuelDurationSeconds() > 0) {
            long delayTicks = configManager.maxDuelDurationSeconds() * 20L;
            BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    timeoutTasks.remove(session.getId());
                    DuelSession current = sessionsById.get(session.getId());
                    if (current != null && current.getPhase() == DuelSession.Phase.ACTIVE) {
                        UUID winner = session.decideWinnerByDamage();
                        if (winner == null) {
                            cancelDuel(session.getId());
                        } else {
                            endDuel(session.getId(), winner);
                        }
                    }
                }
            }.runTaskLater(plugin, delayTicks);
            timeoutTasks.put(session.getId(), task);
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
        DuelSession session = sessionsById.get(sessionId);
        if (session == null || winnerUuid == null || !session.hasPlayer(winnerUuid)) {
            return;
        }
        finishDuel(session, winnerUuid, true);
    }

    /** Cancels a match without awarding a result, used for shutdowns and non-forfeit disconnects. */
    public void cancelDuel(String sessionId) {
        DuelSession session = sessionsById.get(sessionId);
        if (session != null) {
            finishDuel(session, null, false);
        }
    }

    private void finishDuel(DuelSession session, UUID winnerUuid, boolean recordResult) {
        if (!sessionsById.remove(session.getId(), session)) {
            return;
        }
        session.setPhase(DuelSession.Phase.ENDING);
        session.setWinner(winnerUuid);

        cancelTask(countdownTasks.remove(session.getId()));
        cancelTask(timeoutTasks.remove(session.getId()));
        cancelDisconnectTask(session.getPlayerA());
        cancelDisconnectTask(session.getPlayerB());

        UUID loserUuid = winnerUuid == null ? null : session.getOpponent(winnerUuid);
        sessionIdByPlayer.remove(session.getPlayerA());
        sessionIdByPlayer.remove(session.getPlayerB());

        Player winner = winnerUuid == null ? null : Bukkit.getPlayer(winnerUuid);
        Player loser = loserUuid != null ? Bukkit.getPlayer(loserUuid) : null;

        if (winner != null) {
            playerStateManager.restore(winner);
            winner.sendMessage(configManager.message("duel-ended-win")
                    .replace("%opponent%", loser != null ? loser.getName() : "opponent")
                    .replace("%kit%", session.getKit().getDisplayName()));
        }
        if (loser != null && !loser.getUniqueId().equals(winnerUuid)) {
            playerStateManager.restore(loser);
            loser.sendMessage(configManager.message("duel-ended-loss")
                    .replace("%opponent%", winner != null ? winner.getName() : "opponent")
                    .replace("%kit%", session.getKit().getDisplayName()));
        }

        if (winnerUuid == null) {
            restoreIfOnline(session.getPlayerA());
            restoreIfOnline(session.getPlayerB());
            sendIfOnline(session.getPlayerA(), configManager.message("duel-cancelled"));
            sendIfOnline(session.getPlayerB(), configManager.message("duel-cancelled"));
        }

        disconnectedDuelStates.remove(session.getPlayerA());
        disconnectedDuelStates.remove(session.getPlayerB());

        if (recordResult && winnerUuid != null && loserUuid != null) {
            statsManager.recordResult(winnerUuid, loserUuid, session.getKit().getId(), session.getCombatDurationMillis());
        }

        spectatorManager.stopAllSpectatorsOf(session.getId());
        arenaManager.resetAndRelease(session.getArena());
    }

    /**
     * Safely snapshots the in-match state and restores the player's original
     * state before Bukkit saves their logout data.
     */
    public void handleDisconnect(Player player) {
        UUID uuid = player.getUniqueId();
        DuelSession session = getSession(uuid);
        if (session == null) return;

        disconnectedDuelStates.put(uuid, PlayerState.capture(player));
        playerStateManager.restore(player);

        if (!configManager.forfeitOnQuit()) {
            cancelDuel(session.getId());
            return;
        }

        UUID opponent = session.getOpponent(uuid);
        Player opponentPlayer = opponent == null ? null : Bukkit.getPlayer(opponent);
        if (opponentPlayer != null) {
            opponentPlayer.sendMessage(configManager.message("opponent-disconnected")
                    .replace("%player%", player.getName())
                    .replace("%seconds%", String.valueOf(configManager.forfeitGraceSeconds())));
        }

        Runnable forfeit = () -> {
            disconnectTasks.remove(uuid);
            DuelSession current = getSession(uuid);
            if (current != null && current.getId().equals(session.getId())) {
                Player op = opponent == null ? null : Bukkit.getPlayer(opponent);
                if (op != null) {
                    op.sendMessage(configManager.message("forfeit-quit").replace("%player%", player.getName()));
                }
                if (opponent != null) {
                    endDuel(current.getId(), opponent);
                } else {
                    cancelDuel(current.getId());
                }
            }
        };

        int graceSeconds = configManager.forfeitGraceSeconds();
        if (graceSeconds == 0) {
            forfeit.run();
        } else {
            BukkitTask old = disconnectTasks.put(uuid,
                    Bukkit.getScheduler().runTaskLater(plugin, forfeit, graceSeconds * 20L));
            cancelTask(old);
        }
    }

    /** Restores the exact in-match state when a player returns during the grace period. */
    public void handleReconnect(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask disconnectTask = disconnectTasks.remove(uuid);
        PlayerState duelState = disconnectedDuelStates.remove(uuid);
        DuelSession session = getSession(uuid);
        if (disconnectTask == null || duelState == null || session == null) {
            return;
        }
        cancelTask(disconnectTask);

        playerStateManager.save(player);
        duelState.restore(player);
        if (!session.getArena().contains(player.getLocation())) {
            Location spawn = session.getPlayerA().equals(uuid)
                    ? session.getArena().getSpawnA() : session.getArena().getSpawnB();
            player.teleport(spawn);
        }
        if (session.getPhase() == DuelSession.Phase.COUNTDOWN) {
            player.setGameMode(GameMode.ADVENTURE);
            player.setWalkSpeed(0f);
            player.setInvulnerable(true);
            player.setCollidable(false);
        } else {
            player.setInvulnerable(false);
            player.setCollidable(true);
        }

        UUID opponent = session.getOpponent(uuid);
        Player opponentPlayer = opponent == null ? null : Bukkit.getPlayer(opponent);
        if (opponentPlayer != null) {
            opponentPlayer.sendMessage(configManager.message("opponent-reconnected")
                    .replace("%player%", player.getName()));
        }
    }

    public void endAllDuelsForShutdown() {
        for (String id : Map.copyOf(sessionsById).keySet()) {
            cancelDuel(id);
        }
    }

    private void restoreIfOnline(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            playerStateManager.restore(player);
        }
    }

    private void sendIfOnline(UUID uuid, String message) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.sendMessage(message);
        }
    }

    private void cancelDisconnectTask(UUID uuid) {
        cancelTask(disconnectTasks.remove(uuid));
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}
