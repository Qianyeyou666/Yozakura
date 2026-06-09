package gq.vapulite.Vapu.modules.combat;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.value.Mode;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0CPacketInput;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public class FakeLag extends Module {
    public enum LagScope {
        MOVEMENT,
        COMBAT,
        FULL
    }

    public enum ReleaseMode {
        DELAY,
        PULSE
    }

    private static final String HANDLER_NAME = "vapulite_fakelag";

    private final Mode<LagScope> scope = new Mode<LagScope>("Scope", "Scope", LagScope.values(), LagScope.MOVEMENT);
    private final Mode<ReleaseMode> releaseMode =
            new Mode<ReleaseMode>("Release", "Release", ReleaseMode.values(), ReleaseMode.DELAY);
    private final Numbers<Double> delayMs = new Numbers<Double>("Delay MS", "DelayMS", 160.0, 20.0, 1000.0, 10.0);
    private final Numbers<Double> jitterMs = new Numbers<Double>("Jitter MS", "JitterMS", 35.0, 0.0, 250.0, 5.0);
    private final Numbers<Double> pulseMs = new Numbers<Double>("Pulse MS", "PulseMS", 220.0, 50.0, 1400.0, 10.0);
    private final Numbers<Double> maxPackets = new Numbers<Double>("Max Packets", "MaxPackets", 96.0, 16.0, 260.0, 1.0);
    private final Option<Boolean> onlyMoving = new Option<Boolean>("Only Moving", "OnlyMoving", false);
    private final Option<Boolean> releaseOnAttack = new Option<Boolean>("Release On Attack", "ReleaseOnAttack", true);

    private final Queue<QueuedPacket> queuedPackets = new ConcurrentLinkedQueue<QueuedPacket>();
    private Channel channel;
    private long nextPulseAt;
    private volatile boolean lagAllowed;

    public FakeLag() {
        super("FakeLag", Keyboard.KEY_NONE, ModuleType.Combat, "Delay outgoing packets to simulate latency");
        this.addValues(scope, releaseMode, delayMs, jitterMs, pulseMs, maxPackets, onlyMoving, releaseOnAttack);
        Chinese = "假延迟";
    }

    @Override
    public void enable() {
        queuedPackets.clear();
        nextPulseAt = System.currentTimeMillis() + pulseMs.getValue().longValue();
        lagAllowed = true;
        injectHandler();
    }

    @Override
    public void disable() {
        lagAllowed = false;
        releaseQueuedPackets();
        removeHandler();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!isInGame() || mc.getNetHandler() == null) {
            lagAllowed = false;
            releaseQueuedPackets();
            removeHandler();
            return;
        }

        lagAllowed = shouldLagNow();
        injectHandler();
        long now = System.currentTimeMillis();
        if (releaseMode.getValue() == ReleaseMode.PULSE) {
            if (now >= nextPulseAt) {
                releaseQueuedPackets();
                nextPulseAt = now + Math.max(50L, pulseMs.getValue().longValue());
            }
        } else {
            releaseDuePackets();
            nextPulseAt = now + Math.max(50L, pulseMs.getValue().longValue());
        }
    }

    private boolean shouldLagNow() {
        if (!Boolean.TRUE.equals(onlyMoving.getValue())) {
            return true;
        }
        if (mc.thePlayer == null) {
            return false;
        }
        return mc.gameSettings.keyBindForward.isKeyDown()
                || mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown()
                || mc.gameSettings.keyBindRight.isKeyDown()
                || mc.gameSettings.keyBindJump.isKeyDown()
                || Math.abs(mc.thePlayer.motionX) + Math.abs(mc.thePlayer.motionZ) > 0.02D;
    }

    private void injectHandler() {
        if (!isInGame() || mc.getNetHandler() == null) {
            return;
        }
        try {
            NetworkManager manager = mc.getNetHandler().getNetworkManager();
            Channel current = getChannel(manager);
            if (current == null || !current.isOpen()) {
                return;
            }
            if (channel != null && channel != current) {
                releaseQueuedPackets();
                removeHandler();
            }
            if (current.pipeline().get(HANDLER_NAME) == null) {
                current.pipeline().addBefore("packet_handler", HANDLER_NAME, new FakeLagPacketHandler(this));
            }
            channel = current;
        } catch (Throwable ignored) {
            channel = null;
        }
    }

    private void removeHandler() {
        Channel current = channel;
        channel = null;
        if (current == null) {
            return;
        }
        try {
            if (current.isOpen() && current.pipeline().get(HANDLER_NAME) != null) {
                current.pipeline().remove(HANDLER_NAME);
            }
        } catch (Throwable ignored) {
        }
    }

    private Channel getChannel(NetworkManager manager) {
        String[] names = new String[]{"channel", "field_150746_k"};
        for (String name : names) {
            try {
                Field field = NetworkManager.class.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(manager);
                if (value instanceof Channel) {
                    return (Channel) value;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private boolean shouldQueuePacket(Object packet) {
        if (!getState() || !lagAllowed || packet == null || delayMs.getValue() <= 0.0D) {
            return false;
        }
        if (packet instanceof C02PacketUseEntity && Boolean.TRUE.equals(releaseOnAttack.getValue())) {
            releaseQueuedPackets();
            return false;
        }
        LagScope currentScope = scope.getValue();
        if (packet instanceof C03PacketPlayer) {
            return true;
        }
        if (currentScope == LagScope.MOVEMENT) {
            return false;
        }
        if (packet instanceof C02PacketUseEntity || packet instanceof C0APacketAnimation
                || packet instanceof C0BPacketEntityAction) {
            return true;
        }
        return currentScope == LagScope.FULL
                && (packet instanceof C07PacketPlayerDigging
                || packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C09PacketHeldItemChange
                || packet instanceof C0CPacketInput);
    }

    private boolean queuePacket(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) {
        int max = Math.max(1, maxPackets.getValue().intValue());
        if (queuedPackets.size() >= max) {
            releaseQueuedPackets();
            return false;
        }
        long delay = releaseMode.getValue() == ReleaseMode.PULSE ? Math.max(50L, pulseMs.getValue().longValue()) : randomDelay();
        queuedPackets.offer(new QueuedPacket(ctx, packet, promise, System.currentTimeMillis() + delay));
        return true;
    }

    private long randomDelay() {
        long base = Math.max(20L, delayMs.getValue().longValue());
        long jitter = Math.max(0L, jitterMs.getValue().longValue());
        if (jitter <= 0L) {
            return base;
        }
        long offset = ThreadLocalRandom.current().nextLong(jitter * 2L + 1L) - jitter;
        return Math.max(20L, base + offset);
    }

    private void releaseDuePackets() {
        long now = System.currentTimeMillis();
        while (true) {
            QueuedPacket queued = queuedPackets.peek();
            if (queued == null || queued.releaseAt > now) {
                break;
            }
            queuedPackets.poll();
            writePacket(queued);
        }
    }

    private void releaseQueuedPackets() {
        QueuedPacket queued;
        while ((queued = queuedPackets.poll()) != null) {
            writePacket(queued);
        }
    }

    private void writePacket(final QueuedPacket queued) {
        if (queued.ctx == null || queued.packet == null || queued.promise == null) {
            return;
        }
        queued.ctx.executor().execute(new Runnable() {
            @Override
            public void run() {
                queued.ctx.writeAndFlush(queued.packet, queued.promise);
            }
        });
    }

    private static final class QueuedPacket {
        final ChannelHandlerContext ctx;
        final Object packet;
        final ChannelPromise promise;
        final long releaseAt;

        QueuedPacket(ChannelHandlerContext ctx, Object packet, ChannelPromise promise, long releaseAt) {
            this.ctx = ctx;
            this.packet = packet;
            this.promise = promise;
            this.releaseAt = releaseAt;
        }
    }

    private static final class FakeLagPacketHandler extends ChannelDuplexHandler {
        private final FakeLag module;

        FakeLagPacketHandler(FakeLag module) {
            this.module = module;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (module.shouldQueuePacket(msg) && module.queuePacket(ctx, msg, promise)) {
                return;
            }
            super.write(ctx, msg, promise);
        }
    }
}
