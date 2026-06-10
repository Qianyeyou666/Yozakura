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
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public class BlockHit extends Module {
    public enum BlockMode {
        MANUAL,
        PREDICT,
        AUTO,
        LAG
    }

    private static final String HANDLER_NAME = "vapulite_blockhit_blink";
    private static final int MAX_QUEUED_PACKETS = 72;
    private static final long MIN_RETRIGGER_MS = 45L;
    private static BlockHit INSTANCE;

    private final Mode<BlockMode> mode = new Mode<BlockMode>("Mode", "Mode", BlockMode.values(), BlockMode.MANUAL);
    private final Numbers<Double> minDelay = new Numbers<Double>("Min Delay", "MinDelay", 70.0, 0.0, 240.0, 5.0);
    private final Numbers<Double> maxDelay = new Numbers<Double>("Max Delay", "MaxDelay", 90.0, 0.0, 320.0, 5.0);
    private final Numbers<Double> holdMs = new Numbers<Double>("Hold MS", "HoldMS", 110.0, 35.0, 280.0, 5.0);
    private final Numbers<Double> chance = new Numbers<Double>("Chance", "Chance", 100.0, 0.0, 100.0, 1.0);
    private final Option<Boolean> requireMouseDown = new Option<Boolean>("Require mouse down", "RequireMouseDown", true);
    private final Option<Boolean> onlySword = new Option<Boolean>("Only Sword", "OnlySword", true);
    private final Option<Boolean> onlyPlayers = new Option<Boolean>("Only Players", "OnlyPlayers", false);
    private final Queue<QueuedPacket> queuedPackets = new ConcurrentLinkedQueue<QueuedPacket>();

    private int phase;
    private boolean holdingUseKey;
    private boolean blockingApplied;
    private boolean serverBlocking;
    private boolean mouseTriggered;
    private Entity triggerTarget;
    private long blinkUntil;
    private long blockAt;
    private long releaseAt;
    private long lastTriggerAt;
    private long inputGraceUntil;
    private Channel channel;

    public BlockHit() {
        super("BlockHit", Keyboard.KEY_NONE, ModuleType.Combat, "Automatically blockhit");
        this.addValues(minDelay, maxDelay, mode, requireMouseDown, chance, holdMs, onlySword, onlyPlayers);
        Chinese = "格挡攻击";
        INSTANCE = this;
    }

    @Override
    public void enable() {
        phase = 0;
        blockAt = 0L;
        releaseAt = 0L;
        lastTriggerAt = 0L;
        blinkUntil = 0L;
        inputGraceUntil = 0L;
        mouseTriggered = false;
        serverBlocking = false;
        triggerTarget = null;
        queuedPackets.clear();
        releaseBlock();
    }

    @Override
    public void disable() {
        phase = 0;
        blockAt = 0L;
        releaseAt = 0L;
        inputGraceUntil = 0L;
        triggerTarget = null;
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
            start(entity, true);
        }
    }

    @SubscribeEvent
    public void onAttackEvent(AttackEntityEvent event) {
        if (!isInGame() || event.entityPlayer != mc.thePlayer || event.target == null) {
            return;
        }
        start(event.target, true);
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
        if (mode.getValue() == BlockMode.PREDICT && phase == 0 && mc.objectMouseOver != null
                && mc.objectMouseOver.entityHit instanceof EntityLivingBase
                && (!Boolean.TRUE.equals(requireMouseDown.getValue()) || mc.gameSettings.keyBindAttack.isKeyDown())) {
            start(mc.objectMouseOver.entityHit, false);
        }
        if (phase == 0) {
            return;
        }
        if (!canContinueBlock()) {
            releaseBlock();
            stopBlink();
            phase = 0;
            return;
        }

        long now = System.currentTimeMillis();
        if (phase == 1) {
            if (now < blockAt) {
                return;
            }
            applyBlock();
            phase = 2;
            releaseAt = now + holdDuration();
        } else {
            if (now < releaseAt) {
                return;
            }
            releaseBlock();
            phase = 0;
        }
    }

    public static void onAttack(Entity entity) {
        if (INSTANCE != null && INSTANCE.getState()) {
            INSTANCE.start(entity, false);
        }
    }

    private void start(Entity entity, boolean mouseTrigger) {
        BlockMode current = mode.getValue();
        if (current == BlockMode.MANUAL && !mouseTrigger && !mc.gameSettings.keyBindAttack.isKeyDown()) {
            return;
        }
        if (!canBlock(entity, mouseTrigger) || ThreadLocalRandom.current().nextDouble(100.0D) > chance.getValue()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastTriggerAt < MIN_RETRIGGER_MS) {
            return;
        }
        lastTriggerAt = now;
        long delay = delayFor(entity, current);
        phase = 1;
        blockAt = now + delay;
        releaseAt = 0L;
        triggerTarget = entity;
        mouseTriggered = mouseTrigger || mc.gameSettings.keyBindAttack.isKeyDown();
        inputGraceUntil = now + delay + holdDuration() + 140L;
    }

    private boolean canBlock(Entity entity) {
        return canBlock(entity, false);
    }

    private boolean canBlock(Entity entity, boolean mouseTrigger) {
        if (!isInGame()) {
            return false;
        }
        if (Boolean.TRUE.equals(requireMouseDown.getValue())
                && !mouseTrigger && !mc.gameSettings.keyBindAttack.isKeyDown()) {
            return false;
        }
        if (Boolean.TRUE.equals(onlyPlayers.getValue()) && entity != null && !(entity instanceof EntityPlayer)) {
            return false;
        }
        ItemStack stack = mc.thePlayer.getCurrentEquippedItem();
        return stack != null && (!Boolean.TRUE.equals(onlySword.getValue()) || stack.getItem() instanceof ItemSword);
    }

    private boolean canContinueBlock() {
        if (!isInGame()) {
            return false;
        }
        if (Boolean.TRUE.equals(onlyPlayers.getValue()) && triggerTarget != null
                && !(triggerTarget instanceof EntityPlayer)) {
            return false;
        }
        ItemStack stack = mc.thePlayer.getCurrentEquippedItem();
        if (stack == null || (Boolean.TRUE.equals(onlySword.getValue()) && !(stack.getItem() instanceof ItemSword))) {
            return false;
        }
        if (Boolean.TRUE.equals(requireMouseDown.getValue()) && !mouseTriggered
                && !mc.gameSettings.keyBindAttack.isKeyDown()
                && System.currentTimeMillis() > inputGraceUntil) {
            return false;
        }
        return true;
    }

    private void applyBlock() {
        ItemStack stack = mc.thePlayer.getCurrentEquippedItem();
        if (stack == null) {
            return;
        }
        BlockMode current = mode.getValue();
        if (current == BlockMode.LAG) {
            startBlink();
            sendBlockPacket(stack);
            mc.thePlayer.setItemInUse(stack, stack.getMaxItemUseDuration());
            blockingApplied = true;
            serverBlocking = true;
            return;
        }
        if (current == BlockMode.MANUAL || current == BlockMode.AUTO) {
            int key = mc.gameSettings.keyBindUseItem.getKeyCode();
            KeyBinding.setKeyBindState(key, true);
            KeyBinding.onTick(key);
            holdingUseKey = true;
        }
        sendBlockPacket(stack);
        mc.thePlayer.setItemInUse(stack, stack.getMaxItemUseDuration());
        blockingApplied = true;
        serverBlocking = true;
    }

    private void releaseBlock() {
        if (holdingUseKey) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            holdingUseKey = false;
        }
        if (blockingApplied && isInGame()) {
            if (serverBlocking) {
                sendReleasePacket();
            }
            mc.thePlayer.clearItemInUse();
        }
        blockingApplied = false;
        serverBlocking = false;
        mouseTriggered = false;
        inputGraceUntil = 0L;
        triggerTarget = null;
    }

    private void sendBlockPacket(ItemStack stack) {
        if (!isInGame() || stack == null) {
            return;
        }
        if (mc.thePlayer.sendQueue != null) {
            mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(stack));
        } else {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, stack);
        }
    }

    private void sendReleasePacket() {
        if (!isInGame() || mc.thePlayer.sendQueue == null) {
            return;
        }
        mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
    }

    private void updateBlink() {
        if (mode.getValue() != BlockMode.LAG) {
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
        blinkUntil = Math.max(blinkUntil, now + lagDuration());
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
                && mode.getValue() == BlockMode.LAG
                && blinkUntil > System.currentTimeMillis()
                && packet instanceof C03PacketPlayer;
    }

    private boolean queuePacket(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) {
        if (queuedPackets.size() >= MAX_QUEUED_PACKETS) {
            releaseQueuedPackets();
            return false;
        }
        long delay = lagDuration();
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

    private long delayFor(Entity entity, BlockMode current) {
        long delay = randomRange(minDelay.getValue().longValue(), maxDelay.getValue().longValue());
        if (current == BlockMode.PREDICT && entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            if (living.hurtTime <= 2 || living.hurtResistantTime <= 4) {
                delay = Math.max(0L, delay - 25L);
            }
        }
        if (current == BlockMode.LAG) {
            delay = Math.max(0L, delay - 15L);
        }
        return delay;
    }

    private long holdDuration() {
        return Math.max(35L, holdMs.getValue().longValue());
    }

    private long lagDuration() {
        return Math.max(35L, holdDuration() + randomRange(20L, 60L));
    }

    private static long randomRange(long first, long second) {
        long min = Math.max(0L, Math.min(first, second));
        long max = Math.max(min, Math.max(first, second));
        if (max <= min) {
            return min;
        }
        return ThreadLocalRandom.current().nextLong(max - min + 1L) + min;
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
