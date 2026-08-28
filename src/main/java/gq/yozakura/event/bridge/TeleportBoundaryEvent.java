package gq.yozakura.event.bridge;

import net.minecraft.network.play.server.S08PacketPlayerPosLook;

/**
 * Published after an inbound teleport survives PacketEvent cancellation and
 * immediately before Vanilla applies it. Outbound delay owners use this
 * boundary to discard stale pre-teleport traffic and protect the required
 * position confirmation packet.
 */
public final class TeleportBoundaryEvent {
    private final S08PacketPlayerPosLook packet;

    public TeleportBoundaryEvent(S08PacketPlayerPosLook packet) {
        this.packet = packet;
    }

    public S08PacketPlayerPosLook getPacket() {
        return packet;
    }
}
