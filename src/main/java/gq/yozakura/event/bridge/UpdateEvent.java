package gq.yozakura.event.bridge;

import gq.yozakura.event.bus.events.Event;
import gq.yozakura.event.bus.types.EventType;

public class UpdateEvent implements Event {
    private final EventType type;
    private final float yaw;
    private final float pitch;
    private float newYaw;
    private float newPitch;
    private float prevYaw;
    private int lastPriority = -1;
    private int movementPriority = -1;
    private boolean rotated = false;
    private boolean moveFix = false;

    public UpdateEvent(EventType type, float yaw, float pitch, float newYaw, float newPitch) {
        this.type = type;
        this.yaw = yaw;
        this.pitch = pitch;
        this.newYaw = newYaw;
        this.newPitch = newPitch;
        this.prevYaw = newYaw;
    }

    public EventType getType() {
        return this.type;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public float getNewYaw() {
        return this.newYaw;
    }

    public float getNewPitch() {
        return this.newPitch;
    }

    public float getPreYaw() {
        return this.prevYaw;
    }

    public int isRotating() {
        return this.movementPriority;
    }

    public boolean isRotated() {
        return this.rotated;
    }

    public boolean isMoveFix() {
        return this.moveFix;
    }

    public boolean trySetRotation(float yaw, float pitch, int priority) {
        if (this.type != EventType.PRE || this.lastPriority > priority) {
            return false;
        }
        this.newYaw = yaw;
        this.newPitch = pitch;
        this.lastPriority = priority;
        this.rotated = true;
        return true;
    }

    public void setRotation(float yaw, float pitch, int priority) {
        trySetRotation(yaw, pitch, priority);
    }

    public void setPervRotation(float yaw, int priority) {
        setPervRotation(yaw, priority, true);
    }

    public void setPervRotation(float yaw, int priority, boolean remapInput) {
        if (this.type == EventType.PRE && this.movementPriority < priority) {
            this.prevYaw = yaw;
            this.movementPriority = priority;
            this.rotated = true;
            this.moveFix = remapInput;
        }
    }
}
