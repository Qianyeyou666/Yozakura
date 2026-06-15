package gq.yozakura.module.combat;

import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
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
    public enum LagMode {
        LATENCY,
        DYNAMIC,
        REPEL
    }

    private static final String HANDLER_NAME = "vapulite_fakelag";
    private static final int MIN_OFFSET_TICKS = 1;
    private static final int MAX_OFFSET_TICKS = 20;

    private final Mode<LagMode> mode = new Mode<LagMode>("Mode", "Mode", LagMode.values(), LagMode.DYNAMIC);
    private final Numbers<Double> transmissionOffset =
            new Numbers<Double>("Transmission offset", "TransmissionOffset", 5.0, 1.0, 20.0, 1.0);
    private final Option<Boolean> onlyMoving = new Option<Boolean>("Only Moving", "OnlyMoving", false);
    private final Option<Boolean> releaseOnAttack = new Option<Boolean>("Release On Attack", "ReleaseOnAttack", true);

    private final Queue<QueuedPacket> queuedPackets = new ConcurrentLinkedQueue<QueuedPacket>();
    private Channel channel;
    private long nextBurstAt;
    private long releaseAfterAttackAt;
    private volatile boolean lagAllowed;

    public FakeLag() {
        super("FakeLag", Keyboard.KEY_NONE, ModuleType.Combat, "Delay outgoing packets to simulate latency");
        this.addValues(transmissionOffset, mode, onlyMoving, releaseOnAttack);
        Chinese = "假延迟";
    }

    @Override
    public void enable() {
        queuedPackets.clear();
        nextBurstAt = System.currentTimeMillis() + offsetMillis();
        releaseAfterAttackAt = 0L;
        lagAllowed = true;
        injectHandler();
    }

    @Override
    public void disable() {
        lagAllowed = false;
        releaseAfterAttackAt = 0L;
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
        if (!lagAllowed) {
            releaseQueuedPackets();
            nextBurstAt = now + offsetMillis();
            return;
        }

        LagMode current = mode.getValue();
        if (current == LagMode.REPEL && now >= nextBurstAt) {
            releaseQueuedPackets();
            nextBurstAt = now + repelBurstInterval();
            return;
        }
        releaseDuePackets();
        if (releaseAfterAttackAt > 0L && now >= releaseAfterAttackAt) {
            releaseAfterAttackAt = 0L;
            releaseQueuedPackets();
            nextBurstAt = now + offsetMillis();
            return;
        }
        if (current != LagMode.REPEL) {
            nextBurstAt = now + offsetMillis();
        }
    }

    private boolean shouldLagNow() {
        LagMode current = mode.getValue();
        if (current == LagMode.LATENCY) {
            return true;
        }
        if (CombatUtil.hasCombatFocus()) {
            return true;
        }
        if (!Boolean.TRUE.equals(onlyMoving.getValue())) {
            return current == LagMode.DYNAMIC;
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
        String[] names = new String[]{"channel", "field_150746_k", "k"};
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
        if (!getState() || !lagAllowed || packet == null || transmissionOffset.getValue() <= 0.0D) {
            return false;
        }
        LagMode current = mode.getValue();
        if (packet instanceof C02PacketUseEntity
                && (current == LagMode.REPEL || Boolean.TRUE.equals(releaseOnAttack.getValue()))) {
            schedulePostAttackRelease();
            return false;
        }
        if (packet instanceof C03PacketPlayer) {
            return true;
        }
        if (current == LagMode.LATENCY) {
            return false;
        }
        if (packet instanceof C02PacketUseEntity || packet instanceof C0APacketAnimation
                || packet instanceof C0BPacketEntityAction) {
            return true;
        }
        if (current == LagMode.REPEL && (packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C09PacketHeldItemChange)) {
            return true;
        }
        return current == LagMode.DYNAMIC
                && (packet instanceof C07PacketPlayerDigging
                || packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C09PacketHeldItemChange
                || packet instanceof C0CPacketInput);
    }

    private boolean queuePacket(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) {
        int max = maxQueuedPackets();
        if (queuedPackets.size() >= max) {
            releaseQueuedPackets();
            return false;
        }
        long delay = queueDelay(packet);
        queuedPackets.offer(new QueuedPacket(ctx, packet, promise, System.currentTimeMillis() + delay));
        return true;
    }

    private long queueDelay(Object packet) {
        LagMode current = mode.getValue();
        long base = offsetMillis();
        if (current == LagMode.DYNAMIC) {
            if (packet instanceof C02PacketUseEntity || packet instanceof C0APacketAnimation) {
                base = Math.max(50L, base - 55L);
            } else if (CombatUtil.hasCombatFocus()) {
                base += 45L;
            } else if (isMoving()) {
                base += movementBoost();
            }
        } else if (current == LagMode.REPEL) {
            base = Math.min(900L, base + 75L + movementBoost());
        }
        long jitter = current == LagMode.LATENCY ? 0L : Math.max(10L, base / 6L);
        if (jitter <= 0L) {
            return base;
        }
        long offset = ThreadLocalRandom.current().nextLong(jitter * 2L + 1L) - jitter;
        return Math.max(45L, base + offset);
    }

    private long offsetMillis() {
        int ticks = Math.max(MIN_OFFSET_TICKS,
                Math.min(MAX_OFFSET_TICKS, transmissionOffset.getValue().intValue()));
        return ticks * 50L;
    }

    private long repelBurstInterval() {
        return Math.max(120L, Math.min(900L, offsetMillis() + 120L));
    }

    private int maxQueuedPackets() {
        int ticks = Math.max(MIN_OFFSET_TICKS,
                Math.min(MAX_OFFSET_TICKS, transmissionOffset.getValue().intValue()));
        return Math.max(24, Math.min(220, ticks * 14));
    }

    private long movementBoost() {
        if (mc.thePlayer == null) {
            return 0L;
        }
        double speed = Math.abs(mc.thePlayer.motionX) + Math.abs(mc.thePlayer.motionZ);
        return Math.max(0L, Math.min(120L, Math.round(speed * 380.0D)));
    }

    private boolean isMoving() {
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

    private void schedulePostAttackRelease() {
        long now = System.currentTimeMillis();
        releaseAfterAttackAt = Math.max(releaseAfterAttackAt, now + 45L);
        nextBurstAt = Math.max(nextBurstAt, releaseAfterAttackAt);
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
