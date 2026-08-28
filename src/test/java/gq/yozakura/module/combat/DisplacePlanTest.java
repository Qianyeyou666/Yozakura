package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DisplacePlanTest {
    @Test
    public void leftAndRightAnglesAreRelativeToCurrentServerYaw() {
        assertEquals(-60.0F, DisplacePlan.displacedYaw(30.0F, 90.0F, false), 0.001F);
        assertEquals(120.0F, DisplacePlan.displacedYaw(30.0F, 90.0F, true), 0.001F);
    }

    @Test
    public void displacedYawWrapsAcrossOneHundredEightyDegrees() {
        assertEquals(-110.0F, DisplacePlan.displacedYaw(160.0F, 90.0F, true), 0.001F);
        assertEquals(110.0F, DisplacePlan.displacedYaw(-160.0F, 90.0F, false), 0.001F);
    }

    @Test
    public void zeroAndFullTurnRemainNormalized() {
        assertEquals(45.0F, DisplacePlan.displacedYaw(45.0F, 0.0F, true), 0.001F);
        assertEquals(-135.0F, DisplacePlan.displacedYaw(45.0F, 180.0F, true), 0.001F);
    }
}
