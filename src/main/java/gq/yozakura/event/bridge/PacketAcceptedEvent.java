package gq.yozakura.event.bridge;

import gq.yozakura.event.bus.events.Event;
import net.minecraft.network.Packet;

import java.util.concurrent.atomic.AtomicLong;

/** Raised after outbound packet listeners accept a packet for bridge processing. */
public final class PacketAcceptedEvent implements Event {
    public static final long NO_WRITE_ID = 0L;
    private static final AtomicLong NEXT_WRITE_ID = new AtomicLong();

    private final Packet<?> packet;
    private final long writeId;
    private boolean originalPacketOrderRequired;
    private boolean strictOriginalPacketOrderRequired;
    private boolean afterCurrentRotationRequired;

    public PacketAcceptedEvent(Packet<?> packet) {
        this.packet = packet;
        this.writeId = nextWriteId();
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public long getWriteId() {
        return writeId;
    }

    /**
     * Keeps this accepted packet at the position where vanilla submitted it,
     * rather than deferring it behind a bridge-side rotation action batch.
     */
    public void requestOriginalPacketOrder() {
        originalPacketOrderRequired = true;
        afterCurrentRotationRequired = false;
    }

    public boolean isOriginalPacketOrderRequired() {
        return originalPacketOrderRequired;
    }

    /**
     * Locks this packet to its original submission position. Later listeners
     * cannot move it behind a silent-rotation publication.
     */
    public void requestStrictOriginalPacketOrder() {
        strictOriginalPacketOrderRequired = true;
        originalPacketOrderRequired = true;
        afterCurrentRotationRequired = false;
    }

    public boolean isStrictOriginalPacketOrderRequired() {
        return strictOriginalPacketOrderRequired;
    }

    /**
     * Defers this accepted action until the current canonical player packet has
     * published its silent rotation. This is narrower than the normal action
     * batch: it is flushed immediately after that C03 in the same client tick.
     */
    public void requestAfterCurrentRotation() {
        if (strictOriginalPacketOrderRequired) {
            return;
        }
        afterCurrentRotationRequired = true;
        originalPacketOrderRequired = false;
    }

    public boolean isAfterCurrentRotationRequired() {
        return afterCurrentRotationRequired;
    }

    private static long nextWriteId() {
        long writeId;
        do {
            writeId = NEXT_WRITE_ID.incrementAndGet();
        } while (writeId == NO_WRITE_ID);
        return writeId;
    }
}
