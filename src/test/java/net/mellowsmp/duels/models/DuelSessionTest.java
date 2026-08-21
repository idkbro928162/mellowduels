package net.mellowsmp.duels.models;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelSessionTest {

    private final UUID playerA = UUID.randomUUID();
    private final UUID playerB = UUID.randomUUID();

    @Test
    void resolvesParticipantsAndOpponent() {
        DuelSession session = session();

        assertTrue(session.hasPlayer(playerA));
        assertTrue(session.hasPlayer(playerB));
        assertEquals(playerB, session.getOpponent(playerA));
        assertEquals(playerA, session.getOpponent(playerB));
        assertNull(session.getOpponent(UUID.randomUUID()));
    }

    @Test
    void tracksOnlyFinitePositiveParticipantDamage() {
        DuelSession session = session();

        session.addDamage(playerA, 4.5);
        session.addDamage(playerB, 2.0);
        session.addDamage(playerA, -5.0);
        session.addDamage(playerA, Double.NaN);
        session.addDamage(UUID.randomUUID(), 100.0);

        assertEquals(4.5, session.getDamageDealt(playerA));
        assertEquals(2.0, session.getDamageDealt(playerB));
        assertEquals(playerA, session.decideWinnerByDamage());
    }

    @Test
    void usesStableUniqueSessionIds() {
        DuelSession first = session();
        DuelSession second = session();

        assertNotEquals(first.getId(), second.getId());
        assertEquals(DuelSession.Phase.COUNTDOWN, first.getPhase());
        assertEquals(0, first.getCombatDurationMillis());
    }

    @Test
    void timeoutDamageTieHasNoWinner() {
        DuelSession session = session();
        session.addDamage(playerA, 3);
        session.addDamage(playerB, 3);

        assertNull(session.decideWinnerByDamage());
    }

    private DuelSession session() {
        return new DuelSession(playerA, playerB, null, null);
    }
}
