package gq.yozakura.module.combat;

import com.google.common.base.CaseFormat;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.event.bridge.AttackEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.runtime.Module;
import gq.yozakura.value.properties.BooleanProperty;
import gq.yozakura.value.properties.FloatProperty;
import gq.yozakura.value.properties.IntProperty;
import gq.yozakura.value.properties.ModeProperty;
import gq.yozakura.value.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int MODE_ATTACK = 0;
    private static final int MODE_REDUCE = 1;

    public final ModeProperty mode =
            new ModeProperty("Mode", MODE_REDUCE, new String[]{"Attack", "Reduce"});
    public final PercentProperty horizontal = new PercentProperty("Horizontal", 60);
    public final PercentProperty vertical = new PercentProperty("Vertical", 100,
            () -> mode.getValue() == MODE_REDUCE);

    private final IntProperty attackTimeout =
            new IntProperty("Attack Timeout", 2, 1, 6, () -> mode.getValue() == MODE_ATTACK);
    private final FloatProperty attackRange =
            new FloatProperty("Attack Range", 3.0F, 1.0F, 6.0F, () -> mode.getValue() == MODE_ATTACK);
    private final BooleanProperty onlySprinting =
            new BooleanProperty("Only Sprinting", false, () -> mode.getValue() == MODE_ATTACK);
    private final BooleanProperty requireKillAura =
            new BooleanProperty("Require KillAura", false, () -> mode.getValue() == MODE_ATTACK);
    private final BooleanProperty playersOnly =
            new BooleanProperty("Players Only", true, () -> mode.getValue() == MODE_ATTACK);
    private final PercentProperty chance =
            new PercentProperty("Chance", 100, () -> mode.getValue() == MODE_REDUCE);

    public volatile boolean knockback;
    public static volatile boolean hasReceivedVelocity;

    private static Field s12MotionXField;
    private static Field s12MotionYField;
    private static Field s12MotionZField;

    private final Object attackStateLock = new Object();
    private final VelocityController controller = new VelocityController();
    private int activeMode = -1;
    private Entity pendingAttackTarget;

    public Velocity() {
        super("Velocity", false);
        setCategory(ModuleType.Combat);
        Chinese = "击退控制";
        Descript = "Real-attack slowdown or packet-based knockback reduction";
        About = Descript;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled() || !isInGame()) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (!(packet instanceof S12PacketEntityVelocity)) {
            return;
        }

        S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) packet;
        if (velocity.getEntityID() != mc.thePlayer.getEntityId()) {
            return;
        }

        int currentMode = mode.getValue();
        if (currentMode == MODE_ATTACK) {
            if (mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || mc.thePlayer.isOnLadder()) {
                clearAttackState();
                return;
            }
            synchronized (attackStateLock) {
                synchronizeModeLocked(currentMode);
                controller.armAttackWindow(attackTimeout.getValue());
                pendingAttackTarget = null;
                updateAttackFlagsLocked();
            }
            return;
        }

        boolean shouldScale;
        int horizontalPercent;
        int verticalPercent;
        synchronized (attackStateLock) {
            synchronizeModeLocked(currentMode);
            knockback = false;
            hasReceivedVelocity = false;
            shouldScale = controller.shouldApplyReduction(chance.getValue());
            horizontalPercent = horizontal.getValue();
            verticalPercent = vertical.getValue();
        }
        if (shouldScale) {
            scaleVelocityPacket(velocity, horizontalPercent, verticalPercent);
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onAttack(AttackEvent event) {
        if (!isEnabled() || mode.getValue() != MODE_ATTACK || event.isCancelled() || !isInGame()) {
            return;
        }
        acceptCustomAttack(event.getTarget());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onForgeAttack(AttackEntityEvent event) {
        if (event == null || event.isCanceled() || event.entityPlayer != mc.thePlayer) {
            return;
        }
        acceptExternalAttack(event.target);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE && event.getType() != EventType.POST) {
            return;
        }
        if (!isInGame() || mc.thePlayer.isDead || mc.currentScreen != null) {
            resetState();
            return;
        }
        synchronized (attackStateLock) {
            synchronizeModeLocked(mode.getValue());
            if (mode.getValue() != MODE_ATTACK) {
                knockback = false;
                hasReceivedVelocity = false;
                return;
            }

            if (event.getType() == EventType.PRE) {
                updateAttackFlagsLocked();
                return;
            }

            if (event.getType() == EventType.POST) {
                controller.tick();
                if (!controller.hasPendingAttackSlowdown()) {
                    pendingAttackTarget = null;
                }
                updateAttackFlagsLocked();
            }
        }
    }

    @Override
    public void onEnabled() {
        synchronized (attackStateLock) {
            clearAttackStateLocked();
            activeMode = mode.getValue();
        }
    }

    @Override
    public void onDisabled() {
        synchronized (attackStateLock) {
            clearAttackStateLocked();
            activeMode = -1;
        }
    }

    @Override
    public String[] getSuffix() {
        if (mode.getValue() == MODE_REDUCE) {
            return new String[]{horizontal.getValue() + "%", vertical.getValue() + "%"};
        }
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mode.getModeString())};
    }

    public static boolean applyAttackSlowdown(Entity target) {
        gq.yozakura.module.Module module = ModuleManager.getModule("Velocity");
        if (!(module instanceof Velocity)) {
            return false;
        }
        return ((Velocity) module).applyPendingAttackSlowdown(target);
    }

    private boolean applyPendingAttackSlowdown(Entity target) {
        if (!claimPendingAttackSlowdown(target)) {
            return false;
        }
        applyAttackSlowdownMotion();
        return true;
    }

    private boolean claimPendingAttackSlowdown(Entity target) {
        synchronized (attackStateLock) {
            if (!isEnabled() || mode.getValue() != MODE_ATTACK || target == null || target != pendingAttackTarget) {
                return false;
            }
            if (!controller.consumePendingAttackSlowdown()) {
                return false;
            }
            pendingAttackTarget = null;
            updateAttackFlagsLocked();
            return true;
        }
    }

    private void applyAttackSlowdownMotion() {
        mc.thePlayer.motionX = VelocityController.scale(mc.thePlayer.motionX, horizontal.getValue());
        mc.thePlayer.motionZ = VelocityController.scale(mc.thePlayer.motionZ, horizontal.getValue());
        mc.thePlayer.setSprinting(false);
    }

    private void acceptCustomAttack(Entity target) {
        if (!canAcceptAttack(target)) {
            return;
        }
        synchronized (attackStateLock) {
            synchronizeModeLocked(mode.getValue());
            if (controller.acceptRealAttack(true, mc.thePlayer.isSprinting(), Boolean.TRUE.equals(onlySprinting.getValue()))) {
                pendingAttackTarget = target;
                updateAttackFlagsLocked();
            }
        }
    }

    private void acceptExternalAttack(Entity target) {
        if (!canAcceptAttack(target)) {
            return;
        }
        boolean shouldApply = false;
        synchronized (attackStateLock) {
            synchronizeModeLocked(mode.getValue());
            if (target == pendingAttackTarget && controller.hasPendingAttackSlowdown()) {
                return;
            }
            if (controller.acceptRealAttack(true, mc.thePlayer.isSprinting(), Boolean.TRUE.equals(onlySprinting.getValue()))) {
                pendingAttackTarget = target;
                shouldApply = controller.consumePendingAttackSlowdown();
                pendingAttackTarget = null;
                updateAttackFlagsLocked();
            }
        }
        if (shouldApply) {
            applyAttackSlowdownMotion();
        }
    }

    private boolean canAcceptAttack(Entity target) {
        if (!isEnabled() || mode.getValue() != MODE_ATTACK || !isInGame() || !isValidAttackTarget(target)) {
            return false;
        }
        return !Boolean.TRUE.equals(requireKillAura.getValue()) || isKillAuraTarget(target);
    }

    private boolean isKillAuraTarget(Entity target) {
        gq.yozakura.module.Module module = ModuleManager.getModule("KillAura");
        if (!(module instanceof KillAura) || !module.getState()) {
            return false;
        }
        return ((KillAura) module).getTarget() == target;
    }

    private boolean isValidAttackTarget(Entity target) {
        if (!(target instanceof EntityLivingBase) || target == mc.thePlayer || target.isDead) {
            return false;
        }
        EntityLivingBase livingTarget = (EntityLivingBase) target;
        if (livingTarget.getHealth() <= 0.0F) {
            return false;
        }
        if (Boolean.TRUE.equals(playersOnly.getValue()) && !(target instanceof EntityPlayer)) {
            return false;
        }
        return mc.thePlayer.getDistanceToEntity(target) <= attackRange.getValue();
    }

    private void scaleVelocityPacket(S12PacketEntityVelocity packet, int horizontalPercent, int verticalPercent) {
        setS12Motion(packet,
                VelocityController.scalePacketMotion(packet.getMotionX(), horizontalPercent),
                VelocityController.scalePacketMotion(packet.getMotionY(), verticalPercent),
                VelocityController.scalePacketMotion(packet.getMotionZ(), horizontalPercent));
    }

    private static void setS12Motion(S12PacketEntityVelocity packet, int motionX, int motionY, int motionZ) {
        try {
            initializeS12MotionFields();
            s12MotionXField.setInt(packet, motionX);
            s12MotionYField.setInt(packet, motionY);
            s12MotionZField.setInt(packet, motionZ);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to scale S12PacketEntityVelocity", exception);
        }
    }

    private static synchronized void initializeS12MotionFields() throws NoSuchFieldException {
        if (s12MotionXField != null && s12MotionYField != null && s12MotionZField != null) {
            return;
        }
        s12MotionXField = findField("motionX", "field_149415_b", "b");
        s12MotionYField = findField("motionY", "field_149416_c", "c");
        s12MotionZField = findField("motionZ", "field_149414_d", "d");
    }

    private static Field findField(String... names) throws NoSuchFieldException {
        for (String name : names) {
            try {
                Field field = S12PacketEntityVelocity.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException("S12PacketEntityVelocity motion field");
    }

    private void clearAttackState() {
        synchronized (attackStateLock) {
            clearAttackStateLocked();
        }
    }

    private void clearAttackStateLocked() {
        controller.reset();
        pendingAttackTarget = null;
        knockback = false;
        hasReceivedVelocity = false;
    }

    private void synchronizeModeLocked(int currentMode) {
        if (activeMode == currentMode) {
            return;
        }
        clearAttackStateLocked();
        activeMode = currentMode;
    }

    private void updateAttackFlagsLocked() {
        boolean active = controller.isAttackWindowActive() || controller.hasPendingAttackSlowdown();
        hasReceivedVelocity = active;
        knockback = active;
    }

    private void resetState() {
        clearAttackState();
    }
}
