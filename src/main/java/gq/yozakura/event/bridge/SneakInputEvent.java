package gq.yozakura.event.bridge;

import gq.yozakura.event.bus.events.Event;

/**
 * Raised after vanilla has sampled keyboard input but before the final sneak
 * multiplier is applied to the frame consumed by legacy movement listeners.
 */
public final class SneakInputEvent implements Event {
    public enum SneakIntent {
        KEEP,
        FORCE_ON,
        FORCE_OFF
    }

    private final int tick;
    private final float rawForward;
    private final float rawStrafe;
    private final boolean jump;
    private final boolean sampledSneak;
    private final boolean physicalSneak;
    private SneakIntent intent = SneakIntent.KEEP;
    private int intentPriority = Integer.MIN_VALUE;
    private float resolvedForward;
    private float resolvedStrafe;
    private boolean resolvedJump;
    private int movementPriority = Integer.MIN_VALUE;
    private boolean movementOverride;

    public SneakInputEvent(int tick, float rawForward, float rawStrafe, boolean jump,
                           boolean sampledSneak, boolean physicalSneak) {
        this.tick = tick;
        this.rawForward = rawForward;
        this.rawStrafe = rawStrafe;
        this.jump = jump;
        this.sampledSneak = sampledSneak;
        this.physicalSneak = physicalSneak;
        this.resolvedForward = rawForward;
        this.resolvedStrafe = rawStrafe;
        this.resolvedJump = jump;
    }

    public int getTick() {
        return tick;
    }

    public float getRawForward() {
        return rawForward;
    }

    public float getRawStrafe() {
        return rawStrafe;
    }

    public boolean isJump() {
        return jump;
    }

    public boolean isSampledSneak() {
        return sampledSneak;
    }

    public boolean isPhysicalSneak() {
        return physicalSneak;
    }

    public void requestSneak(SneakIntent intent, int priority) {
        if (intent != null && priority >= intentPriority) {
            this.intent = intent;
            this.intentPriority = priority;
        }
    }

    public SneakIntent getIntent() {
        return intent;
    }

    /** Lets one high-priority controller own the final movement input frame. */
    public void requestMovement(float forward, float strafe, boolean jump, int priority) {
        if (priority < movementPriority) {
            return;
        }
        resolvedForward = forward;
        resolvedStrafe = strafe;
        resolvedJump = jump;
        movementPriority = priority;
        movementOverride = true;
    }

    public boolean hasMovementOverride() {
        return movementOverride;
    }

    public float getResolvedForward() {
        return resolvedForward;
    }

    public float getResolvedStrafe() {
        return resolvedStrafe;
    }

    public boolean isResolvedJump() {
        return resolvedJump;
    }
}
