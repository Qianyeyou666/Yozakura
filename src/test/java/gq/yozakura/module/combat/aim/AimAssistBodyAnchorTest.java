package gq.yozakura.module.combat.aim;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AimAssistBodyAnchorTest {

    @Test
    public void verticalTargetTranslationCanBeSmoothedWithoutDelayingHorizontalTracking() {
        AimAssistBodyAnchor anchor = AimAssistBodyAnchor.capture(0.5D, 1.0D, 0.5D);

        anchor.followTargetTranslation(
                0.0D, 0.0D, 0.0D, 1.0D, 2.0D, 1.0D,
                1.0D, 1.0D, -1.0D, 2.0D, 3.0D, 0.0D, 0.5D);

        assertArrayEquals(new double[]{1.5D, 1.5D, -0.5D}, anchor.point(), 0.000001D);
    }
    @Test
    public void initialBlatantAnchorPrioritizesThePlayersLevelViewHeight() {
        AimAssistBodyAnchor anchor = AimAssistBodyAnchor.captureLevelInnerBox(
                2.0D, 4.0D, 6.0D,
                3.0D, 6.0D, 7.0D,
                5.60D);

        double[] point = anchor.point();
        assertEquals(2.5D, point[0], 0.0001D);
        assertEquals(5.60D, point[1], 0.0001D);
        assertEquals(6.5D, point[2], 0.0001D);
    }

    @Test
    public void levelViewHeightStaysInsideTheVerticalSafeRegion() {
        AimAssistBodyAnchor above = AimAssistBodyAnchor.captureLevelInnerBox(
                2.0D, 4.0D, 6.0D,
                3.0D, 6.0D, 7.0D,
                8.0D);
        AimAssistBodyAnchor below = AimAssistBodyAnchor.captureLevelInnerBox(
                2.0D, 4.0D, 6.0D,
                3.0D, 6.0D, 7.0D,
                2.0D);

        assertEquals(5.88D, above.point()[1], 0.0001D);
        assertEquals(4.12D, below.point()[1], 0.0001D);
    }

    @Test
    public void playerEyeMovementDoesNotMoveTheCapturedWorldPoint() {
        AimAssistBodyAnchor anchor = AimAssistBodyAnchor.capture(0.12D, 1.25D, 3.20D);

        double[] point = anchor.point();

        assertEquals(0.12D, point[0], 0.0001D);
        assertEquals(1.25D, point[1], 0.0001D);
        assertEquals(3.20D, point[2], 0.0001D);
    }

    @Test
    public void safeTrackingFollowsTargetTranslationWhilePreservingTheBodyOffset() {
        AimAssistBodyAnchor anchor = AimAssistBodyAnchor.capture(0.30D, 1.25D, 3.20D);

        assertTrue(anchor.followTargetTranslation(
                0.00D, 0.00D, 3.00D, 0.40D, 2.00D, 3.40D,
                0.40D, 0.00D, 3.20D, 0.80D, 2.00D, 3.60D));
        double[] point = anchor.point();

        assertEquals(0.70D, point[0], 0.0001D);
        assertEquals(1.25D, point[1], 0.0001D);
        assertEquals(3.40D, point[2], 0.0001D);
    }

    @Test
    public void renderPointInterpolatesWithoutMutatingTheTickAnchor() {
        AimAssistBodyAnchor anchor = AimAssistBodyAnchor.capture(0.70D, 1.25D, 3.40D);

        double[] quarter = anchor.translatedPoint(
                0.40D, 0.00D, 3.20D, 0.80D, 2.00D, 3.60D,
                0.10D, 0.00D, 3.05D, 0.50D, 2.00D, 3.45D);
        double[] threeQuarters = anchor.translatedPoint(
                0.40D, 0.00D, 3.20D, 0.80D, 2.00D, 3.60D,
                0.30D, 0.00D, 3.15D, 0.70D, 2.00D, 3.55D);
        double[] persistent = anchor.point();

        assertEquals(0.40D, quarter[0], 0.0001D);
        assertEquals(3.25D, quarter[2], 0.0001D);
        assertEquals(0.60D, threeQuarters[0], 0.0001D);
        assertEquals(3.35D, threeQuarters[2], 0.0001D);
        assertEquals(0.70D, persistent[0], 0.0001D);
        assertEquals(3.40D, persistent[2], 0.0001D);
    }

    @Test
    public void targetMovementDoesNotMoveAWorldPointStillInsideTheAttackBox() {
        AimAssistBodyAnchor anchor = AimAssistBodyAnchor.capture(0.12D, 1.25D, 3.20D);

        assertFalse(anchor.clampToBox(
                -0.20D, 0.80D, 3.10D,
                0.40D, 2.80D, 4.10D));
        double[] point = anchor.point();

        assertEquals(0.12D, point[0], 0.0001D);
        assertEquals(1.25D, point[1], 0.0001D);
        assertEquals(3.20D, point[2], 0.0001D);
    }

    @Test
    public void pointMovesOnlyOntoSafeInnerEdgesAfterTheTargetLeavesIt() {
        AimAssistBodyAnchor anchor = AimAssistBodyAnchor.capture(0.12D, 1.25D, 3.20D);

        assertTrue(anchor.clampToBox(
                0.70D, 1.80D, 2.25D,
                1.30D, 3.80D, 3.05D));
        double[] point = anchor.point();

        assertEquals(0.74D, point[0], 0.0001D);
        assertEquals(1.86D, point[1], 0.0001D);
        assertEquals(3.01D, point[2], 0.0001D);
    }

    @Test
    public void safeInsetAbsorbsSmallFollowUpTargetMovement() {
        AimAssistBodyAnchor anchor = AimAssistBodyAnchor.capture(0.12D, 1.25D, 3.20D);

        assertTrue(anchor.clampToBox(
                0.70D, 0.80D, 3.10D,
                1.30D, 2.80D, 4.10D));
        assertFalse(anchor.clampToBox(
                0.72D, 0.80D, 3.10D,
                1.32D, 2.80D, 4.10D));

        double[] point = anchor.point();
        assertEquals(0.74D, point[0], 0.0001D);
        assertEquals(1.25D, point[1], 0.0001D);
        assertEquals(3.20D, point[2], 0.0001D);
    }

    @Test
    public void axesStillInsideTheAttackBoxDoNotDriftTowardItsCenter() {
        AimAssistBodyAnchor anchor = AimAssistBodyAnchor.capture(0.12D, 1.25D, 3.20D);

        assertTrue(anchor.clampToBox(
                -0.20D, 1.80D, 3.10D,
                0.40D, 3.80D, 4.10D));
        double[] point = anchor.point();

        assertEquals(0.12D, point[0], 0.0001D);
        assertEquals(1.86D, point[1], 0.0001D);
        assertEquals(3.20D, point[2], 0.0001D);
    }
}
