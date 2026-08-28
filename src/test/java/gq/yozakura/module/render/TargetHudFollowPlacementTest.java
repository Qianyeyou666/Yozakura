package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TargetHudFollowPlacementTest {
    @Test
    public void placesCardBesideProjectedHeadAndKeepsItInsideScreen() {
        TargetHudFollowProjection.Position beside = TargetHudFollowProjection.placeBeside(
                160.0F, 100.0F, 120.0F, 40.0F, 320.0F, 180.0F);
        assertEquals(172.0F, beside.getX(), 0.001F);
        assertEquals(80.0F, beside.getY(), 0.001F);

        TargetHudFollowProjection.Position clamped = TargetHudFollowProjection.place(
                -20.0F, 20.0F, 120.0F, 40.0F, 320.0F, 180.0F);
        assertEquals(2.0F, clamped.getX(), 0.001F);
        assertEquals(2.0F, clamped.getY(), 0.001F);
    }
}
