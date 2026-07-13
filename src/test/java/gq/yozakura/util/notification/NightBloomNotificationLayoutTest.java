package gq.yozakura.util.notification;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NightBloomNotificationLayoutTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void transitionAlphaTracksTheExistingSlideContinuously() {
        assertEquals(0.0F, NightBloomNotificationLayout.alphaForSlide(1.0F), EPSILON);
        assertEquals(0.5F, NightBloomNotificationLayout.alphaForSlide(0.5F), EPSILON);
        assertEquals(1.0F, NightBloomNotificationLayout.alphaForSlide(0.0F), EPSILON);
        assertTrue(NightBloomNotificationLayout.alphaForSlide(0.49F)
                > NightBloomNotificationLayout.alphaForSlide(0.50F));
    }

    @Test
    public void lifetimeProgressIsClampedAtBothEnds() {
        assertEquals(1.0F, NightBloomNotificationLayout.progressForLifetime(0L, 2500L), EPSILON);
        assertEquals(0.5F, NightBloomNotificationLayout.progressForLifetime(1250L, 2500L), EPSILON);
        assertEquals(0.0F, NightBloomNotificationLayout.progressForLifetime(4000L, 2500L), EPSILON);
        assertEquals(0.0F, NightBloomNotificationLayout.progressForLifetime(1L, 0L), EPSILON);
    }

    @Test
    public void layoutKeepsIconTextAndProgressInsideThePanelInsets() {
        NightBloomNotificationLayout.Layout layout = NightBloomNotificationLayout.create(100.0F, 200.0F, 300.0F, 246.0F);

        assertEquals(109.0F, layout.getIconLeft(), EPSILON);
        assertEquals(209.0F, layout.getIconTop(), EPSILON);
        assertEquals(22.0F, layout.getIconSize(), EPSILON);
        assertEquals(139.0F, layout.getTitleX(), EPSILON);
        assertEquals(207.0F, layout.getTitleY(), EPSILON);
        assertEquals(222.0F, layout.getMessageY(), EPSILON);
        assertEquals(109.0F, layout.getProgressLeft(), EPSILON);
        assertEquals(291.0F, layout.getProgressRight(), EPSILON);
        assertTrue(layout.getProgressTop() < layout.getProgressBottom());
        assertTrue(layout.getProgressBottom() <= 246.0F);
    }

    @Test
    public void compactPanelDimensionsStayBelowTheLegacyNotificationSize() {
        assertEquals(38.0F, NightBloomNotificationLayout.panelHeight(false), EPSILON);
        assertEquals(46.0F, NightBloomNotificationLayout.panelHeight(true), EPSILON);
        assertEquals(190.0F, NightBloomNotificationLayout.panelWidth(40.0F, 80.0F), EPSILON);
        assertEquals(248.0F, NightBloomNotificationLayout.panelWidth(260.0F, 280.0F), EPSILON);
    }
}
