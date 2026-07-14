package gq.yozakura.event.bridge;

import gq.yozakura.event.bus.events.Event;

/** Immutable snapshot of a PRE update after all rotation claims are resolved. */
public final class RotationResolvedEvent implements Event {
    private final float yaw;
    private final float pitch;
    private final boolean rotated;

    public RotationResolvedEvent(UpdateEvent update) {
        this.yaw = update.getNewYaw();
        this.pitch = update.getNewPitch();
        this.rotated = update.isRotated();
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isRotated() {
        return rotated;
    }
}
