package gq.yozakura.module.world;

/**
 * Tick-aligned planner for BridgeAssist's generic technique modes.
 *
 * <p>The complete TellyBridge runtime is owned by {@link TellyBridgeRuntime}.
 * This generic planner intentionally contains only the Legit/GodBridge runtime path so
 * there is a single owner for Telly activation, movement, rotation and placement state.</p>
 */
final class BridgeAssistBridgeModeStateMachine {
    private static final int NO_TICK = -1;
    private static final int GOD_BRIDGE_ACTIVATION_PLACEMENTS = 1;
    private static final int GOD_BRIDGE_SETTLE_TICKS = 10;

    enum Mode {
        Legit,
        GodBridge,
        TellyBridge
    }

    private int godBridgePlacements;
    private int godBridgeStartTick = NO_TICK;
    private int airborneTicks;
    private Mode previousMode = Mode.Legit;

    Plan update(Mode selectedMode, Frame frame) {
        Mode mode = selectedMode == null ? Mode.Legit : selectedMode;
        if (mode != previousMode) {
            resetCycle();
            if (mode == Mode.GodBridge || previousMode == Mode.GodBridge) {
                godBridgePlacements = 0;
            }
            previousMode = mode;
        }

        if (mode == Mode.GodBridge) {
            return planGodBridge(frame);
        }
        resetCycle();
        return Plan.inactive();
    }

    void recordManualPlacement(Mode selectedMode) {
        Mode mode = selectedMode == null ? Mode.Legit : selectedMode;
        if (mode == Mode.GodBridge && godBridgePlacements < Integer.MAX_VALUE) {
            godBridgePlacements++;
        }
    }

    void recordManualPlacement() {
        recordManualPlacement(previousMode);
    }

    void reset() {
        godBridgePlacements = 0;
        previousMode = Mode.Legit;
        resetCycle();
    }

    int getAirborneTicks() {
        return airborneTicks;
    }

    static int eightWayDirectionFromYaw(float yaw) {
        int octant = (int) Math.round(positiveDegrees(yaw + 180.0F) / 45.0F) & 7;
        return new int[]{7, 2, 8, 3, 5, 4, 6, 1}[octant];
    }

    static int offsetX(int direction) {
        switch (direction) {
            case 1:
            case 4:
            case 6:
                return 1;
            case 2:
            case 3:
            case 8:
                return -1;
            default:
                return 0;
        }
    }

    static int offsetZ(int direction) {
        switch (direction) {
            case 1:
            case 2:
            case 7:
                return 1;
            case 3:
            case 4:
            case 5:
                return -1;
            default:
                return 0;
        }
    }

    private Plan planGodBridge(Frame frame) {
        boolean activationHeld = frame.backwardPressed && frame.useItemPressed;
        if (!activationHeld) {
            godBridgePlacements = 0;
            godBridgeStartTick = NO_TICK;
            return Plan.inactive();
        }
        if (godBridgePlacements < GOD_BRIDGE_ACTIVATION_PLACEMENTS) {
            godBridgeStartTick = NO_TICK;
            return Plan.inactive();
        }
        if (godBridgeStartTick == NO_TICK) {
            godBridgeStartTick = frame.tick;
        }

        int direction = eightWayDirectionFromYaw(frame.yaw);
        float yaw = godBridgeYaw(direction);
        float pitch = frame.tick - godBridgeStartTick >= GOD_BRIDGE_SETTLE_TICKS ? 83.0F : 81.0F;
        return Plan.godBridge(yaw, pitch);
    }

    private void resetCycle() {
        godBridgeStartTick = NO_TICK;
        airborneTicks = 0;
    }

    private static float godBridgeYaw(int direction) {
        switch (direction) {
            case 1:
                return 135.0F;
            case 2:
                return -135.0F;
            case 3:
                return -45.0F;
            default:
                return 45.0F;
        }
    }

    private static float positiveDegrees(float value) {
        float normalized = value % 360.0F;
        return normalized < 0.0F ? normalized + 360.0F : normalized;
    }

    static final class Frame {
        final int tick;
        final float yaw;
        final boolean onGround;
        final boolean backwardPressed;
        final boolean useItemPressed;
        final float verticalVelocity;

        Frame(int tick, float yaw, boolean onGround, boolean backwardPressed, boolean useItemPressed) {
            this(tick, yaw, onGround, backwardPressed, useItemPressed, onGround ? 0.0F : -0.08F);
        }

        Frame(int tick, float yaw, boolean onGround, boolean backwardPressed, boolean useItemPressed,
              float verticalVelocity) {
            this.tick = tick;
            this.yaw = yaw;
            this.onGround = onGround;
            this.backwardPressed = backwardPressed;
            this.useItemPressed = useItemPressed;
            this.verticalVelocity = verticalVelocity;
        }
    }

    static final class Plan {
        private final boolean active;
        private final boolean shouldJump;
        private final float setupYaw;
        private final float setupPitch;

        private Plan(boolean active, boolean shouldJump, float setupYaw, float setupPitch) {
            this.active = active;
            this.shouldJump = shouldJump;
            this.setupYaw = setupYaw;
            this.setupPitch = setupPitch;
        }

        static Plan inactive() {
            return new Plan(false, false, 0.0F, 0.0F);
        }

        static Plan godBridge(float setupYaw, float setupPitch) {
            return new Plan(true, false, setupYaw, setupPitch);
        }

        boolean isActive() {
            return active;
        }

        boolean shouldJump() {
            return shouldJump;
        }

        float getSetupYaw() {
            return setupYaw;
        }

        float getSetupPitch() {
            return setupPitch;
        }
    }
}
