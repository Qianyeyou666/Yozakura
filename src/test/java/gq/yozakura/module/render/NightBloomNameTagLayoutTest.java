package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NightBloomNameTagLayoutTest {
    @Test
    public void panelGrowsWithTheNameButStopsAtTheNightBloomMaximum() {
        NightBloomNameTagLayout.Layout shortName = NightBloomNameTagLayout.measure(
                38.0F, 34.0F, 20.0F, true, true);
        NightBloomNameTagLayout.Layout longName = NightBloomNameTagLayout.measure(
                128.0F, 34.0F, 20.0F, true, true);
        NightBloomNameTagLayout.Layout excessiveName = NightBloomNameTagLayout.measure(
                600.0F, 34.0F, 20.0F, true, true);

        assertTrue(longName.getWidth() > shortName.getWidth());
        assertEquals(NightBloomNameTagLayout.MAX_WIDTH, excessiveName.getWidth(), 0.0F);
        assertTrue(excessiveName.getNameMaxWidth() < 600.0F);
    }

    @Test
    public void optionalMetadataKeepsCompactConsistentSpacing() {
        NightBloomNameTagLayout.Layout nameOnly = NightBloomNameTagLayout.measure(
                64.0F, 34.0F, 20.0F, false, false);
        NightBloomNameTagLayout.Layout full = NightBloomNameTagLayout.measure(
                64.0F, 34.0F, 20.0F, true, true);

        assertTrue(full.getWidth() > nameOnly.getWidth());
        assertEquals(NightBloomNameTagLayout.HEIGHT, full.getHeight(), 0.0F);
        assertEquals(NightBloomNameTagLayout.CONTENT_GAP,
                full.getDistanceX() - full.getHealthRight(), 0.0F);
    }

    @Test
    public void healthAndDistanceScalingAreClamped() {
        assertEquals(0.0F, NightBloomNameTagLayout.healthFraction(-4.0F, 20.0F), 0.0F);
        assertEquals(0.5F, NightBloomNameTagLayout.healthFraction(10.0F, 20.0F), 0.0F);
        assertEquals(1.0F, NightBloomNameTagLayout.healthFraction(80.0F, 20.0F), 0.0F);

        float near = NightBloomNameTagLayout.worldScale(0.0F, 1.0F);
        float far = NightBloomNameTagLayout.worldScale(200.0F, 1.0F);
        assertEquals(NightBloomNameTagLayout.BASE_WORLD_SCALE, near, 0.000001F);
        assertEquals(NightBloomNameTagLayout.BASE_WORLD_SCALE
                * NightBloomNameTagLayout.MAX_DISTANCE_SCALE, far, 0.000001F);
    }
}
