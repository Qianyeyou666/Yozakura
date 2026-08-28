package gq.yozakura.module.render;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ProjectileRayPolicyTest {
    private static final double EPSILON = 0.0001D;

    @Test
    public void bowChargeUsesVanillaCurveAndRejectsWeakRelease() {
        assertEquals(0.0D, ProjectileRayPolicy.bowPower(0), EPSILON);
        assertEquals(5.0D / 12.0D, ProjectileRayPolicy.bowPower(10), EPSILON);
        assertEquals(1.0D, ProjectileRayPolicy.bowPower(20), EPSILON);
        assertEquals(1.0D, ProjectileRayPolicy.bowPower(60), EPSILON);

        assertNull(ProjectileRayPolicy.launchSpec(
                ProjectileRayPolicy.ProjectileKind.BOW, 1, 0.0F, 0.0F));
        assertNotNull(ProjectileRayPolicy.launchSpec(
                ProjectileRayPolicy.ProjectileKind.BOW, 20, 0.0F, 0.0F));
    }

    @Test
    public void viewRotationProducesVanillaLaunchDirection() {
        ProjectileRayPolicy.LaunchSpec forward = ProjectileRayPolicy.launchSpec(
                ProjectileRayPolicy.ProjectileKind.BOW, 20, 0.0F, 0.0F);
        ProjectileRayPolicy.LaunchSpec left = ProjectileRayPolicy.launchSpec(
                ProjectileRayPolicy.ProjectileKind.BOW, 20, 90.0F, 0.0F);
        ProjectileRayPolicy.LaunchSpec upward = ProjectileRayPolicy.launchSpec(
                ProjectileRayPolicy.ProjectileKind.BOW, 20, 0.0F, -30.0F);

        assertEquals(0.0D, forward.getMotion().getX(), EPSILON);
        assertEquals(0.0D, forward.getMotion().getY(), EPSILON);
        assertEquals(3.0D, forward.getMotion().getZ(), EPSILON);
        assertEquals(-3.0D, left.getMotion().getX(), EPSILON);
        assertEquals(0.0D, left.getMotion().getZ(), EPSILON);
        assertEquals(1.5D, upward.getMotion().getY(), EPSILON);
    }

    @Test
    public void throwableTypesUseVanillaSpeedGravityAndDrag() {
        ProjectileRayPolicy.ProjectileKind[] kinds = {
                ProjectileRayPolicy.ProjectileKind.SNOWBALL,
                ProjectileRayPolicy.ProjectileKind.EGG,
                ProjectileRayPolicy.ProjectileKind.ENDER_PEARL
        };

        for (ProjectileRayPolicy.ProjectileKind kind : kinds) {
            ProjectileRayPolicy.LaunchSpec spec = ProjectileRayPolicy.launchSpec(
                    kind, 0, 0.0F, 0.0F);
            assertNotNull(spec);
            assertEquals(1.5D, spec.getMotion().length(), EPSILON);
            assertEquals(0.03D, spec.getGravity(), EPSILON);
            assertEquals(0.99D, spec.getDrag(), EPSILON);
        }
    }

    @Test
    public void traceAppliesDragAndGravityWithoutChangingAim() {
        ProjectileRayPolicy.LaunchSpec spec = ProjectileRayPolicy.launchSpec(
                ProjectileRayPolicy.ProjectileKind.SNOWBALL, 0, 0.0F, 0.0F);
        List<ProjectileRayPolicy.Point> points = ProjectileRayPolicy.trace(
                new ProjectileRayPolicy.Point(0.0D, 10.0D, 0.0D), spec, 2);

        assertEquals(3, points.size());
        assertEquals(1.5D, points.get(1).getZ(), EPSILON);
        assertEquals(2.985D, points.get(2).getZ(), EPSILON);
        assertEquals(9.97D, points.get(2).getY(), EPSILON);
    }
}
