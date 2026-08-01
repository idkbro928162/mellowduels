package net.mellowsmp.duels.models;

import java.util.UUID;

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
}
