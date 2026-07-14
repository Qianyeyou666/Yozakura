package gq.yozakura.event.bridge;

import gq.yozakura.event.bus.events.Event;
import net.minecraft.network.Packet;

/** Raised after an outbound packet has completed its Netty write. */
public final class PacketWriteEvent implements Event {
    private final Packet<?> packet;
    private final long writeId;
    private final boolean success;

    public PacketWriteEvent(Packet<?> packet, long writeId, boolean success) {
        this.packet = packet;
        this.writeId = writeId;
        this.success = success;
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public boolean isPacketAccepted() {
        return writeId != PacketAcceptedEvent.NO_WRITE_ID;
    }

    public long getWriteId() {
        return writeId;
    }

    public boolean isSuccess() {
        return success;
    }
}
