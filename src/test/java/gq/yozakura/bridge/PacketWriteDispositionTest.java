package gq.yozakura.bridge;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PacketWriteDispositionTest {
    @Test
    public void compatibilitySuccessfulDropIsNotServerVisible() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelDuplexHandler());
        ChannelPromise promise = channel.newPromise();
        final AtomicBoolean serverVisible = new AtomicBoolean(true);
        promise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                serverVisible.set(PacketWriteDisposition.isServerVisibleSuccess(future));
            }
        });

        PacketWriteDisposition.completeDropped(promise);

        assertTrue("A deliberate drop must still complete the caller promise", promise.isSuccess());
        assertFalse("A deliberate drop must never look server-visible to bridge listeners",
                serverVisible.get());
        channel.finish();
    }

    @Test
    public void ordinarySuccessfulPromiseRemainsServerVisible() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelDuplexHandler());
        ChannelPromise promise = channel.newPromise();
        final AtomicBoolean serverVisible = new AtomicBoolean(false);
        promise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                serverVisible.set(PacketWriteDisposition.isServerVisibleSuccess(future));
            }
        });

        promise.setSuccess();

        assertTrue(serverVisible.get());
        channel.finish();
    }
}
