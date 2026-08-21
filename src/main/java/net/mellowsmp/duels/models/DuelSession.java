package net.mellowsmp.duels.models;

import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents one active (or recently finished) duel: the two participants,
 * the arena and kit in use, and the current phase of the match.
 */
public class DuelSession {

    public enum Phase {
        COUNTDOWN,
        ACTIVE,
        ENDING
    }

    private final String id;
    private final UUID playerA;
    private final UUID playerB;
    private final Arena arena;
    private final Kit kit;
    private final long startedAtMillis;

    private Phase phase = Phase.COUNTDOWN;
    private UUID winner;
    private long combatStartedAtMillis;
    private double damageDealtByA;
    private double damageDealtByB;

    private BukkitTask countdownTask;
    private BukkitTask timeoutTask;
    private BukkitTask reconnectTask;

    private final Set<UUID> disconnected = ConcurrentHashMap.newKeySet();
    private volatile boolean internalTeleport;

    public DuelSession(UUID playerA, UUID playerB, Arena arena, Kit kit) {
        this.id = UUID.randomUUID().toString();
        this.playerA = playerA;
        this.playerB = playerB;
        this.arena = arena;
        this.kit = kit;
        this.startedAtMillis = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public UUID getPlayerA() {
        return playerA;
    }

    public UUID getPlayerB() {
        return playerB;
    }

    public boolean hasPlayer(UUID uuid) {
        return playerA.equals(uuid) || playerB.equals(uuid);
    }

    public UUID getOpponent(UUID uuid) {
        if (playerA.equals(uuid)) return playerB;
        if (playerB.equals(uuid)) return playerA;
        return null;
    }

    public Arena getArena() {
        return arena;
    }

    public Kit getKit() {
        return kit;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
        if (phase == Phase.ACTIVE) {
            this.combatStartedAtMillis = System.currentTimeMillis();
        }
    }

    public UUID getWinner() {
        return winner;
    }

    public void setWinner(UUID winner) {
        this.winner = winner;
    }

    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    public long getCombatDurationMillis() {
        if (combatStartedAtMillis == 0) return 0;
        return System.currentTimeMillis() - combatStartedAtMillis;
    }

    public void addDamage(UUID dealer, double amount) {
        if (playerA.equals(dealer)) {
            damageDealtByA += amount;
        } else if (playerB.equals(dealer)) {
            damageDealtByB += amount;
        }
    }

    /** Used for the max-duration timeout rule: whoever dealt more damage wins. */
    public UUID decideWinnerByDamage() {
        return damageDealtByA >= damageDealtByB ? playerA : playerB;
    }

    public BukkitTask getCountdownTask() {
        return countdownTask;
    }

    public void setCountdownTask(BukkitTask countdownTask) {
        this.countdownTask = countdownTask;
    }

    public void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    public void setTimeoutTask(BukkitTask timeoutTask) {
        this.timeoutTask = timeoutTask;
    }

    public void setReconnectTask(BukkitTask reconnectTask) {
        cancelReconnectTask();
        this.reconnectTask = reconnectTask;
    }

    public void cancelReconnectTask() {
        if (reconnectTask != null) {
            reconnectTask.cancel();
            reconnectTask = null;
        }
    }

    public void cancelTasks() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
        cancelReconnectTask();
    }

    public void markDisconnected(UUID uuid) {
        disconnected.add(uuid);
    }

    public void markReconnected(UUID uuid) {
        disconnected.remove(uuid);
    }

    public boolean isDisconnected(UUID uuid) {
        return disconnected.contains(uuid);
    }

    public boolean bothDisconnected() {
        return disconnected.contains(playerA) && disconnected.contains(playerB);
    }

    public boolean isInternalTeleport() {
        return internalTeleport;
    }

    public void setInternalTeleport(boolean internalTeleport) {
        this.internalTeleport = internalTeleport;
    }
}
