package gq.yozakura.manager;

import gq.yozakura.bridge.PacketWriteDisposition;
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
import gq.yozakura.module.player.BlinkSettings;
import gq.yozakura.runtime.YozakuraRuntime;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.event.bridge.TeleportBoundaryEvent;
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
    private boolean teleportConfirmationPending;
    private boolean slowReleasing;
    private int slowReleaseTicks;
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
        if (shouldBypassForTeleport(packet)) {
            return false;
        }
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

    private BlinkSettings getBlinkSettings() {
        if (YozakuraRuntime.moduleManager == null) {
            return null;
        }
        gq.yozakura.module.Module module = YozakuraRuntime.moduleManager.modules.get(BlinkSettings.class);
        return module instanceof BlinkSettings ? (BlinkSettings) module : null;
    }

    public boolean setBlinkState(boolean state, BlinkModules module) {
        Deque<BlinkEntry> pending = null;
        synchronized (this) {
            if (module == null || module == BlinkModules.NONE) {
                return false;
            }
            if (state) {
                if ((blinking || slowReleasing) && blinkModule != module) {
                    return false;
                }
                this.blinkModule = module;
                this.blinking = true;
                BlinkSettings settings = this.getBlinkSettings();
                if (settings != null && settings.slowRelease.getValue()
                        && settings.slowReleaseTime.getValue() == 0) {
                    this.slowReleasing = true;
                    this.slowReleaseTicks = 0;
                }
                return true;
            }
            if (blinkModule != module) {
                return false;
            }

            BlinkSettings settings = this.getBlinkSettings();
            if (settings != null && settings.slowRelease.getValue()
                    && settings.slowReleaseTime.getValue() == 1) {
                this.blinking = false;
                this.slowReleasing = true;
                this.slowReleaseTicks = 0;
                return true;
            }
            this.blinking = false;
            this.slowReleasing = false;
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
        return (blinking || slowReleasing) && blinkModule == module;
    }

    public boolean discard(BlinkModules module) {
        Deque<BlinkEntry> discarded;
        synchronized (this) {
            if (module == null || module == BlinkModules.NONE || blinkModule != module) {
                return false;
            }
            blinking = false;
            slowReleasing = false;
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

    private boolean forceStopAndFlush(BlinkModules module) {
        synchronized (this) {
            if (module == null || module == BlinkModules.NONE || blinkModule != module) {
                return false;
            }
            this.blinking = false;
            this.slowReleasing = false;
        }
        this.flushRemaining();
        return true;
    }

    private void processSlowRelease() {
        BlinkSettings settings = this.getBlinkSettings();
        if (settings == null || !settings.slowRelease.getValue()) {
            this.slowReleasing = false;
            this.flushRemaining();
            return;
        }
        this.slowReleaseTicks++;
        if (this.slowReleaseTicks < settings.slowReleaseDelay.getValue()) {
            return;
        }
        this.slowReleaseTicks = 0;
        int maxTotal = settings.maxPacketsPerTick.getValue();
        int maxC03 = settings.maxC03PacketsPerTick.getValue();
        int released = 0;
        int c03Released = 0;
        int size = this.blinkedPackets.size();
        for (int i = 0; i < size && released < maxTotal; i++) {
            BlinkEntry entry = this.blinkedPackets.poll();
            if (entry == null) {
                break;
            }
            if (entry.packet instanceof C03PacketPlayer) {
                if (c03Released >= maxC03) {
                    this.blinkedPackets.offer(entry);
                    continue;
                }
                c03Released++;
            }
            boolean wasBlinking = this.blinking;
            this.blinking = false;
            try {
                PacketUtil.sendPacketNoEvent(entry.packet, entry.promise, entry.writeId,
                        entry.alreadyBridgeProcessed);
            } catch (Throwable throwable) {
                failBufferedPacket(entry, throwable);
            } finally {
                this.blinking = wasBlinking;
            }
            released++;
        }
        if (this.blinkedPackets.isEmpty() && !this.blinking) {
            this.slowReleasing = false;
            this.blinkModule = BlinkModules.NONE;
        }
    }

    private void flushRemaining() {
        Deque<BlinkEntry> pending = new ArrayDeque<BlinkEntry>();
        boolean wasBlinking;
        synchronized (this) {
            wasBlinking = this.blinking;
            this.blinking = false;
            BlinkEntry entry;
            while ((entry = this.blinkedPackets.poll()) != null) {
                pending.offer(entry);
            }
        }
        for (BlinkEntry entry : pending) {
            try {
                PacketUtil.sendPacketNoEvent(entry.packet, entry.promise, entry.writeId,
                        entry.alreadyBridgeProcessed);
            } catch (Throwable throwable) {
                failBufferedPacket(entry, throwable);
            }
        }
        synchronized (this) {
            this.blinking = wasBlinking;
            if (!wasBlinking) {
                this.blinkModule = BlinkModules.NONE;
            }
        }
    }

    @EventTarget
    public void onTeleportBoundary(TeleportBoundaryEvent event) {
        discardBufferedPacketsForTeleport();
    }

    private void discardBufferedPacketsForTeleport() {
        Deque<BlinkEntry> discarded = new ArrayDeque<BlinkEntry>();
        synchronized (this) {
            BlinkEntry packet;
            while ((packet = blinkedPackets.poll()) != null) {
                discarded.offer(packet);
            }
            teleportConfirmationPending = true;
        }
        completeDroppedPackets(discarded);
    }

    private synchronized boolean shouldBypassForTeleport(Packet<?> packet) {
        if (!teleportConfirmationPending || !(packet instanceof C03PacketPlayer)) {
            return false;
        }
        C03PacketPlayer playerPacket = (C03PacketPlayer) packet;
        if (!playerPacket.isMoving()) {
            return false;
        }
        teleportConfirmationPending = false;
        return true;
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
                this.forceStopAndFlush(this.getBlinkingModule());
            }
            if (this.slowReleasing) {
                this.processSlowRelease();
            }
        }
    }

    private static void completeDroppedPackets(Deque<BlinkEntry> packets) {
        if (packets == null) {
            return;
        }
        BlinkEntry packet;
        while ((packet = packets.poll()) != null) {
            PacketWriteDisposition.completeDropped(packet.promise);
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
