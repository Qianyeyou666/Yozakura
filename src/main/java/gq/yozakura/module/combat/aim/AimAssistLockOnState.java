package gq.yozakura.module.combat.aim;

/** Two-stage Lock-on state with no smoothing or speed policy. */
public final class AimAssistLockOnState {
    public enum Action {
        KEEP,
        CLAMP_REGION,
        SNAP_HEAD
    }

    private int targetId = -1;
    private boolean aiming = true;
    private boolean hasLastHeadRotation;
    private float lastHeadYaw;
    private float lastHeadPitch;

    public void acquire(int entityId) {
        if (entityId != targetId) {
            targetId = entityId;
            aiming = true;
            hasLastHeadRotation = false;
        }
    }

    public Resolution resolve(boolean disruptionActive, boolean currentHitsHead,
                              boolean currentHitsAny, boolean lastStoredHeadStillHits,
                              float currentYaw, float currentPitch,
                              float snapYaw, float snapPitch) {
        if (aiming || targetId < 0) {
            return snap(snapYaw, snapPitch);
        }
        if (disruptionActive) {
            if (currentHitsAny) {
                if (currentHitsHead) {
                    storeHead(currentYaw, currentPitch);
                }
                return new Resolution(Action.KEEP, currentYaw, currentPitch);
            }
            storeHead(snapYaw, snapPitch);
            return new Resolution(Action.CLAMP_REGION, snapYaw, snapPitch);
        }
        if (currentHitsHead) {
            storeHead(currentYaw, currentPitch);
            return new Resolution(Action.KEEP, currentYaw, currentPitch);
        }
        storeHead(snapYaw, snapPitch);
        return new Resolution(Action.CLAMP_REGION, snapYaw, snapPitch);
    }

    public boolean hasLastHeadRotation() {
        return hasLastHeadRotation;
    }

    public boolean isAiming() {
        return aiming;
    }

    public float getLastHeadYaw() {
        return lastHeadYaw;
    }

    public float getLastHeadPitch() {
        return lastHeadPitch;
    }

    public void reset() {
        targetId = -1;
        aiming = true;
        hasLastHeadRotation = false;
        lastHeadYaw = 0.0F;
        lastHeadPitch = 0.0F;
    }

    private Resolution snap(float yaw, float pitch) {
        aiming = false;
        storeHead(yaw, pitch);
        return new Resolution(Action.SNAP_HEAD, yaw, pitch);
    }

    private void storeHead(float yaw, float pitch) {
        hasLastHeadRotation = true;
        lastHeadYaw = yaw;
        lastHeadPitch = pitch;
    }

    public static final class Resolution {
        private final Action action;
        private final float yaw;
        private final float pitch;

        private Resolution(Action action, float yaw, float pitch) {
            this.action = action;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public Action getAction() {
            return action;
        }

        public float getYaw() {
            return yaw;
        }

        public float getPitch() {
            return pitch;
        }
    }
}
