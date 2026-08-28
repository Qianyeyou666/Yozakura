package gq.yozakura.module.world;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpeedMineDigSessionTest {
    @Test
    public void startArmsOneMatchingStopAndDuplicateFinishIsRejected() {
        SpeedMineDigSession session = new SpeedMineDigSession();

        session.start(10, 64, -3, 2);
        assertTrue(session.isActive());
        assertTrue(session.canFinish(10, 64, -3));

        SpeedMineDigSession.Target target = session.finish(10, 64, -3);
        assertEquals(10, target.x);
        assertEquals(64, target.y);
        assertEquals(-3, target.z);
        assertEquals(2, target.facingOrdinal);
        assertFalse(session.isActive());
        assertFalse(session.canFinish(10, 64, -3));
    }

    @Test
    public void targetChangeAndAbortDropThePreviousMiningSession() {
        SpeedMineDigSession session = new SpeedMineDigSession();
        session.start(1, 2, 3, 1);

        assertFalse(session.canFinish(2, 2, 3));
        assertFalse(session.isActive());

        session.start(4, 5, 6, 3);
        session.abort(4, 5, 6);
        assertFalse(session.isActive());
    }

    @Test
    public void unrelatedAbortDoesNotCancelTheActiveTarget() {
        SpeedMineDigSession session = new SpeedMineDigSession();
        session.start(1, 2, 3, 1);

        session.abort(9, 9, 9);

        assertTrue(session.canFinish(1, 2, 3));
    }
}
