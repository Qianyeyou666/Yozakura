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
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public class BlockHit extends Module {
    public enum BlockMode {
        KEY,
        PACKET,
        HYBRID,
        BLINK
    }

    private static final String HANDLER_NAME = "vapulite_blockhit_blink";
    private static final int MAX_QUEUED_PACKETS = 72;
    private static BlockHit INSTANCE;

    private final Mode<BlockMode> mode = new Mode<BlockMode>("Mode", "Mode", BlockMode.values(), BlockMode.HYBRID);
    private final Numbers<Double> chance = new Numbers<Double>("Chance", "Chance", 100.0, 0.0, 100.0, 1.0);
    private final Numbers<Double> waitTicks = new Numbers<Double>("Wait Ticks", "WaitTicks", 1.0, 0.0, 5.0, 1.0);
    private final Numbers<Double> holdTicks = new Numbers<Double>("Hold Ticks", "HoldTicks", 2.0, 1.0, 8.0, 1.0);
    private final Numbers<Double> jitterTicks = new Numbers<Double>("Jitter", "Jitter", 1.0, 0.0, 4.0, 1.0);
    private final Numbers<Double> blinkMs = new Numbers<Double>("Blink MS", "BlinkMS", 120.0, 20.0, 400.0, 10.0);
    private final Option<Boolean> onlySword = new Option<Boolean>("Only Sword", "OnlySword", true);
    private final Option<Boolean> onlyPlayers = new Option<Boolean>("Only Players", "OnlyPlayers", false);
    private final Option<Boolean> requireAttack = new Option<Boolean>("Attack Held", "AttackHeld", true);
    private final Queue<QueuedPacket> queuedPackets = new ConcurrentLinkedQueue<QueuedPacket>();

    private int phase;
    private int ticksRemaining;
    private boolean holdingUseKey;
    private boolean blockingApplied;
    private long blinkUntil;
    private Channel channel;

    public BlockHit() {
        super("BlockHit", Keyboard.KEY_NONE, ModuleType.Combat, "Block briefly after landing attacks");
        this.addValues(mode, chance, waitTicks, holdTicks, jitterTicks, blinkMs, onlySword, onlyPlayers, requireAttack);
        Chinese = "格挡攻击";
        INSTANCE = this;
    }

    @Override
    public void enable() {
        phase = 0;
        ticksRemaining = 0;
        blinkUntil = 0L;
        queuedPackets.clear();
        releaseBlock();
    }

    @Override
    public void disable() {
        phase = 0;
        ticksRemaining = 0;
        releaseBlock();
        stopBlink();
        removeHandler();
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.button != 0 || !event.buttonstate || !isInGame() || mc.objectMouseOver == null) {
            return;
        }
        Entity entity = mc.objectMouseOver.entityHit;
        if (entity != null) {
            start(entity);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isInGame()) {
            releaseBlock();
            stopBlink();
            removeHandler();
            phase = 0;
            return;
        }
        updateBlink();
        if (phase == 0) {
            return;
        }
        if (!canBlock(null)) {
            releaseBlock();
            stopBlink();
            phase = 0;
            return;
        }

        ticksRemaining--;
        if (ticksRemaining > 0) {
            return;
        }
        if (phase == 1) {
            applyBlock();
            phase = 2;
            ticksRemaining = randomTicks(holdTicks.getValue().intValue(), jitterTicks.getValue().intValue());
        } else {
            releaseBlock();
            phase = 0;
        }
    }

    public static void onAttack(Entity entity) {
        if (INSTANCE != null && INSTANCE.getState()) {
            INSTANCE.start(entity);
        }
    }

    private void start(Entity entity) {
        if (!canBlock(entity) || ThreadLocalRandom.current().nextDouble(100.0D) > chance.getValue()) {
            return;
        }
        phase = 1;
        ticksRemaining = Math.max(1, randomTicks(waitTicks.getValue().intValue(), jitterTicks.getValue().intValue()));
    }

    private boolean canBlock(Entity entity) {
        if (!isInGame()) {
            return false;
        }
        if (Boolean.TRUE.equals(requireAttack.getValue()) && !mc.gameSettings.keyBindAttack.isKeyDown()) {
            return false;
        }
        if (Boolean.TRUE.equals(onlyPlayers.getValue()) && entity != null && !(entity instanceof EntityPlayer)) {
            return false;
        }
        ItemStack stack = mc.thePlayer.getCurrentEquippedItem();
        return stack != null && (!Boolean.TRUE.equals(onlySword.getValue()) || stack.getItem() instanceof ItemSword);
    }

    private void applyBlock() {
        ItemStack stack = mc.thePlayer.getCurrentEquippedItem();
        if (stack == null) {
            return;
        }
        BlockMode current = mode.getValue();
        if (current == BlockMode.BLINK) {
            startBlink();
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, stack);
            mc.thePlayer.setItemInUse(stack, stack.getMaxItemUseDuration());
            blockingApplied = true;
            return;
        }
        if (current == BlockMode.KEY || current == BlockMode.HYBRID) {
            int key = mc.gameSettings.keyBindUseItem.getKeyCode();
            KeyBinding.setKeyBindState(key, true);
            KeyBinding.onTick(key);
            holdingUseKey = true;
        }
        if (current == BlockMode.PACKET || current == BlockMode.HYBRID) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, stack);
            mc.thePlayer.setItemInUse(stack, stack.getMaxItemUseDuration());
        }
        blockingApplied = true;
    }

    private void releaseBlock() {
        if (holdingUseKey) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            holdingUseKey = false;
        }
        if (blockingApplied && isInGame()) {
            mc.thePlayer.stopUsingItem();
        }
        blockingApplied = false;
    }

    private void updateBlink() {
        if (mode.getValue() != BlockMode.BLINK) {
            stopBlink();
            removeHandler();
            return;
        }
        injectHandler();
        releaseDuePackets();
        if (blinkUntil > 0L && System.currentTimeMillis() >= blinkUntil) {
            blinkUntil = 0L;
            releaseQueuedPackets();
        }
    }

    private void startBlink() {
        injectHandler();
        long now = System.currentTimeMillis();
        blinkUntil = Math.max(blinkUntil, now + Math.max(20L, blinkMs.getValue().longValue()));
    }

    private void stopBlink() {
        blinkUntil = 0L;
        releaseQueuedPackets();
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
            if (current.pipeline().get(HANDLER_NAME) != null) {
                this.channel = current;
                return;
            }
            current.pipeline().addBefore("packet_handler", HANDLER_NAME, new BlinkPacketHandler(this));
            this.channel = current;
        } catch (Throwable ignored) {
            this.channel = null;
        }
    }

    private void removeHandler() {
        Channel current = this.channel;
        this.channel = null;
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

    private boolean shouldBlinkPacket(Object packet) {
        return getState()
                && mode.getValue() == BlockMode.BLINK
                && blinkUntil > System.currentTimeMillis()
                && packet instanceof C03PacketPlayer;
    }

    private boolean queuePacket(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) {
        if (queuedPackets.size() >= MAX_QUEUED_PACKETS) {
            releaseQueuedPackets();
            return false;
        }
        long delay = Math.max(20L, blinkMs.getValue().longValue());
        queuedPackets.offer(new QueuedPacket(ctx, packet, promise, System.currentTimeMillis() + delay));
        return true;
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

    private static int randomTicks(int base, int jitter) {
        int safeBase = Math.max(0, base);
        int safeJitter = Math.max(0, jitter);
        return safeBase + (safeJitter == 0 ? 0 : ThreadLocalRandom.current().nextInt(safeJitter + 1));
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

    private static final class BlinkPacketHandler extends ChannelDuplexHandler {
        private final BlockHit module;

        BlinkPacketHandler(BlockHit module) {
            this.module = module;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (module.shouldBlinkPacket(msg) && module.queuePacket(ctx, msg, promise)) {
                return;
            }
            super.write(ctx, msg, promise);
        }
    }
}
