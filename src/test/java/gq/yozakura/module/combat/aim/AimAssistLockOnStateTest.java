package gq.yozakura.module.combat.aim;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AimAssistLockOnStateTest {
    @Test
    public void firstFrameAlwaysSnapsToTheHeadPoint() {
        AimAssistLockOnState state = new AimAssistLockOnState();
        state.acquire(7);

        AimAssistLockOnState.Resolution result = state.resolve(
                false, false, false, false,
                20.0F, 10.0F, 90.0F, -5.0F);

        assertEquals(AimAssistLockOnState.Action.SNAP_HEAD, result.getAction());
        assertEquals(90.0F, result.getYaw(), 0.0001F);
        assertEquals(-5.0F, result.getPitch(), 0.0001F);
    }

    @Test
    public void validHeadInputIsNeverModified() {
        AimAssistLockOnState state = lockedState();

        AimAssistLockOnState.Resolution result = state.resolve(
                false, true, true, true,
                12.0F, 3.0F, 90.0F, -5.0F);

        assertEquals(AimAssistLockOnState.Action.KEEP, result.getAction());
        assertEquals(12.0F, result.getYaw(), 0.0001F);
        assertEquals(3.0F, result.getPitch(), 0.0001F);
    }

    @Test
    public void manualHeadExitClampsToTheCurrentHeadBoundary() {
        AimAssistLockOnState state = lockedState();
        state.resolve(false, true, true, true,
                14.0F, 4.0F, 90.0F, -5.0F);

        AimAssistLockOnState.Resolution result = state.resolve(
                false, false, true, true,
                14.0F, 20.0F, 91.0F, -4.0F);

        assertEquals(AimAssistLockOnState.Action.CLAMP_REGION, result.getAction());
        assertEquals(91.0F, result.getYaw(), 0.0001F);
        assertEquals(-4.0F, result.getPitch(), 0.0001F);
    }

    @Test
    public void knockbackAllowsAllThreeZonesButClampsOutsideTheirUnion() {
        AimAssistLockOnState state = lockedState();

        assertEquals(AimAssistLockOnState.Action.KEEP, state.resolve(
                true, false, true, true,
                10.0F, 18.0F, 90.0F, -5.0F).getAction());
        assertEquals(AimAssistLockOnState.Action.CLAMP_REGION, state.resolve(
                true, false, false, true,
                40.0F, 30.0F, 92.0F, -6.0F).getAction());
    }

    @Test
    public void movingHeadUsesCurrentBoundaryInsteadOfAnOldStoredAngle() {
        AimAssistLockOnState state = lockedState();

        AimAssistLockOnState.Resolution result = state.resolve(
                false, false, true, false,
                15.0F, 20.0F, 93.0F, -7.0F);

        assertEquals(AimAssistLockOnState.Action.CLAMP_REGION, result.getAction());
        assertEquals(93.0F, result.getYaw(), 0.0001F);
    }

    @Test
    public void targetSwitchAndResetRequireANewFirstFrameSnap() {
        AimAssistLockOnState state = lockedState();
        state.acquire(8);
        assertEquals(AimAssistLockOnState.Action.SNAP_HEAD, state.resolve(
                false, true, true, true,
                10.0F, 2.0F, 80.0F, -3.0F).getAction());

        state.reset();
        state.acquire(8);
        assertEquals(AimAssistLockOnState.Action.SNAP_HEAD, state.resolve(
                false, true, true, true,
                10.0F, 2.0F, 70.0F, -4.0F).getAction());
    }

    @Test
    public void exposesWhetherTheNextSnapIsInitialAcquisition() {
        AimAssistLockOnState state = new AimAssistLockOnState();
        state.acquire(7);
        assertEquals(true, state.isAiming());

        state.resolve(false, false, false, false,
                0.0F, 0.0F, 90.0F, -5.0F);
        assertEquals(false, state.isAiming());
    }

    private static AimAssistLockOnState lockedState() {
        AimAssistLockOnState state = new AimAssistLockOnState();
        state.acquire(7);
        state.resolve(false, false, false, false,
                0.0F, 0.0F, 90.0F, -5.0F);
        return state;
    }
}
