package gq.yozakura.event.bridge;

import gq.yozakura.event.bus.events.Event;

/**
 * Raised after the bridge's one canonical player packet for a client tick
 * successfully reaches Netty.
 */
public final class PlayerPacketBoundaryEvent implements Event {
    private final long writeId;

    public PlayerPacketBoundaryEvent(long writeId) {
        this.writeId = writeId;
    }

    public long getWriteId() {
        return writeId;
    }

    public boolean isPacketAccepted() {
        return writeId != PacketAcceptedEvent.NO_WRITE_ID;
    }
}
