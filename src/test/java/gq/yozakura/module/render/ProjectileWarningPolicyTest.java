package gq.yozakura.module.render;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProjectileWarningPolicyTest {
    private static final double EPSILON = 0.0001D;

    @Test
    public void anchorsBedWarningAboveTheVanillaPlayerStats() {
        assertEquals(176.0F, ProjectileWarningPolicy.bedWarningY(240), 0.0F);
        assertEquals(36.0F, ProjectileWarningPolicy.bedWarningY(100), 0.0F);
    }

    @Test
    public void trajectoryAppliesAccelerationDragAndGravityPerTick() {
        List<ProjectileWarningPolicy.Point> points = ProjectileWarningPolicy.trace(
                new ProjectileWarningPolicy.Point(0.0D, 10.0D, 0.0D),
                new ProjectileWarningPolicy.Point(1.0D, 1.0D, 0.0D),
                new ProjectileWarningPolicy.Point(0.1D, 0.0D, 0.0D),
                0.1D, 0.5D, 2);

        assertEquals(3, points.size());
        assertEquals(1.0D, points.get(1).getX(), EPSILON);
        assertEquals(11.0D, points.get(1).getY(), EPSILON);
        assertEquals(1.55D, points.get(2).getX(), EPSILON);
        assertEquals(11.45D, points.get(2).getY(), EPSILON);
    }

    @Test
    public void referenceRayUsesNormalizedMotionForFiveHundredBlocks() {
        ProjectileWarningPolicy.Point end = ProjectileWarningPolicy.referenceRayEnd(
                new ProjectileWarningPolicy.Point(10.0D, 20.0D, 30.0D),
                new ProjectileWarningPolicy.Point(3.0D, 4.0D, 0.0D),
                500.0D);

        assertEquals(310.0D, end.getX(), EPSILON);
        assertEquals(420.0D, end.getY(), EPSILON);
        assertEquals(30.0D, end.getZ(), EPSILON);
        assertTrue(ProjectileWarningPolicy.hasReferenceFireballMotion(
                new ProjectileWarningPolicy.Point(0.01D, 0.0D, 0.0D)));
        assertFalse(ProjectileWarningPolicy.hasReferenceFireballMotion(
                new ProjectileWarningPolicy.Point(0.009D, 0.0D, 0.0D)));
    }

    @Test
    public void referenceDangerUsesImpactCenteredFiveByFiveByFiveBox() {
        ProjectileWarningPolicy.Point impact = new ProjectileWarningPolicy.Point(10.5D, 20.5D, 30.5D);

        assertTrue(ProjectileWarningPolicy.isInsideReferenceWarningBox(
                new ProjectileWarningPolicy.Point(13.0D, 18.0D, 33.0D), impact, 2.5D));
        assertFalse(ProjectileWarningPolicy.isInsideReferenceWarningBox(
                new ProjectileWarningPolicy.Point(13.01D, 20.5D, 30.5D), impact, 2.5D));
    }

    @Test
    public void referenceEtaAndColorFollowRemainingImpactDistance() {
        assertEquals(2.0D, ProjectileWarningPolicy.referenceEtaSeconds(40.0D, 1.0D), EPSILON);
        assertEquals(-1.0D, ProjectileWarningPolicy.referenceEtaSeconds(40.0D, 0.0D), EPSILON);
        assertEquals(0xFFFF0000, ProjectileWarningPolicy.referenceDistanceColor(8.0D));
        assertEquals(0xFFFFFF00, ProjectileWarningPolicy.referenceDistanceColor(24.0D));
        assertEquals(0xFF00FF00, ProjectileWarningPolicy.referenceDistanceColor(48.0D));
    }

    @Test
    public void predictedExplosionOnlyMarksBreakableBlocksInsideEffectiveRadius() {
        assertTrue(ProjectileWarningPolicy.isPredictedDestroyedBlock(
                1.25D, 2.0D, 2.0F, 30.0F, false));
        assertFalse(ProjectileWarningPolicy.isPredictedDestroyedBlock(
                2.25D, 2.0D, 1.0F, 5.0F, false));
        assertFalse(ProjectileWarningPolicy.isPredictedDestroyedBlock(
                0.25D, 2.0D, -1.0F, 0.0F, false));
        assertFalse(ProjectileWarningPolicy.isPredictedDestroyedBlock(
                0.25D, 2.0D, 1.0F, 1200.0F, true));
    }

    @Test
    public void explosionStrengthIsInferredFromFireballType() {
        assertEquals(0.0D, ProjectileWarningPolicy.inferredExplosionStrength(false, 0), EPSILON);
        assertEquals(2.0D, ProjectileWarningPolicy.inferredExplosionStrength(true, 2), EPSILON);
        assertEquals(1.0D, ProjectileWarningPolicy.inferredExplosionStrength(true, 0), EPSILON);
    }

    @Test
    public void bedAlarmProgressMatchesReferenceDistanceRatioAndClamps() {
        assertEquals(1.0D, ProjectileWarningPolicy.bedAlarmProgress(50.0D, 50.0D), EPSILON);
        assertEquals(0.5D, ProjectileWarningPolicy.bedAlarmProgress(25.0D, 50.0D), EPSILON);
        assertEquals(0.0D, ProjectileWarningPolicy.bedAlarmProgress(0.0D, 50.0D), EPSILON);
        assertEquals(1.0D, ProjectileWarningPolicy.bedAlarmProgress(60.0D, 50.0D), EPSILON);
    }

    @Test
    public void bedWarsStatusMatchesTheReferenceSidebarTransitions() {
        assertEquals(1, ProjectileWarningPolicy.bedWarsStatus("BED WARS",
                Arrays.asList("Waiting...")));
        assertEquals(2, ProjectileWarningPolicy.bedWarsStatus("BED WARS",
                Arrays.asList("R Red: Alive", "B Blue: Alive")));
        assertEquals(0, ProjectileWarningPolicy.bedWarsStatus("BED WARS",
                Arrays.asList("Map  Lighthouse")));
        assertEquals(-1, ProjectileWarningPolicy.bedWarsStatus("SKYWARS",
                Arrays.asList("R Red: Alive")));
    }

    @Test
    public void selectsNearestEligibleEnemyInsideBedWarningRange() {
        ProjectileWarningPolicy.BedThreat selected = ProjectileWarningPolicy.selectNearestBedThreat(
                Arrays.asList(
                        new ProjectileWarningPolicy.BedThreat("teammate", 3.0D, false),
                        new ProjectileWarningPolicy.BedThreat("far", 40.0D, true),
                        new ProjectileWarningPolicy.BedThreat("near", 8.25D, true),
                        new ProjectileWarningPolicy.BedThreat("other", 12.0D, true)),
                32.0D);

        assertEquals("near", selected.getName());
        assertEquals(8.25D, selected.getDistance(), EPSILON);
    }
}
