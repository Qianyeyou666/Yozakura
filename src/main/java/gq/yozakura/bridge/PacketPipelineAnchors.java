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
        if (pipeline.get(FORGE_BRIDGE_HANDLER_NAME) != null) {
            pipeline.remove(FORGE_BRIDGE_HANDLER_NAME);
        }
        if (pipeline.get(STANDALONE_BRIDGE_HANDLER_NAME) == null) {
            requirePacketHandler(pipeline);
            pipeline.addBefore(PACKET_HANDLER_NAME, STANDALONE_BRIDGE_HANDLER_NAME, handler);
        }
    }

    public static void installDelayHandler(ChannelPipeline pipeline, String handlerName, ChannelHandler handler) {
        requirePipelineAndHandler(pipeline, handler);
        if (!isDelayHandler(handlerName)) {
            throw new IllegalArgumentException("Unknown packet delay handler: " + handlerName);
        }
        if (pipeline.get(handlerName) != null) {
            return;
        }
        String anchor = findDelaySuccessor(pipeline, handlerName);
        if (anchor == null) {
            anchor = findBridgeAnchor(pipeline);
        }
        if (anchor == null) {
            requirePacketHandler(pipeline);
            anchor = PACKET_HANDLER_NAME;
        }
        pipeline.addBefore(anchor, handlerName, handler);
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

    private static String findDelaySuccessor(ChannelPipeline pipeline, String handlerName) {
        for (int index = 0; index < DELAY_HANDLER_ORDER.length; index++) {
            if (!DELAY_HANDLER_ORDER[index].equals(handlerName)) {
                continue;
            }
            for (int successor = index + 1; successor < DELAY_HANDLER_ORDER.length; successor++) {
                if (pipeline.get(DELAY_HANDLER_ORDER[successor]) != null) {
                    return DELAY_HANDLER_ORDER[successor];
                }
            }
            return null;
        }
        return null;
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
