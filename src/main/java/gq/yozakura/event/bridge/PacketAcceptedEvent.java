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
    }

    public boolean isOriginalPacketOrderRequired() {
        return originalPacketOrderRequired;
    }

    private static long nextWriteId() {
        long writeId;
        do {
            writeId = NEXT_WRITE_ID.incrementAndGet();
        } while (writeId == NO_WRITE_ID);
        return writeId;
    }
}
