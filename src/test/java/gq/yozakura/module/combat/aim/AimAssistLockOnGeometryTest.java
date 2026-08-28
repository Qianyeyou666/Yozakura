package gq.yozakura.module.combat.aim;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AimAssistLockOnGeometryTest {
    @Test
    public void zeroMultipointUsesPlayerModelSegmentDimensions() {
        AimAssistLockOnGeometry.Frame frame = frame(0.0D, 0.0D, 0.0F);

        assertEquals(0.50D, frame.get(AimAssistLockOnGeometry.Zone.HEAD).getWidth(), 0.000001D);
        assertEquals(0.50D, frame.get(AimAssistLockOnGeometry.Zone.HEAD).getDepth(), 0.000001D);
        assertEquals(0.50D, frame.get(AimAssistLockOnGeometry.Zone.BODY).getWidth(), 0.000001D);
        assertEquals(0.25D, frame.get(AimAssistLockOnGeometry.Zone.BODY).getDepth(), 0.000001D);
        assertEquals(1.35D, frame.get(AimAssistLockOnGeometry.Zone.HEAD).getMinimumY(), 0.000001D);
        assertEquals(1.80D, frame.get(AimAssistLockOnGeometry.Zone.HEAD).getMaximumY(), 0.000001D);
        assertEquals(0.675D, frame.get(AimAssistLockOnGeometry.Zone.BODY).getMinimumY(), 0.000001D);
        assertEquals(1.35D, frame.get(AimAssistLockOnGeometry.Zone.BODY).getMaximumY(), 0.000001D);
    }

    @Test
    public void fullMultipointMakesEverySegmentEqualThePlayerCollisionBox() {
        AimAssistLockOnGeometry.Frame frame = frame(1.0D, 1.0D, 37.0F);

        for (AimAssistLockOnGeometry.Zone zone : AimAssistLockOnGeometry.Zone.values()) {
            AimAssistLockOnGeometry.Region region = frame.get(zone);
            assertTrue(region.contains(-0.299D, 0.001D, -0.299D));
            assertTrue(region.contains(0.299D, 1.799D, 0.299D));
            assertFalse(region.contains(0.301D, 0.9D, 0.0D));
        }
    }

    @Test
    public void horizontalAndVerticalMultipointExpandIndependently() {
        AimAssistLockOnGeometry.Frame horizontal = frame(1.0D, 0.0D, 0.0F);
        AimAssistLockOnGeometry.Frame vertical = frame(0.0D, 1.0D, 0.0F);

        assertTrue(horizontal.get(AimAssistLockOnGeometry.Zone.HEAD).contains(0.299D, 1.50D, 0.299D));
        assertFalse(horizontal.get(AimAssistLockOnGeometry.Zone.HEAD).contains(0.0D, 0.10D, 0.0D));
        assertTrue(vertical.get(AimAssistLockOnGeometry.Zone.HEAD).contains(0.0D, 0.10D, 0.0D));
        assertFalse(vertical.get(AimAssistLockOnGeometry.Zone.BODY).contains(0.0D, 1.0D, 0.20D));
    }

    @Test
    public void modelSegmentsAreAlwaysClippedToTheSuppliedCollisionBox() {
        AimAssistLockOnGeometry.Frame frame = AimAssistLockOnGeometry.create(
                -0.25D, 2.0D, -0.20D, 0.25D, 3.50D, 0.20D,
                25.0F, 0.0D, 0.0D);

        for (AimAssistLockOnGeometry.Zone zone : AimAssistLockOnGeometry.Zone.values()) {
            AimAssistLockOnGeometry.Region region = frame.get(zone);
            assertFalse(region.contains(0.0D, 1.999D, 0.0D));
            assertFalse(region.contains(0.251D, 2.8D, 0.0D));
        }
    }

    @Test
    public void bodyYawRotatesTheNarrowModelDepth() {
        AimAssistLockOnGeometry.Frame facingSouth = frame(0.0D, 0.0D, 0.0F);
        AimAssistLockOnGeometry.Frame facingWest = frame(0.0D, 0.0D, 90.0F);

        assertTrue(facingSouth.get(AimAssistLockOnGeometry.Zone.BODY).contains(0.20D, 1.0D, 0.0D));
        assertFalse(facingWest.get(AimAssistLockOnGeometry.Zone.BODY).contains(0.20D, 1.0D, 0.0D));
    }

    @Test
    public void firstHeadPointUsesEyeLevelAndNearestHorizontalSurface() {
        AimAssistLockOnGeometry.Frame frame = frame(0.0D, 0.0D, 0.0F);
        AimAssistLockOnGeometry.Point point = frame.nearestHeadPoint(2.0D, 1.62D, 0.0D);

        assertEquals(0.249D, point.getX(), 0.002D);
        assertEquals(1.62D, point.getY(), 0.000001D);
        assertEquals(0.0D, point.getZ(), 0.000001D);
        assertTrue(frame.get(AimAssistLockOnGeometry.Zone.HEAD).contains(
                point.getX(), point.getY(), point.getZ()));
    }

    @Test
    public void rayCanClassifyHeadAndWholeModelSeparately() {
        AimAssistLockOnGeometry.Frame frame = frame(0.0D, 0.0D, 0.0F);

        assertTrue(frame.rayHits(AimAssistLockOnGeometry.Zone.HEAD,
                0.0D, 1.62D, -3.0D, 0.0D, 0.0D, 1.0D, 4.0D));
        assertFalse(frame.rayHits(AimAssistLockOnGeometry.Zone.HEAD,
                0.0D, 0.30D, -3.0D, 0.0D, 0.0D, 1.0D, 4.0D));
        assertTrue(frame.rayHitsAny(
                0.0D, 0.30D, -3.0D, 0.0D, 0.0D, 1.0D, 4.0D));
    }

    @Test
    public void missedMovingHeadProjectsToTheNearestAngularBoundary() {
        AimAssistLockOnGeometry.Frame frame = frame(0.0D, 0.0D, 0.0F);
        double directionX = 0.12D;
        double directionZ = Math.sqrt(1.0D - directionX * directionX);

        AimAssistLockOnGeometry.Point point = frame.nearestHeadPointToRay(
                0.0D, 1.62D, -3.0D, directionX, 0.0D, directionZ);

        assertTrue(point.getX() > 0.20D);
        assertTrue(frame.get(AimAssistLockOnGeometry.Zone.HEAD).contains(
                point.getX(), point.getY(), point.getZ()));
    }

    @Test
    public void knockbackMissProjectsToTheNearestZoneBoundary() {
        AimAssistLockOnGeometry.Frame frame = frame(0.0D, 0.0D, 0.0F);
        double directionX = 0.12D;
        double directionZ = Math.sqrt(1.0D - directionX * directionX);

        AimAssistLockOnGeometry.Point point = frame.nearestPointToRay(
                AimAssistLockOnGeometry.Zone.BODY,
                0.0D, 1.0D, -3.0D, directionX, 0.0D, directionZ);

        assertTrue(point.getX() > 0.20D);
        assertTrue(frame.get(AimAssistLockOnGeometry.Zone.BODY).contains(
                point.getX(), point.getY(), point.getZ()));
    }

    private static AimAssistLockOnGeometry.Frame frame(double horizontal, double vertical, float yaw) {
        return AimAssistLockOnGeometry.create(
                -0.30D, 0.0D, -0.30D,
                0.30D, 1.80D, 0.30D,
                yaw, horizontal, vertical);
    }
}
