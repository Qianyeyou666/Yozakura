package gq.yozakura.manager;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import gq.yozakura.manager.BlinkModules;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.util.module.PacketUtil;
import io.netty.channel.ChannelPromise;

import java.util.ArrayDeque;
import java.util.Deque;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.ConcurrentLinkedDeque;

public class BlinkManager {
    public static Minecraft mc = Minecraft.getMinecraft();
    private BlinkModules blinkModule = BlinkModules.NONE;
    private boolean blinking = false;
    private final Deque<BlinkEntry> blinkedPackets = new ConcurrentLinkedDeque<BlinkEntry>();

    public synchronized boolean offerPacket(Packet<?> packet) {
        return offerPacket(packet, null, 0L, false);
    }

    public synchronized boolean offerPacket(Packet<?> packet, long writeId) {
        return offerPacket(packet, null, writeId, false);
    }

    public synchronized boolean offerPacket(Packet<?> packet, ChannelPromise promise, long writeId) {
        return offerPacket(packet, promise, writeId, true);
    }

    private boolean offerPacket(Packet<?> packet, ChannelPromise promise, long writeId,
                                boolean alreadyBridgeProcessed) {
        if (!this.blinking || this.blinkModule == BlinkModules.NONE
                || packet instanceof C00PacketKeepAlive || packet instanceof C01PacketChatMessage) {
            return false;
        } else if (this.blinkedPackets.isEmpty() && packet instanceof C0FPacketConfirmTransaction) {
            return false;
        } else {
            this.blinkedPackets.offer(new BlinkEntry(packet, promise, writeId, alreadyBridgeProcessed));
            return true;
        }
    }

    public boolean setBlinkState(boolean state, BlinkModules module) {
        Deque<BlinkEntry> pending = null;
        synchronized (this) {
            if (module == null || module == BlinkModules.NONE) {
                return false;
            }
            if (state) {
                if (blinking && blinkModule != module) {
                    return false;
                }
                this.blinkModule = module;
                this.blinking = true;
                return true;
            }
            if (blinkModule != module) {
                return false;
            }

            this.blinking = false;
            this.blinkModule = BlinkModules.NONE;
            if (this.blinkedPackets.isEmpty()) {
                return true;
            }
            pending = new ArrayDeque<BlinkEntry>();
            BlinkEntry packet;
            while ((packet = this.blinkedPackets.poll()) != null) {
                pending.offer(packet);
            }
        }

        if (Minecraft.getMinecraft().getNetHandler() == null) {
            failBufferedPackets(pending, new ClosedChannelException());
            return true;
        }
        for (BlinkEntry blinkedPacket : pending) {
            try {
                PacketUtil.sendPacketNoEvent(blinkedPacket.packet, blinkedPacket.promise,
                        blinkedPacket.writeId, blinkedPacket.alreadyBridgeProcessed);
            } catch (Throwable throwable) {
                failBufferedPacket(blinkedPacket, throwable);
            }
        }
        return true;
    }

    /**
     * Acquires the outbound blink channel only when no other module owns it.
     * BlockHit uses this instead of replacing an existing KillAura/fakelag lease.
     */
    public boolean tryAcquire(BlinkModules module) {
        if (module == null || module == BlinkModules.NONE) {
            return false;
        }
        return setBlinkState(true, module);
    }

    public synchronized boolean owns(BlinkModules module) {
        return blinking && blinkModule == module;
    }

    public boolean discard(BlinkModules module) {
        Deque<BlinkEntry> discarded;
        synchronized (this) {
            if (module == null || module == BlinkModules.NONE || blinkModule != module) {
                return false;
            }
            blinking = false;
            blinkModule = BlinkModules.NONE;
            discarded = new ArrayDeque<BlinkEntry>();
            BlinkEntry packet;
            while ((packet = blinkedPackets.poll()) != null) {
                discarded.offer(packet);
            }
        }
        failBufferedPackets(discarded, new ClosedChannelException());
        return true;
    }

    public synchronized BlinkModules getBlinkingModule() {
        return this.blinkModule;
    }

    public synchronized long countMovement() {
        return this.blinkedPackets.stream().filter(packet -> packet.packet instanceof C03PacketPlayer).count();
    }

    public synchronized boolean isBlinking() {
        return blinking;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof C00Handshake
                || event.getPacket() instanceof C00PacketLoginStart
                || event.getPacket() instanceof C00PacketServerQuery
                || event.getPacket() instanceof C01PacketPing
                || event.getPacket() instanceof C01PacketEncryptionResponse) {
            this.discard(this.getBlinkingModule());
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.POST) {
            if (mc.thePlayer != null && mc.thePlayer.isDead) {
                this.setBlinkState(false, this.getBlinkingModule());
            }
        }
    }

    private static void failBufferedPackets(Deque<BlinkEntry> packets, Throwable cause) {
        if (packets == null) {
            return;
        }
        BlinkEntry packet;
        while ((packet = packets.poll()) != null) {
            failBufferedPacket(packet, cause);
        }
    }

    private static void failBufferedPacket(BlinkEntry packet, Throwable cause) {
        if (packet != null && packet.promise != null) {
            packet.promise.tryFailure(cause);
        }
    }

    private static final class BlinkEntry {
        private final Packet<?> packet;
        private final ChannelPromise promise;
        private final long writeId;
        private final boolean alreadyBridgeProcessed;

        private BlinkEntry(Packet<?> packet, ChannelPromise promise, long writeId,
                           boolean alreadyBridgeProcessed) {
            this.packet = packet;
            this.promise = promise;
            this.writeId = writeId;
            this.alreadyBridgeProcessed = alreadyBridgeProcessed;
        }
    }
}
