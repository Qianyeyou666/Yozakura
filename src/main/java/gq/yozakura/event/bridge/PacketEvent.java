package gq.vapulite.event.bridge;

import net.minecraft.network.Packet;
import gq.vapulite.event.bus.events.callables.EventCancellable;
import gq.vapulite.event.bus.types.EventType;

public class PacketEvent extends EventCancellable {
    private final EventType type;
    private final Packet<?> packet;

    public PacketEvent(EventType type, Packet<?> packet) {
        this.type = type;
        this.packet = packet;
    }

    public EventType getType() {
        return this.type;
    }

    public Packet<?> getPacket() {
        return this.packet;
    }
}
