package gq.yozakura.bridge;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;

import java.util.List;

/**
 * Keeps the bridge and packet-delay handlers in a fixed pipeline order.
 *
 * Pipeline order is head to tail. Outbound traffic therefore sees the bridge
 * before a delay handler, while inbound traffic reaches the bridge only after
 * an inbound delay handler releases its packet.
 */
public final class PacketPipelineAnchors {
    public static final String PACKET_HANDLER_NAME = "packet_handler";
    public static final String FORGE_BRIDGE_HANDLER_NAME = "yozakura_event_bridge";
    public static final String STANDALONE_BRIDGE_HANDLER_NAME = "yozakura_standalone_event_bridge";
    public static final String BACKTRACK_HANDLER_NAME = "yozakura_backtrack";
    public static final String FAKE_LAG_HANDLER_NAME = "yozakura_fakelag";
    public static final String LAG_RANGE_HANDLER_NAME = "yozakura_lag_range";

    private static final String[] DELAY_HANDLER_ORDER = new String[]{
            BACKTRACK_HANDLER_NAME,
            FAKE_LAG_HANDLER_NAME,
            LAG_RANGE_HANDLER_NAME
    };

    private PacketPipelineAnchors() {
    }

    public static void installStandaloneBridge(ChannelPipeline pipeline, ChannelHandler handler) {
        requirePipelineAndHandler(pipeline, handler);
        if (pipeline.get(STANDALONE_BRIDGE_HANDLER_NAME) == null) {
            requirePacketHandler(pipeline);
            pipeline.addBefore(PACKET_HANDLER_NAME, STANDALONE_BRIDGE_HANDLER_NAME, handler);
        }
        normalizeDelayedHandlersBefore(pipeline, STANDALONE_BRIDGE_HANDLER_NAME);
    }

    public static void installDelayHandler(ChannelPipeline pipeline, String handlerName, ChannelHandler handler) {
        requirePipelineAndHandler(pipeline, handler);
        if (!isDelayHandler(handlerName)) {
            throw new IllegalArgumentException("Unknown packet delay handler: " + handlerName);
        }
        String anchor = findBridgeAnchor(pipeline);
        if (anchor == null) {
            requirePacketHandler(pipeline);
            anchor = PACKET_HANDLER_NAME;
        }
        if (pipeline.get(handlerName) == null) {
            pipeline.addBefore(anchor, handlerName, handler);
        }
        normalizeDelayedHandlersBefore(pipeline, anchor);
    }

    static void normalizeDelayedHandlersBefore(ChannelPipeline pipeline, String anchor) {
        if (pipeline.get(anchor) == null) {
            throw new IllegalStateException("Packet pipeline anchor is unavailable: " + anchor);
        }
        String next = anchor;
        for (int index = DELAY_HANDLER_ORDER.length - 1; index >= 0; index--) {
            String handlerName = DELAY_HANDLER_ORDER[index];
            ChannelHandler handler = pipeline.get(handlerName);
            if (handler == null) {
                continue;
            }
            if (!isImmediatelyBefore(pipeline, handlerName, next)) {
                pipeline.remove(handlerName);
                pipeline.addBefore(next, handlerName, handler);
            }
            next = handlerName;
        }
    }

    private static boolean isDelayHandler(String handlerName) {
        for (String knownHandler : DELAY_HANDLER_ORDER) {
            if (knownHandler.equals(handlerName)) {
                return true;
            }
        }
        return false;
    }

    private static String findBridgeAnchor(ChannelPipeline pipeline) {
        List<String> names = pipeline.names();
        int standalone = names.indexOf(STANDALONE_BRIDGE_HANDLER_NAME);
        int forge = names.indexOf(FORGE_BRIDGE_HANDLER_NAME);
        if (standalone < 0 && forge < 0) {
            return null;
        }
        return standalone > forge ? STANDALONE_BRIDGE_HANDLER_NAME : FORGE_BRIDGE_HANDLER_NAME;
    }

    private static boolean isImmediatelyBefore(ChannelPipeline pipeline, String handlerName, String next) {
        List<String> names = pipeline.names();
        int handlerIndex = names.indexOf(handlerName);
        int nextIndex = names.indexOf(next);
        return handlerIndex >= 0 && nextIndex == handlerIndex + 1;
    }

    private static void requirePipelineAndHandler(ChannelPipeline pipeline, ChannelHandler handler) {
        if (pipeline == null || handler == null) {
            throw new IllegalArgumentException("Packet pipeline and handler are required");
        }
    }

    private static void requirePacketHandler(ChannelPipeline pipeline) {
        if (pipeline.get(PACKET_HANDLER_NAME) == null) {
            throw new IllegalStateException("Minecraft packet_handler is unavailable");
        }
    }
}
