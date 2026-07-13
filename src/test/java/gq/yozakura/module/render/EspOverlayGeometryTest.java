package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class EspOverlayGeometryTest {
    @Test
    public void createsScreenBoundsOnlyForVisibleProjectionPoints() {
        EspOverlayGeometry.Bounds bounds = EspOverlayGeometry.bounds(new float[][]{
                {42.0F, 88.0F, 0.25F}, {16.0F, 41.0F, 0.55F}, {73.0F, 62.0F, 0.82F}
        });

        assertEquals(16.0F, bounds.minX, 0.0F);
        assertEquals(41.0F, bounds.minY, 0.0F);
        assertEquals(73.0F, bounds.maxX, 0.0F);
        assertEquals(88.0F, bounds.maxY, 0.0F);
        assertNull(EspOverlayGeometry.bounds(new float[][]{{16.0F, 41.0F, 1.01F}}));
    }

    @Test
    public void preservesRgbWhileScalingTheExistingAlpha() {
        assertEquals(0x407FA0C0, EspOverlayGeometry.applyOpacity(0x807FA0C0, 0.5F));
    }
}
