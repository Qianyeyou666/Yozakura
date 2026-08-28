package gq.yozakura.bridge;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;

import java.util.IdentityHashMap;
import java.util.Map;

/** Distinguishes a real outbound write from a compatibility-successful dropped write. */
public final class PacketWriteDisposition {
    private static final Map<ChannelFuture, Boolean> DROPPED_WRITES =
            new IdentityHashMap<ChannelFuture, Boolean>();

    private PacketWriteDisposition() {
    }

    /**
     * Completes a deliberately dropped write without failing its caller while retaining
     * enough metadata for bridge listeners to reject it as server-visible.
     */
    public static void completeDropped(final ChannelPromise promise) {
        if (promise == null) {
            return;
        }
        if (!promise.isDone()) {
            synchronized (DROPPED_WRITES) {
                DROPPED_WRITES.put(promise, Boolean.TRUE);
            }
            promise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    synchronized (DROPPED_WRITES) {
                        DROPPED_WRITES.remove(future);
                    }
                }
            });
        }
        promise.trySuccess();
    }

    /** True only when Netty completed a write that was not deliberately discarded. */
    public static boolean isServerVisibleSuccess(ChannelFuture future) {
        if (future == null || !future.isSuccess()) {
            return false;
        }
        synchronized (DROPPED_WRITES) {
            return !DROPPED_WRITES.containsKey(future);
        }
    }
}
