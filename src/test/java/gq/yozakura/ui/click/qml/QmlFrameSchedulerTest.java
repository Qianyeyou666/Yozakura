package gq.yozakura.ui.click.qml;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QmlFrameSchedulerTest {
    @Test
    public void initialAnimationRendersAtMostOncePerFrameThenBecomesIdle() {
        QmlFrameScheduler scheduler = new QmlFrameScheduler(0L);

        assertTrue(scheduler.shouldRender(0L));
        scheduler.didRender(0L);
        assertFalse(scheduler.shouldRender(5_000_000L));
        assertTrue(scheduler.shouldRender(17_000_000L));
        scheduler.didRender(17_000_000L);
        assertFalse(scheduler.shouldRender(400_000_000L));
    }

    @Test
    public void interactionWakesAnIdleSceneForAnimations() {
        QmlFrameScheduler scheduler = new QmlFrameScheduler(0L);
        scheduler.didRender(0L);

        scheduler.invalidateForAnimation(500_000_000L);

        assertTrue(scheduler.shouldRender(500_000_000L));
        scheduler.didRender(500_000_000L);
        assertTrue(scheduler.shouldRender(517_000_000L));
        scheduler.didRender(517_000_000L);
        assertFalse(scheduler.shouldRender(850_000_000L));
    }
}
