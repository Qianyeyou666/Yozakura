package gq.yozakura.event.bridge;

import gq.yozakura.event.bus.events.Event;

/** Raised on the client thread after the final PRE rotation snapshot is published. */
public final class RotationPublishedEvent implements Event {
    private final UpdateEvent update;

    public RotationPublishedEvent(UpdateEvent update) {
        this.update = update;
    }

    public UpdateEvent getUpdate() {
        return update;
    }

    public boolean isRotated() {
        return update != null && update.isRotated();
    }

    public float getYaw() {
        return update == null ? 0.0F : update.getNewYaw();
    }

    public float getPitch() {
        return update == null ? 0.0F : update.getNewPitch();
    }
}
