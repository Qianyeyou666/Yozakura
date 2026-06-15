package gq.vapulite.bridge;

import net.minecraft.network.Packet;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class PacketBridgeSupport {
    private static final Set<Packet<?>> NO_EVENT_PACKETS =
            Collections.newSetFromMap(new IdentityHashMap<Packet<?>, Boolean>());

    private PacketBridgeSupport() {
    }

    public static void markNoEvent(Packet<?> packet) {
        if (packet == null) {
            return;
        }
        synchronized (NO_EVENT_PACKETS) {
            NO_EVENT_PACKETS.add(packet);
        }
    }

    public static boolean consumeNoEvent(Packet<?> packet) {
        synchronized (NO_EVENT_PACKETS) {
            return NO_EVENT_PACKETS.remove(packet);
        }
    }
}
