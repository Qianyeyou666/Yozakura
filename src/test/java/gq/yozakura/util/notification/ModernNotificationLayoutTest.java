package gq.yozakura.util.notification;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ModernNotificationLayoutTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void compactDimensionsAdaptToContentWithoutReturningToLegacyBulk() {
        assertEquals(34.0F, ModernNotificationLayout.panelHeight(false), EPSILON);
        assertEquals(42.0F, ModernNotificationLayout.panelHeight(true), EPSILON);
        assertEquals(188.0F, ModernNotificationLayout.panelWidth(32.0F, 70.0F), EPSILON);
        assertEquals(264.0F, ModernNotificationLayout.panelWidth(280.0F, 300.0F), EPSILON);
    }

    @Test
    public void contentHierarchyStaysInsideTheCardAndClearsTheAccentRail() {
        ModernNotificationLayout.Layout layout =
                ModernNotificationLayout.create(100.0F, 200.0F, 300.0F, 242.0F);

        assertEquals(108.0F, layout.getAccentLeft(), EPSILON);
        assertEquals(2.0F, layout.getAccentWidth(), EPSILON);
        assertEquals(115.0F, layout.getIconLeft(), EPSILON);
        assertEquals(22.0F, layout.getIconSize(), EPSILON);
        assertEquals(145.0F, layout.getTextX(), EPSILON);
        assertEquals(207.0F, layout.getTitleY(), EPSILON);
        assertEquals(221.0F, layout.getMessageY(), EPSILON);
        assertEquals(145.0F, layout.getProgressLeft(), EPSILON);
        assertEquals(292.0F, layout.getProgressRight(), EPSILON);
        assertTrue(layout.getProgressBottom() <= 242.0F);
        assertTrue(layout.getTextX() > layout.getIconLeft() + layout.getIconSize());
    }

    @Test
    public void lifetimeProgressUsesClampedElapsedTime() {
        assertEquals(1.0F, ModernNotificationLayout.progressForLifetime(0L, 2500L), EPSILON);
        assertEquals(0.5F, ModernNotificationLayout.progressForLifetime(1250L, 2500L), EPSILON);
        assertEquals(0.0F, ModernNotificationLayout.progressForLifetime(4000L, 2500L), EPSILON);
        assertEquals(0.0F, ModernNotificationLayout.progressForLifetime(1L, 0L), EPSILON);
    }
}
