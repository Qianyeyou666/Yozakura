package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FreeLookCameraStateTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void mouseInputMovesCameraWithoutChangingPlayerFacing() {
        FreeLookCameraState state = new FreeLookCameraState();
        state.begin(35.0F, 10.0F);

        FreeLookCameraState.Frame frame = state.captureInput(50.0F, 24.0F);

        assertEquals(35.0F, frame.getPlayerYaw(), EPSILON);
        assertEquals(10.0F, frame.getPlayerPitch(), EPSILON);
        assertEquals(50.0F, frame.getCameraYaw(), EPSILON);
        assertEquals(24.0F, frame.getCameraPitch(), EPSILON);
    }

    @Test
    public void pitchIsClampedAndYawDeltaWrapsAcrossBoundary() {
        FreeLookCameraState state = new FreeLookCameraState();
        state.begin(179.0F, 80.0F);

        FreeLookCameraState.Frame frame = state.captureInput(-171.0F, 120.0F);

        assertEquals(189.0F, frame.getCameraYaw(), EPSILON);
        assertEquals(90.0F, frame.getCameraPitch(), EPSILON);
    }

    @Test
    public void endRestoresOriginalPerspectiveAndFacing() {
        FreeLookCameraState state = new FreeLookCameraState();
        state.begin(-45.0F, -12.0F);
        state.captureInput(15.0F, 30.0F);

        FreeLookCameraState.Restore restore = state.end(0);

        assertFalse(state.isActive());
        assertEquals(-45.0F, restore.getYaw(), EPSILON);
        assertEquals(-12.0F, restore.getPitch(), EPSILON);
        assertEquals(0, restore.getPerspective());
    }

    @Test
    public void beginMarksSessionActive() {
        FreeLookCameraState state = new FreeLookCameraState();
        assertFalse(state.isActive());
        state.begin(0.0F, 0.0F);
        assertTrue(state.isActive());
    }
}
