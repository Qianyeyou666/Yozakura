package gq.yozakura.module.world;

/** Pure state machine for BridgeAssist's per-input-frame sneak decision. */
final class BridgeAssistSneakStateMachine {
    private static final int NO_TICK = -1;

    enum State {
        IDLE,
        JUMP_HELD,
        EDGE_HELD,
        UNSNEAK_DELAY,
        PHYSICAL_RELEASE_HELD
    }

    enum Decision {
        KEEP,
        FORCE_ON,
        FORCE_OFF
    }

    private State state = State.IDLE;
    private int releaseTick = NO_TICK;
    private int jumpHoldUntilTick = NO_TICK;
    private boolean placementObserved;

    Decision update(Frame frame) {
        if (!frame.available) {
            reset();
            return Decision.KEEP;
        }

        if (!canControl(frame)) {
            reset();
            return Decision.KEEP;
        }

        if (frame.placementPending) {
            if (!holdsModuleSneak()) {
                state = State.EDGE_HELD;
                releaseTick = NO_TICK;
                jumpHoldUntilTick = NO_TICK;
                placementObserved = false;
            }
            return Decision.FORCE_ON;
        }

        if (frame.placementCommitted && holdsModuleSneak()) {
            placementObserved = true;
        }

        if (needsSneak(frame)) {
            if (state != State.EDGE_HELD && state != State.UNSNEAK_DELAY) {
                placementObserved = false;
            }
            state = State.EDGE_HELD;
            releaseTick = NO_TICK;
            jumpHoldUntilTick = NO_TICK;
            return Decision.FORCE_ON;
        }

        if (shouldStartJumpHold(frame)) {
            state = State.JUMP_HELD;
            releaseTick = NO_TICK;
            jumpHoldUntilTick = frame.tick + frame.jumpHoldTicks;
            return Decision.FORCE_ON;
        }

        if (state == State.PHYSICAL_RELEASE_HELD) {
            return Decision.FORCE_OFF;
        }

        if (state == State.JUMP_HELD) {
            if (frame.tick < jumpHoldUntilTick) {
                return Decision.FORCE_ON;
            }
            return finishHold(frame);
        }

        if (state == State.EDGE_HELD) {
            state = State.UNSNEAK_DELAY;
            releaseTick = frame.tick + Math.max(0, frame.unsneakDelayTicks);
        }

        if (state != State.UNSNEAK_DELAY) {
            return Decision.KEEP;
        }

        if (frame.tick < holdUntilTick()) {
            return Decision.FORCE_ON;
        }

        return finishHold(frame);
    }

    private Decision finishHold(Frame frame) {
        if (frame.requirePhysicalSneak && frame.physicalSneak
                && (placementObserved || !frame.onGround)) {
            state = State.PHYSICAL_RELEASE_HELD;
            return Decision.FORCE_OFF;
        }

        reset();
        return Decision.KEEP;
    }

    void reset() {
        state = State.IDLE;
        releaseTick = NO_TICK;
        jumpHoldUntilTick = NO_TICK;
        placementObserved = false;
    }

    State getState() {
        return state;
    }

    private boolean canControl(Frame frame) {
        if (!frame.moving) {
            return false;
        }
        if (frame.requirePhysicalSneak) {
            return frame.physicalSneak;
        }
        return !frame.physicalSneak;
    }

    private boolean holdsModuleSneak() {
        return state == State.JUMP_HELD || state == State.EDGE_HELD || state == State.UNSNEAK_DELAY;
    }

    private boolean shouldStartJumpHold(Frame frame) {
        return frame.jump
                && frame.onGround
                && frame.jumpHoldTicks > 0
                && (!frame.requirePhysicalSneak || state == State.PHYSICAL_RELEASE_HELD);
    }

    private boolean needsSneak(Frame frame) {
        if (frame.edge) {
            return true;
        }
        return frame.voidBelow && frame.onGround && !frame.jump;
    }

    private int holdUntilTick() {
        return Math.max(releaseTick, jumpHoldUntilTick);
    }

    static final class Frame {
        final int tick;
        final boolean available;
        final boolean moving;
        final boolean physicalSneak;
        final boolean requirePhysicalSneak;
        final boolean jump;
        final boolean onGround;
        final boolean edge;
        final boolean voidBelow;
        final boolean placementPending;
        final boolean placementCommitted;
        final int unsneakDelayTicks;
        final int jumpHoldTicks;

        Frame(int tick, boolean available, boolean moving, boolean physicalSneak,
              boolean requirePhysicalSneak, boolean jump, boolean onGround, boolean edge,
              boolean voidBelow, boolean placementPending, boolean placementCommitted, int unsneakDelayTicks,
              int jumpHoldTicks) {
            this.tick = tick;
            this.available = available;
            this.moving = moving;
            this.physicalSneak = physicalSneak;
            this.requirePhysicalSneak = requirePhysicalSneak;
            this.jump = jump;
            this.onGround = onGround;
            this.edge = edge;
            this.voidBelow = voidBelow;
            this.placementPending = placementPending;
            this.placementCommitted = placementCommitted;
            this.unsneakDelayTicks = unsneakDelayTicks;
            this.jumpHoldTicks = jumpHoldTicks;
        }
    }
}
