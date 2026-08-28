package gq.yozakura.event.bridge;

import gq.yozakura.event.bus.events.Event;

/**
 * Raised after the bridge's one canonical player packet for a client tick
 * successfully reaches Netty.
 */
public final class PlayerPacketBoundaryEvent implements Event {
    private final long writeId;
    private final float yaw;
    private final float pitch;
    private final boolean rotated;

    public PlayerPacketBoundaryEvent(long writeId, float yaw, float pitch, boolean rotated) {
        this.writeId = writeId;
        this.yaw = yaw;
        this.pitch = pitch;
        this.rotated = rotated;
    }

    public long getWriteId() {
        return writeId;
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

    public boolean isPacketAccepted() {
        return writeId != PacketAcceptedEvent.NO_WRITE_ID;
    }
}
