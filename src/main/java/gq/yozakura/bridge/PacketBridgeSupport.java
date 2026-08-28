package gq.yozakura.bridge;

import gq.yozakura.bridge.util.ReflectionUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;

public final class PacketBridgeSupport {
    private static final String FORGE_HANDLER_NAME = "yozakura_event_bridge";
    private static final String STANDALONE_HANDLER_NAME = "yozakura_standalone_event_bridge";
    private static final long NO_WRITE_ID = 0L;
    private static final Map<Packet<?>, Deque<NoEventMarker>> NO_EVENT_PACKETS =
            new IdentityHashMap<Packet<?>, Deque<NoEventMarker>>();
    private static final Map<Packet<?>, Boolean> NON_CANONICAL_PLAYER_PACKETS =
            new IdentityHashMap<Packet<?>, Boolean>();
    private static final Map<Packet<?>, Boolean> PRESERVE_PLAYER_LOOK_PACKETS =
            new IdentityHashMap<Packet<?>, Boolean>();

    private PacketBridgeSupport() {
    }

    public static void markNoEvent(Packet<?> packet) {
        markNoEvent(packet, NO_WRITE_ID, false);
    }

    public static void markNoEvent(Packet<?> packet, long writeId, boolean alreadyBridgeProcessed) {
        storeNoEventMarker(packet, writeId, alreadyBridgeProcessed);
    }

    public static NoEventMarker consumeNoEventMarker(Packet<?> packet) {
        if (packet == null) {
            return NoEventMarker.NONE;
        }
        synchronized (NO_EVENT_PACKETS) {
            Deque<NoEventMarker> markers = NO_EVENT_PACKETS.get(packet);
            if (markers == null) {
                return NoEventMarker.NONE;
            }
            NoEventMarker marker = markers.pollFirst();
            if (markers.isEmpty()) {
                NO_EVENT_PACKETS.remove(packet);
            }
            return marker == null ? NoEventMarker.NONE : marker;
        }
    }

    public static boolean consumeNoEvent(Packet<?> packet) {
        return consumeNoEventMarker(packet).isMarked();
    }

    /** Marks a module-created movement packet so it cannot advance the vanilla tick boundary. */
    public static void markNonCanonicalPlayerPacket(Packet<?> packet) {
        if (packet == null) {
            return;
        }
        synchronized (NO_EVENT_PACKETS) {
            NON_CANONICAL_PLAYER_PACKETS.put(packet, Boolean.TRUE);
        }
    }

    static boolean consumeNonCanonicalPlayerPacket(Packet<?> packet) {
        if (packet == null) {
            return false;
        }
        synchronized (NO_EVENT_PACKETS) {
            return NON_CANONICAL_PLAYER_PACKETS.remove(packet) != null;
        }
    }

    /** Preserves the explicit yaw and pitch of a one-shot synthetic look packet. */
    public static void markPreservePlayerLook(Packet<?> packet) {
        if (packet == null) {
            return;
        }
        synchronized (NO_EVENT_PACKETS) {
            PRESERVE_PLAYER_LOOK_PACKETS.put(packet, Boolean.TRUE);
        }
    }

    static boolean consumePreservePlayerLook(Packet<?> packet) {
        if (packet == null) {
            return false;
        }
        synchronized (NO_EVENT_PACKETS) {
            return PRESERVE_PLAYER_LOOK_PACKETS.remove(packet) != null;
        }
    }

    private static NoEventMarker storeNoEventMarker(Packet<?> packet, long writeId,
                                                     boolean alreadyBridgeProcessed) {
        if (packet == null) {
            return NoEventMarker.NONE;
        }
        synchronized (NO_EVENT_PACKETS) {
            Deque<NoEventMarker> markers = NO_EVENT_PACKETS.get(packet);
            if (markers == null) {
                markers = new ArrayDeque<NoEventMarker>();
                NO_EVENT_PACKETS.put(packet, markers);
            }
            NoEventMarker marker = new NoEventMarker(true, writeId, alreadyBridgeProcessed);
            markers.offerLast(marker);
            return marker;
        }
    }

    private static void removeNoEventMarker(Packet<?> packet, NoEventMarker marker) {
        if (packet == null || marker == null || !marker.isMarked()) {
            return;
        }
        synchronized (NO_EVENT_PACKETS) {
            Deque<NoEventMarker> markers = NO_EVENT_PACKETS.get(packet);
            if (markers == null) {
                return;
            }
            markers.removeLastOccurrence(marker);
            if (markers.isEmpty()) {
                NO_EVENT_PACKETS.remove(packet);
            }
        }
    }

    public static void clearNoEventPackets() {
        synchronized (NO_EVENT_PACKETS) {
            NO_EVENT_PACKETS.clear();
            NON_CANONICAL_PLAYER_PACKETS.clear();
            PRESERVE_PLAYER_LOOK_PACKETS.clear();
        }
    }

    public static void sendNoEvent(final NetworkManager manager, final Packet<?> packet) {
        sendNoEvent(manager, packet, null, NO_WRITE_ID, false);
    }

    public static void sendNoEvent(final NetworkManager manager, final Packet<?> packet,
                                   final ChannelPromise promise, final long writeId,
                                   final boolean alreadyBridgeProcessed) {
        if (manager == null || packet == null) {
            throw new IllegalArgumentException("Network manager and packet are required");
        }
        final Channel channel = ReflectionUtils.getChannel(manager);
        if (channel == null || !channel.isOpen()) {
            IllegalStateException failure = new IllegalStateException("Network channel is unavailable for a no-event send");
            completeFailedWrite(promise, failure);
            throw failure;
        }
        final Runnable sendTask = new Runnable() {
            @Override
            public void run() {
                NoEventMarker marker = NoEventMarker.NONE;
                boolean submitted = false;
                if (!channel.isOpen() || !hasPacketBridge(channel)) {
                    IllegalStateException failure =
                            new IllegalStateException("Packet bridge is unavailable for a no-event send");
                    completeFailedWrite(promise, failure);
                    throw failure;
                }
                try {
                    marker = storeNoEventMarker(packet, writeId, alreadyBridgeProcessed);
                    if (promise == null) {
                        manager.sendPacket(packet, null);
                    } else {
                        channel.writeAndFlush(packet, promise);
                    }
                    submitted = true;
                } finally {
                    if (!submitted) {
                        removeNoEventMarker(packet, marker);
                        completeFailedWrite(promise,
                                new IllegalStateException("Unable to submit no-event packet write"));
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

    private static void completeFailedWrite(ChannelPromise promise, Throwable cause) {
        if (promise != null) {
            promise.tryFailure(cause);
        }
    }

    public static final class NoEventMarker {
        private static final NoEventMarker NONE = new NoEventMarker(false, NO_WRITE_ID, false);

        private final boolean marked;
        private final long writeId;
        private final boolean alreadyBridgeProcessed;

        private NoEventMarker(boolean marked, long writeId, boolean alreadyBridgeProcessed) {
            this.marked = marked;
            this.writeId = writeId;
            this.alreadyBridgeProcessed = alreadyBridgeProcessed;
        }

        public boolean isMarked() {
            return marked;
        }

        public long getWriteId() {
            return writeId;
        }

        public boolean isAlreadyBridgeProcessed() {
            return alreadyBridgeProcessed;
        }
    }

    private static boolean hasPacketBridge(Channel channel) {
        return channel.pipeline().get(FORGE_HANDLER_NAME) != null
                || channel.pipeline().get(STANDALONE_HANDLER_NAME) != null;
    }
}
