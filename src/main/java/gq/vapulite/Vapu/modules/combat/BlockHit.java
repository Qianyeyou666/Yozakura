package gq.vapulite.Vapu.modules.combat;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.value.Mode;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.ThreadLocalRandom;

public class BlockHit extends Module {
    public enum BlockMode {
        MANUAL,
        PREDICT,
        AUTO,
        LAG
    }

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

    private int phase;
    private boolean holdingUseKey;
    private boolean blockingApplied;
    private boolean mouseTriggered;
    private Entity triggerTarget;
    private long blockAt;
    private long releaseAt;
    private long lastTriggerAt;
    private long inputGraceUntil;

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
        inputGraceUntil = 0L;
        mouseTriggered = false;
        triggerTarget = null;
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
            phase = 0;
            return;
        }
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

    public static boolean isBlockingActive() {
        return INSTANCE != null && INSTANCE.getState() && INSTANCE.blockingApplied;
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
        BlockMode current = mode.getValue();
        boolean needsManualInput = current == BlockMode.MANUAL || current == BlockMode.PREDICT;
        if (needsManualInput && Boolean.TRUE.equals(requireMouseDown.getValue())
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
        BlockMode current = mode.getValue();
        boolean needsManualInput = current == BlockMode.MANUAL || current == BlockMode.PREDICT;
        if (needsManualInput && Boolean.TRUE.equals(requireMouseDown.getValue()) && !mouseTriggered
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
        if (current == BlockMode.MANUAL || current == BlockMode.AUTO || current == BlockMode.LAG) {
            int key = mc.gameSettings.keyBindUseItem.getKeyCode();
            KeyBinding.setKeyBindState(key, true);
            KeyBinding.onTick(key);
            holdingUseKey = true;
        }
        mc.thePlayer.setItemInUse(stack, stack.getMaxItemUseDuration());
        blockingApplied = true;
    }

    private void releaseBlock() {
        if (holdingUseKey) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            holdingUseKey = false;
        }
        if (blockingApplied && isInGame()) {
            mc.thePlayer.clearItemInUse();
        }
        blockingApplied = false;
        mouseTriggered = false;
        inputGraceUntil = 0L;
        triggerTarget = null;
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

    private static long randomRange(long first, long second) {
        long min = Math.max(0L, Math.min(first, second));
        long max = Math.max(min, Math.max(first, second));
        if (max <= min) {
            return min;
        }
        return ThreadLocalRandom.current().nextLong(max - min + 1L) + min;
    }

}
