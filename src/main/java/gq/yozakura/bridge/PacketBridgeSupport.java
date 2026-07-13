package gq.yozakura.bridge;

import io.netty.channel.Channel;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

public final class PacketBridgeSupport {
    private static final String FORGE_HANDLER_NAME = "yozakura_event_bridge";
    private static final String STANDALONE_HANDLER_NAME = "yozakura_standalone_event_bridge";
    private static final Map<Packet<?>, Integer> NO_EVENT_PACKETS =
            new IdentityHashMap<Packet<?>, Integer>();
    private static volatile Field channelField;

    private PacketBridgeSupport() {
    }

    public static void markNoEvent(Packet<?> packet) {
        if (packet == null) {
            return;
        }
        synchronized (NO_EVENT_PACKETS) {
            Integer count = NO_EVENT_PACKETS.get(packet);
            NO_EVENT_PACKETS.put(packet, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
        }
    }

    public static boolean consumeNoEvent(Packet<?> packet) {
        synchronized (NO_EVENT_PACKETS) {
            Integer count = NO_EVENT_PACKETS.get(packet);
            if (count == null) {
                return false;
            }
            if (count.intValue() > 1) {
                NO_EVENT_PACKETS.put(packet, Integer.valueOf(count.intValue() - 1));
            } else {
                NO_EVENT_PACKETS.remove(packet);
            }
            return true;
        }
    }

    public static void clearNoEventPackets() {
        synchronized (NO_EVENT_PACKETS) {
            NO_EVENT_PACKETS.clear();
        }
    }

    public static void sendNoEvent(final NetworkManager manager, final Packet<?> packet) {
        if (manager == null || packet == null) {
            throw new IllegalArgumentException("Network manager and packet are required");
        }
        final Channel channel = getChannel(manager);
        if (channel == null || !channel.isOpen()) {
            throw new IllegalStateException("Network channel is unavailable for a no-event send");
        }
        final Runnable sendTask = new Runnable() {
            @Override
            public void run() {
                if (!channel.isOpen() || !hasPacketBridge(channel)) {
                    throw new IllegalStateException("Packet bridge is unavailable for a no-event send");
                }
                markNoEvent(packet);
                boolean submitted = false;
                try {
                    manager.sendPacket(packet, null);
                    submitted = true;
                } finally {
                    if (!submitted) {
                        consumeNoEvent(packet);
                    }
                }
            }
        };
        if (channel.eventLoop().inEventLoop()) {
            sendTask.run();
        } else {
            channel.eventLoop().execute(sendTask);
        }
    }

    private static boolean hasPacketBridge(Channel channel) {
        return channel.pipeline().get(FORGE_HANDLER_NAME) != null
                || channel.pipeline().get(STANDALONE_HANDLER_NAME) != null;
    }

    private static Channel getChannel(NetworkManager manager) {
        try {
            Field field = channelField;
            if (field == null) {
                for (String name : new String[]{"channel", "field_150746_k", "k"}) {
                    try {
                        field = NetworkManager.class.getDeclaredField(name);
                        field.setAccessible(true);
                        channelField = field;
                        break;
                    } catch (NoSuchFieldException ignored) {
                    }
                }
            }
            Object value = field == null ? null : field.get(manager);
            return value instanceof Channel ? (Channel) value : null;
        } catch (Throwable ignored) {
            channelField = null;
            return null;
        }
    }
}
