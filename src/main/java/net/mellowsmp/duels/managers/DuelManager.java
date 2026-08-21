package net.mellowsmp.duels.managers;

import net.kyori.adventure.title.Title;
import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.Arena;
import net.mellowsmp.duels.models.DuelSession;
import net.mellowsmp.duels.models.Kit;
import net.mellowsmp.duels.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
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
    private final Map<UUID, BukkitTask> reconnectTimers = new ConcurrentHashMap<>();

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
    public synchronized boolean startDuel(Player a, Player b, Kit kit, String preferredTemplate) {
        if (a == null || b == null || kit == null) {
            return false;
        }
        if (a.getUniqueId().equals(b.getUniqueId())) {
            a.sendMessage(configManager.message("cannot-challenge-self"));
            return false;
        }
        if (isInDuel(a.getUniqueId()) || isInDuel(b.getUniqueId())) {
            a.sendMessage(configManager.message("already-in-duel"));
            b.sendMessage(configManager.message("already-in-duel"));
            return false;
        }

        if (plugin.getQueueManager() != null) {
            plugin.getQueueManager().leave(a, true);
            plugin.getQueueManager().leave(b, true);
        }
        if (plugin.getRequestManager() != null) {
            plugin.getRequestManager().clearFor(a.getUniqueId());
            plugin.getRequestManager().clearFor(b.getUniqueId());
        }
        if (spectatorManager.isSpectating(a.getUniqueId())) {
            spectatorManager.stopSpectating(a);
        }
        if (spectatorManager.isSpectating(b.getUniqueId())) {
            spectatorManager.stopSpectating(b);
        }

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

        a.closeInventory();
        b.closeInventory();
        teleportInternal(session, a, arena.spawnOrOrigin(arena.getSpawnA()));
        teleportInternal(session, b, arena.spawnOrOrigin(arena.getSpawnB()));
        prepareForDuel(a, kit);
        prepareForDuel(b, kit);

        a.sendMessage(configManager.message("duel-starting"));
        b.sendMessage(configManager.message("duel-starting"));

        runCountdown(session);
        return true;
    }

    public void teleportInternal(DuelSession session, Player player, Location destination) {
        session.setInternalTeleport(true);
        try {
            player.teleport(destination);
        } finally {
            session.setInternalTeleport(false);
        }
    }

    private void prepareForDuel(Player player, Kit kit) {
        kitManager.applyKit(player, kit);
        freeze(player);
    }

    public void freeze(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setVelocity(player.getVelocity().zero());
    }

    public void unfreeze(Player player, Kit kit) {
        player.setGameMode(kit.getGameMode());
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
    }

    private void runCountdown(DuelSession session) {
        int seconds = configManager.countdownSeconds();
        if (seconds <= 0) {
            Player pa = Bukkit.getPlayer(session.getPlayerA());
            Player pb = Bukkit.getPlayer(session.getPlayerB());
            if (pa != null && pb != null) {
                beginCombat(session, pa, pb);
            } else {
                endDuel(session.getId(), pa != null ? pa.getUniqueId() : session.getPlayerA(), true);
            }
            return;
        }

        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100));
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (!sessionsById.containsKey(session.getId()) || session.getPhase() != DuelSession.Phase.COUNTDOWN) {
                    session.cancelTasks();
                    return;
                }
                Player pa = Bukkit.getPlayer(session.getPlayerA());
                Player pb = Bukkit.getPlayer(session.getPlayerB());
                if (pa == null || pb == null) {
                    return;
                }
                if (remaining <= 0) {
                    beginCombat(session, pa, pb);
                    return;
                }
                var title = Texts.component(configManager.countdownTitle());
                var subtitle = Texts.component(configManager.countdownSubtitle()
                        .replace("%seconds%", String.valueOf(remaining)));
                Title shown = Title.title(title, subtitle, times);
                pa.showTitle(shown);
                pb.showTitle(shown);
                remaining--;
            }
        }, 0L, 20L);
        session.setCountdownTask(task);
    }

    private void beginCombat(DuelSession session, Player a, Player b) {
        if (session.getPhase() != DuelSession.Phase.COUNTDOWN) {
            return;
        }
        session.cancelCountdown();
        session.setPhase(DuelSession.Phase.ACTIVE);
        unfreeze(a, session.getKit());
        unfreeze(b, session.getKit());

        Title go = Title.title(
                Texts.component("&a&lFIGHT"),
                Texts.component("&7Good luck"),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(200)));
        a.showTitle(go);
        b.showTitle(go);

        if (configManager.maxDuelDurationSeconds() > 0) {
            long delayTicks = configManager.maxDuelDurationSeconds() * 20L;
            BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                DuelSession current = sessionsById.get(session.getId());
                if (current != null && current.getPhase() == DuelSession.Phase.ACTIVE) {
                    endDuel(session.getId(), session.decideWinnerByDamage(), true);
                }
            }, delayTicks);
            session.setTimeoutTask(timeout);
        }
    }

    /** Called by the combat listener when a player deals damage during an active duel. */
    public void recordDamage(String sessionId, UUID dealer, double amount) {
        DuelSession session = sessionsById.get(sessionId);
        if (session != null) {
            session.addDamage(dealer, amount);
        }
    }

    public void forfeit(UUID uuid) {
        DuelSession session = getSession(uuid);
        if (session == null) {
            return;
        }
        UUID opponent = session.getOpponent(uuid);
        Player quitter = Bukkit.getPlayer(uuid);
        if (quitter != null) {
            quitter.sendMessage(configManager.message("duel-forfeit"));
        }
        if (opponent != null) {
            Player op = Bukkit.getPlayer(opponent);
            if (op != null) {
                String name = quitter != null ? quitter.getName() : Bukkit.getOfflinePlayer(uuid).getName();
                op.sendMessage(configManager.message("forfeit-quit", "%player%", name != null ? name : "opponent"));
            }
            endDuel(session.getId(), opponent, true);
        } else {
            endDuel(session.getId(), null, false);
        }
    }

    /** Ends a duel with the given winner (loser is the other participant), restoring both players. */
    public void endDuel(String sessionId, UUID winnerUuid) {
        endDuel(sessionId, winnerUuid, true);
    }

    public void endDuel(String sessionId, UUID winnerUuid, boolean recordStats) {
        DuelSession session = sessionsById.remove(sessionId);
        if (session == null) {
            return;
        }
        session.setPhase(DuelSession.Phase.ENDING);
        session.setWinner(winnerUuid);
        session.cancelTasks();

        UUID loserUuid = winnerUuid != null ? session.getOpponent(winnerUuid) : null;
        sessionIdByPlayer.remove(session.getPlayerA());
        sessionIdByPlayer.remove(session.getPlayerB());
        cancelReconnect(session.getPlayerA());
        cancelReconnect(session.getPlayerB());

        Player winner = winnerUuid != null ? Bukkit.getPlayer(winnerUuid) : null;
        Player loser = loserUuid != null ? Bukkit.getPlayer(loserUuid) : null;

        if (winnerUuid == null) {
            restoreParticipant(session.getPlayerA());
            restoreParticipant(session.getPlayerB());
            Player a = Bukkit.getPlayer(session.getPlayerA());
            Player b = Bukkit.getPlayer(session.getPlayerB());
            if (a != null) a.sendMessage(configManager.message("duel-ended-draw"));
            if (b != null) b.sendMessage(configManager.message("duel-ended-draw"));
        } else {
            if (winner != null) {
                restoreParticipant(winner);
                winner.sendMessage(configManager.message("duel-ended-win",
                        "%opponent%", loser != null ? loser.getName() : nameOf(loserUuid),
                        "%kit%", session.getKit().getDisplayName()));
            }
            if (loser != null) {
                restoreParticipant(loser);
                loser.sendMessage(configManager.message("duel-ended-loss",
                        "%opponent%", winner != null ? winner.getName() : nameOf(winnerUuid),
                        "%kit%", session.getKit().getDisplayName()));
            }
            if (recordStats && loserUuid != null) {
                statsManager.recordResult(winnerUuid, loserUuid, session.getKit().getId(), session.getCombatDurationMillis());
            }
        }

        spectatorManager.stopAllSpectatorsOf(session.getId());
        arenaManager.resetAndRelease(session.getArena());
    }

    private void restoreParticipant(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            restoreParticipant(player);
        }
    }

    private void restoreParticipant(Player player) {
        player.closeInventory();
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setVelocity(player.getVelocity().zero());
        if (!playerStateManager.restore(player)) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    private static String nameOf(UUID uuid) {
        if (uuid == null) {
            return "opponent";
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : "opponent";
    }

    /** Handles a player disconnecting mid-duel: waits for a grace period, then counts as a forfeit. */
    public void handleDisconnect(UUID uuid) {
        DuelSession session = getSession(uuid);
        if (session == null) {
            return;
        }
        session.markDisconnected(uuid);

        if (!configManager.forfeitOnQuit()) {
            if (session.bothDisconnected()) {
                endDuel(session.getId(), null, false);
            }
            return;
        }

        int grace = configManager.forfeitGraceSeconds();
        UUID opponent = session.getOpponent(uuid);
        Player op = opponent != null ? Bukkit.getPlayer(opponent) : null;
        String disconnectedName = nameOf(uuid);

        if (grace <= 0 || session.getPhase() == DuelSession.Phase.COUNTDOWN) {
            if (op != null) {
                op.sendMessage(configManager.message("forfeit-quit", "%player%", disconnectedName));
            }
            if (opponent != null) {
                endDuel(session.getId(), opponent, true);
            } else {
                endDuel(session.getId(), null, false);
            }
            return;
        }

        if (op != null) {
            op.sendMessage(configManager.message("reconnect-wait",
                    "%player%", disconnectedName,
                    "%seconds%", String.valueOf(grace)));
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            reconnectTimers.remove(uuid);
            DuelSession current = getSession(uuid);
            if (current != null && current.isDisconnected(uuid)) {
                Player stillOnline = opponent != null ? Bukkit.getPlayer(opponent) : null;
                if (stillOnline != null) {
                    stillOnline.sendMessage(configManager.message("forfeit-quit", "%player%", disconnectedName));
                }
                if (opponent != null) {
                    endDuel(current.getId(), opponent, true);
                } else {
                    endDuel(current.getId(), null, false);
                }
            }
        }, grace * 20L);
        reconnectTimers.put(uuid, task);
        session.setReconnectTask(task);
    }

    public void handleReconnect(Player player) {
        UUID uuid = player.getUniqueId();
        cancelReconnect(uuid);

        DuelSession session = getSession(uuid);
        if (session != null) {
            session.markReconnected(uuid);
            Location spawn = session.getPlayerA().equals(uuid)
                    ? session.getArena().spawnOrOrigin(session.getArena().getSpawnA())
                    : session.getArena().spawnOrOrigin(session.getArena().getSpawnB());
            teleportInternal(session, player, spawn);
            if (session.getPhase() == DuelSession.Phase.COUNTDOWN) {
                freeze(player);
            } else if (session.getPhase() == DuelSession.Phase.ACTIVE) {
                unfreeze(player, session.getKit());
            }
            UUID opponent = session.getOpponent(uuid);
            Player op = opponent != null ? Bukkit.getPlayer(opponent) : null;
            if (op != null) {
                op.sendMessage(configManager.message("reconnect-resume", "%player%", player.getName()));
            }
            player.sendMessage(configManager.message("reconnect-resume", "%player%", player.getName()));
            return;
        }

        if (playerStateManager.hasSavedState(uuid)) {
            playerStateManager.restore(player);
        }
        if (spectatorManager.isSpectating(uuid)) {
            spectatorManager.stopSpectating(player);
        }
    }

    private void cancelReconnect(UUID uuid) {
        BukkitTask task = reconnectTimers.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void endAllDuelsForShutdown() {
        for (String id : Map.copyOf(sessionsById).keySet()) {
            endDuel(id, null, false);
        }
    }

}
