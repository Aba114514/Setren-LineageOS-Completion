package com.setren.lineageenhance;

import org.junit.Test;
import static org.junit.Assert.*;

public class AodSessionTest {
    @Test public void reentryAndNotificationsDoNotRestartDeadline() {
        AodSession session = new AodSession();
        assertEquals(16000L, session.enterAod(1000L, 15L));
        assertEquals(16000L, session.enterAod(10000L, 15L));
        assertFalse(session.expire(15999L));
        assertTrue(session.expire(16000L));
        assertTrue(session.blocks("DOZE_AOD"));
        assertTrue(session.blocks("DOZE_AOD_PAUSING"));
        assertFalse(session.blocks("DOZE_PULSING_AUTH_UI"));
        assertFalse(session.blocks("FINISH"));
    }

    @Test public void wakingResetsExpiredSessionAndCancelsOldDeadline() {
        AodSession session = new AodSession();
        session.enterAod(0L, 15L);
        session.reset(); // Wake at 10s, then turn off again at 12s.
        assertEquals(27000L, session.enterAod(12000L, 15L));
        assertFalse(session.expire(15000L));
        assertTrue(session.expire(27000L));
        session.reset();
        assertFalse(session.blocks("DOZE_AOD"));
        assertEquals(45000L, session.enterAod(30000L, 15L));
    }

    @Test public void zeroIsUnlimitedAndLongDurationsDoNotOverflow() {
        AodSession session = new AodSession();
        assertEquals(-1L, session.enterAod(500L, 0L));
        assertFalse(session.expire(Long.MAX_VALUE));
        assertEquals(2147483647500L, session.enterAod(500L, Integer.MAX_VALUE));
        assertEquals(0L, AodSession.parseSeconds("0"));
        assertEquals(90L, AodSession.parseSeconds(" 90 "));
    }

    @Test public void invalidInputIsRejected() {
        for (String value : new String[]{"", "-1", "1.5", "2147483648", "999999999999999999999999"}) {
            assertThrows(IllegalArgumentException.class, () -> AodSession.parseSeconds(value));
        }
    }
}
