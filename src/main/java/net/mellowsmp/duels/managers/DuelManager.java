package net.mellowsmp.duels.managers;

import net.kyori.adventure.title.Title;
import net.mellowsmp.duels.MellowDuels;
import net.mellowsmp.duels.models.Arena;
import net.mellowsmp.duels.models.DuelSession;
import net.mellowsmp.duels.models.Kit;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
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
    private final Map<String, BukkitTask> countdownTasks = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> durationTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> disconnectGraceTasks = new ConcurrentHashMap<>();

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
        if (a == null || b == null || kit == null) {
            return false;
        }
        if (a.getUniqueId().equals(b.getUniqueId())) {
            return false;
        }
        if (isInDuel(a.getUniqueId()) || isInDuel(b.getUniqueId())) {
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
            arenaManager.resetAndRelease(arena);
            a.sendMessage(configManager.message("no-arenas-available"));
            b.sendMessage(configManager.message("no-arenas-available"));
            plugin.getLogger().warning("Arena " + arena.getId() + " is missing spawn points.");
            return false;
        }

        // Leave any queues before locking into a duel
        plugin.getQueueManager().leaveQuiet(a.getUniqueId());
        plugin.getQueueManager().leaveQuiet(b.getUniqueId());

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

        runCountdown(session);
        return true;
    }

    private void prepareForDuel(Player player, org.bukkit.Location spawn, Kit kit) {
        player.closeInventory();
        player.teleport(spawn);
        kitManager.applyKit(player, kit);
        // Frozen during countdown; switched to kit mode when combat begins
        player.setGameMode(GameMode.ADVENTURE);
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    private void runCountdown(DuelSession session) {
        int seconds = configManager.countdownSeconds();
        String title = configManager.raw().getString("countdown.title", "&e&lDUEL STARTING");
        String subtitleTemplate = configManager.raw().getString("countdown.subtitle", "&f%seconds%");

        BukkitTask task = new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (!sessionsById.containsKey(session.getId())) {
                    cancel();
                    countdownTasks.remove(session.getId());
                    return;
                }

                Player pa = Bukkit.getPlayer(session.getPlayerA());
                Player pb = Bukkit.getPlayer(session.getPlayerB());
                if (pa == null || pb == null) {
                    cancel();
                    countdownTasks.remove(session.getId());
                    handleMissingPlayerDuringCountdown(session, pa, pb);
                    return;
                }
                if (remaining <= 0) {
                    beginCombat(session, pa, pb);
                    cancel();
                    countdownTasks.remove(session.getId());
                    return;
                }
                String subtitle = subtitleTemplate.replace("%seconds%", String.valueOf(remaining));
                Title adventureTitle = Title.title(
                        configManager.component(title),
                        configManager.component(subtitle),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(200)));
                pa.showTitle(adventureTitle);
                pb.showTitle(adventureTitle);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        countdownTasks.put(session.getId(), task);
    }

    private void handleMissingPlayerDuringCountdown(DuelSession session, Player pa, Player pb) {
        if (!sessionsById.containsKey(session.getId())) {
            return;
        }
        if (pa != null) {
            endDuel(session.getId(), pa.getUniqueId(), true);
        } else if (pb != null) {
            endDuel(session.getId(), pb.getUniqueId(), true);
        } else {
            endDuel(session.getId(), null, false);
        }
    }

    private void beginCombat(DuelSession session, Player a, Player b) {
        session.setPhase(DuelSession.Phase.ACTIVE);
        a.setGameMode(session.getKit().getGameMode());
        b.setGameMode(session.getKit().getGameMode());
        a.setWalkSpeed(0.2f);
        b.setWalkSpeed(0.2f);

        if (configManager.maxDuelDurationSeconds() > 0) {
            long delayTicks = configManager.maxDuelDurationSeconds() * 20L;
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    durationTasks.remove(session.getId());
                    DuelSession current = sessionsById.get(session.getId());
                    if (current != null && current.getPhase() == DuelSession.Phase.ACTIVE) {
                        endDuel(session.getId(), session.decideWinnerByDamage(), true);
                    }
                }
            }.runTaskLater(plugin, delayTicks);
            durationTasks.put(session.getId(), task);
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
        endDuel(sessionId, winnerUuid, true);
    }

    public void endDuel(String sessionId, UUID winnerUuid, boolean recordStats) {
        cancelSessionTasks(sessionId);

        DuelSession session = sessionsById.remove(sessionId);
        if (session == null) return;
        if (session.getPhase() == DuelSession.Phase.ENDING) return;
        session.setPhase(DuelSession.Phase.ENDING);
        session.setWinner(winnerUuid);

        UUID loserUuid = winnerUuid != null ? session.getOpponent(winnerUuid) : null;
        sessionIdByPlayer.remove(session.getPlayerA());
        sessionIdByPlayer.remove(session.getPlayerB());

        cancelDisconnectGrace(session.getPlayerA());
        cancelDisconnectGrace(session.getPlayerB());

        restoreParticipant(session.getPlayerA(), winnerUuid, loserUuid, session);
        restoreParticipant(session.getPlayerB(), winnerUuid, loserUuid, session);

        if (recordStats && winnerUuid != null && loserUuid != null) {
            statsManager.recordResult(winnerUuid, loserUuid, session.getKit().getId(), session.getCombatDurationMillis());
        }

        spectatorManager.stopAllSpectatorsOf(session.getId());
        arenaManager.resetAndRelease(session.getArena());
    }

    private void restoreParticipant(UUID uuid, UUID winnerUuid, UUID loserUuid, DuelSession session) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            playerStateManager.restore(player);
            if (winnerUuid != null && uuid.equals(winnerUuid)) {
                player.sendMessage(configManager.message("duel-ended-win")
                        .replace("%opponent%", offlineName(loserUuid))
                        .replace("%kit%", session.getKit().getDisplayName()));
            } else if (loserUuid != null && uuid.equals(loserUuid)) {
                player.sendMessage(configManager.message("duel-ended-loss")
                        .replace("%opponent%", offlineName(winnerUuid))
                        .replace("%kit%", session.getKit().getDisplayName()));
            }
        } else {
            playerStateManager.discard(uuid);
        }
    }

    private String offlineName(UUID uuid) {
        if (uuid == null) return "opponent";
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : "opponent";
    }

    private void cancelSessionTasks(String sessionId) {
        BukkitTask countdown = countdownTasks.remove(sessionId);
        if (countdown != null) countdown.cancel();
        BukkitTask duration = durationTasks.remove(sessionId);
        if (duration != null) duration.cancel();
    }

    private void cancelDisconnectGrace(UUID uuid) {
        BukkitTask task = disconnectGraceTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    /** Handles a player disconnecting mid-duel: counts as a forfeit if configured. */
    public void handleDisconnect(UUID uuid) {
        DuelSession session = getSession(uuid);
        if (session == null) return;

        if (!configManager.forfeitOnQuit()) {
            // Keep the session; when they never return the opponent can still win via death/timeout.
            return;
        }

        int grace = configManager.forfeitGraceSeconds();
        UUID opponent = session.getOpponent(uuid);
        if (grace <= 0) {
            forfeitDisconnected(uuid, opponent);
            return;
        }

        cancelDisconnectGrace(uuid);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                disconnectGraceTasks.remove(uuid);
                if (!isInDuel(uuid)) return;
                // Still offline after grace → forfeit
                if (Bukkit.getPlayer(uuid) == null) {
                    forfeitDisconnected(uuid, opponent);
                }
            }
        }.runTaskLater(plugin, grace * 20L);
        disconnectGraceTasks.put(uuid, task);
    }

    /** Called when a player rejoins during the disconnect grace window. */
    public void handleReconnect(UUID uuid) {
        cancelDisconnectGrace(uuid);
    }

    private void forfeitDisconnected(UUID uuid, UUID opponent) {
        DuelSession session = getSession(uuid);
        if (session == null || opponent == null) return;
        Player op = Bukkit.getPlayer(opponent);
        if (op != null) {
            op.sendMessage(configManager.message("forfeit-quit").replace("%player%", offlineName(uuid)));
        }
        endDuel(session.getId(), opponent, true);
    }

    public void endAllDuelsForShutdown() {
        for (String id : Map.copyOf(sessionsById).keySet()) {
            // Do not record stats on an abrupt shutdown — just restore and release arenas
            endDuel(id, null, false);
        }
        for (BukkitTask task : Map.copyOf(disconnectGraceTasks).values()) {
            task.cancel();
        }
        disconnectGraceTasks.clear();
    }
}
