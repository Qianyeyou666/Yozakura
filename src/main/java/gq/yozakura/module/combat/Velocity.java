package gq.yozakura.module.combat;

import com.google.common.base.CaseFormat;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bridge.AttackEvent;
import gq.yozakura.event.bridge.LivingUpdateEvent;
import gq.yozakura.event.bridge.MoveInputEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.StrafeEvent;
import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.manager.RotationState;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.movement.LongJump;
import gq.yozakura.module.runtime.Module;
import gq.yozakura.module.world.Scaffold;
import gq.yozakura.runtime.YozakuraRuntime;
import gq.yozakura.util.minecraft.Helper;
import gq.yozakura.util.module.KeyBindUtil;
import gq.yozakura.util.module.MoveUtil;
import gq.yozakura.util.module.PacketUtil;
import gq.yozakura.util.module.RotationUtil;
import gq.yozakura.value.properties.BooleanProperty;
import gq.yozakura.value.properties.IntProperty;
import gq.yozakura.value.properties.ModeProperty;
import gq.yozakura.value.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.util.List;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Vanilla", "Prediction", "Reduce", "ReduceAttack"});
    public final BooleanProperty reduce = new BooleanProperty("Reduce", true, () -> mode.getValue() == 1);
    private final BooleanProperty reduceWhenCanAttack = new BooleanProperty("Reduce When Can Attack", true, () -> mode.getValue() != 0);
    private final BooleanProperty onlySprinting = new BooleanProperty("Only Sprinting", true, () -> mode.getValue() != 0);
    public final IntProperty attackTimes = new IntProperty("Attack Times", 1, 1, 5,
            () -> mode.getValue() == 1 && reduce.getValue());

    // ReduceAttack mode settings
    private final IntProperty reduceAttackCount = new IntProperty("ReduceAttack Count", 3, 0, 20, () -> mode.getValue() == 3);
    private final PercentProperty reduceAttackHorizontal = new PercentProperty("ReduceAttack Horizontal", 60, () -> mode.getValue() == 3);
    private final PercentProperty reduceAttackVertical = new PercentProperty("ReduceAttack Vertical", 100, () -> mode.getValue() == 3);

    public final BooleanProperty jump = new BooleanProperty("Jump", true, () -> mode.getValue() == 1);
    public final BooleanProperty delay = new BooleanProperty("Delay", false, () -> mode.getValue() == 1);
    public final BooleanProperty airBuffer = new BooleanProperty("Delay Till On Ground", true,
            () -> mode.getValue() == 1 && delay.getValue());
    public final IntProperty delayTicks = new IntProperty("Delay Ticks", 1, 1, 5,
            () -> mode.getValue() == 1 && delay.getValue() && !airBuffer.getValue());
    public final BooleanProperty groundDelay = new BooleanProperty("Ground Delay", false,
            () -> mode.getValue() == 1 && delay.getValue() && !airBuffer.getValue());
    public final BooleanProperty rotate = new BooleanProperty("Rotate", false, () -> mode.getValue() == 1);
    public final IntProperty rotateTick = new IntProperty("Rotate Ticks", 3, 1, 12,
            () -> mode.getValue() == 1 && rotate.getValue());
    public final BooleanProperty autoMove = new BooleanProperty("Auto Move", false,
            () -> mode.getValue() == 1 && rotate.getValue());
    public final PercentProperty chance = new PercentProperty("Chance", 100, () -> mode.getValue() == 0);
    public final PercentProperty horizontal = new PercentProperty("Horizontal", 100, () -> mode.getValue() == 0);
    public final PercentProperty vertical = new PercentProperty("Vertical", 100, () -> mode.getValue() == 0);
    public final PercentProperty explosionHorizontal = new PercentProperty("Explosions Horizontal", 100,
            () -> mode.getValue() == 0);
    public final PercentProperty explosionVertical = new PercentProperty("Explosions Vertical", 100,
            () -> mode.getValue() == 0);
    public final BooleanProperty fakeCheck = new BooleanProperty("Fake Check", true);
    public final BooleanProperty debug = new BooleanProperty("Debug", false);

    public boolean knockback;
    public static boolean hasReceivedVelocity;

    private int chanceCounter;
    private int rotatoTickCounter;
    private boolean allowNext = true;
    private boolean delayFlag;
    private int delayCounter;
    private Packet<?> delayedVelocityPacket;
    private boolean isFallDamage;
    private boolean jumpFlag;
    private int ticksSinceVelocity = -1;
    private boolean handleReset;
    private double knockbackX;
    private double knockbackZ;
    private float[] targetRotation;
    private int reduceTick = -1;
    private int attackCounter = 0;  // ReduceAttack 攻击计数器

    private static Field s12MotionXField;
    private static Field s12MotionYField;
    private static Field s12MotionZField;
    private static Field s27MotionXField;
    private static Field s27MotionYField;
    private static Field s27MotionZField;
    private static Field isInWebField;

    public Velocity() {
        super("Velocity", false);
        setCategory(ModuleType.Combat);
        Chinese = "反击退";
        Descript = "Reduce knockback";
        About = Descript;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled() || !isInGame()) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (packet instanceof S19PacketEntityStatus) {
            handleEntityStatus((S19PacketEntityStatus) packet);
            return;
        }
        if (packet instanceof S12PacketEntityVelocity) {
            handleVelocityPacket(event, (S12PacketEntityVelocity) packet);
            return;
        }
        if (packet instanceof S27PacketExplosion) {
            handleExplosionPacket((S27PacketExplosion) packet);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE || !isInGame()) {
            return;
        }
        if (ticksSinceVelocity >= 0) {
            ticksSinceVelocity++;
        }
        if (ticksSinceVelocity >= 10) {
            ticksSinceVelocity = -1;
        }
        if (delayFlag) {
            delayCounter++;
        }
        if (jump.getValue() && mode.getValue() == 1) {
            handleJumpReset();
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || !isInGame()) {
            return;
        }
        if (mode.getValue() == 1) {
            if (event.getType() == EventType.PRE) {
                handleRotation(event);
                if (reduce.getValue()) {
                    handleReduceAttack(event, 3.0D, true);
                }
            } else if (event.getType() == EventType.POST) {
                advanceRotation();
                releaseDelayedVelocityIfReady();
            }
            return;
        }
        if (mode.getValue() == 2 && event.getType() == EventType.PRE) {
            handleReduceAttack(event, 3.2D, false);
        }
        // ReduceAttack mode (mode 3)
        if (mode.getValue() == 3 && event.getType() == EventType.PRE) {
            handleReduceAttack(event, 3.0D, true);
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!isEnabled() || !isInGame()) {
            return;
        }
        // ReduceAttack: 记录攻击次数
        if (mode.getValue() == 3) {
            attackCounter++;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!isEnabled() || !jumpFlag || !isInGame()) {
            return;
        }
        if (mc.thePlayer.onGround
                && MoveUtil.isForwardPressed()
                && !mc.thePlayer.isPotionActive(Potion.jump)
                && !isInLiquidOrWeb()) {
            mc.thePlayer.movementInput.jump = true;
        }
        jumpFlag = false;
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (!isEnabled() || !isInGame()) {
            return;
        }
        if (handleReset) {
            mc.thePlayer.movementInput.moveForward = 1.0F;
        }
        if (rotatoTickCounter > 0 && rotatoTickCounter <= rotateTick.getValue()) {
            if (autoMove.getValue()) {
                mc.thePlayer.movementInput.moveForward = 1.0F;
            }
            if (targetRotation != null
                    && RotationState.isActived()
                    && RotationState.getPriority() == 2.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!isEnabled() || !isInGame() || mode.getValue() != 2) {
            return;
        }
        boolean shouldJump = mc.thePlayer.hurtTime == 9 && mc.thePlayer.isSprinting() && !isFallDamage;
        if (shouldJump
                && mc.thePlayer.onGround
                && !mc.gameSettings.keyBindJump.isKeyDown()
                && !isInLiquidOrWeb()) {
            mc.thePlayer.jump();
        }
    }

    @Override
    public void onEnabled() {
        resetState();
    }

    @Override
    public void onDisabled() {
        releaseJumpKey();
        resetState();
    }

    @Override
    public String[] getSuffix() {
        if (mode.getValue() == 0) {
            return new String[]{
                    horizontal.getValue() + "%",
                    vertical.getValue() + "%"
            };
        }
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mode.getModeString())};
    }

    private void handleEntityStatus(S19PacketEntityStatus packet) {
        Entity entity = packet.getEntity(mc.theWorld);
        if (entity != null && entity.equals(mc.thePlayer) && packet.getOpCode() == 2) {
            allowNext = false;
        }
    }

    private void handleVelocityPacket(PacketEvent event, S12PacketEntityVelocity packet) {
        if (packet.getEntityID() != mc.thePlayer.getEntityId()) {
            return;
        }
        knockback = true;
        updateFallDamageFlag(packet);

        boolean handleVelocity = consumeVelocityAllowance();

        // ReduceAttack mode: 只在攻击次数达标时处理击退
        if (mode.getValue() == 3) {
            if (attackCounter < reduceAttackCount.getValue()) {
                attackCounter = 0;
                return;
            }
            attackCounter = 0;
            scaleVelocityPacket(packet, reduceAttackHorizontal.getValue(), reduceAttackVertical.getValue());
            return;
        }

        if (mode.getValue() != 1 || !delay.getValue()) {
            ticksSinceVelocity = 0;
            hasReceivedVelocity = true;
        }
        if (!handleVelocity) {
            return;
        }
        if (mode.getValue() == 1) {
            if (shouldDelayVelocity()) {
                delayedVelocityPacket = packet;
                delayFlag = true;
                delayCounter = 0;
                event.setCancelled(true);
                dbg("Velocity Delay/Buffer Active");
                return;
            }
            markVelocityForPrediction(packet);
            return;
        }
        if (mode.getValue() == 0 && shouldApplyChance()) {
            scaleVelocityPacket(packet, horizontal.getValue(), vertical.getValue());
        }
    }

    private void handleExplosionPacket(S27PacketExplosion packet) {
        if (mode.getValue() != 0) {
            return;
        }
        if (packet.func_149149_c() == 0.0F && packet.func_149144_d() == 0.0F && packet.func_149147_e() == 0.0F) {
            return;
        }
        if (!consumeVelocityAllowance()) {
            return;
        }
        scaleExplosionPacket(packet, explosionHorizontal.getValue(), explosionVertical.getValue());
    }

    private boolean consumeVelocityAllowance() {
        if (!fakeCheck.getValue()) {
            return true;
        }
        if (!allowNext) {
            allowNext = true;
            return true;
        }
        return false;
    }

    private boolean shouldApplyChance() {
        chanceCounter = chanceCounter % 100 + chance.getValue();
        return chanceCounter >= 100;
    }

    private void scaleVelocityPacket(S12PacketEntityVelocity packet, int horizontalPercent, int verticalPercent) {
        int motionX = horizontalPercent > 0
                ? scalePacketMotion(packet.getMotionX(), horizontalPercent)
                : toPacketMotion(mc.thePlayer.motionX);
        int motionZ = horizontalPercent > 0
                ? scalePacketMotion(packet.getMotionZ(), horizontalPercent)
                : toPacketMotion(mc.thePlayer.motionZ);
        int motionY = verticalPercent > 0
                ? scalePacketMotion(packet.getMotionY(), verticalPercent)
                : toPacketMotion(mc.thePlayer.motionY);
        setS12Motion(packet, motionX, motionY, motionZ);
    }

    private void scaleExplosionPacket(S27PacketExplosion packet, int horizontalPercent, int verticalPercent) {
        float motionX = horizontalPercent > 0 ? packet.func_149149_c() * horizontalPercent / 100.0F : 0.0F;
        float motionZ = horizontalPercent > 0 ? packet.func_149147_e() * horizontalPercent / 100.0F : 0.0F;
        float motionY = verticalPercent > 0 ? packet.func_149144_d() * verticalPercent / 100.0F : 0.0F;
        setS27Motion(packet, motionX, motionY, motionZ);
    }

    private int scalePacketMotion(int motion, int percent) {
        return (int) Math.round(motion * (percent / 100.0D));
    }

    private int toPacketMotion(double motion) {
        return (int) (MathHelper.clamp_double(motion, -3.9D, 3.9D) * 8000.0D);
    }

    private void markVelocityForPrediction(S12PacketEntityVelocity packet) {
        double motionX = packet.getMotionX() / 8000.0D;
        double motionY = packet.getMotionY() / 8000.0D;
        double motionZ = packet.getMotionZ() / 8000.0D;
        if (mode.getValue() == 1 && rotate.getValue() && motionY > 0.0D) {
            knockbackX = motionX;
            knockbackZ = motionZ;
            if (Math.abs(knockbackX) > 0.01D || Math.abs(knockbackZ) > 0.01D) {
                rotatoTickCounter = 1;
            }
        }
    }

    private boolean shouldDelayVelocity() {
        if (delayFlag || isInLiquidOrWeb()) {
            return false;
        }
        LongJump longJump = (LongJump) YozakuraRuntime.moduleManager.modules.get(LongJump.class);
        if (longJump.isEnabled()) {
            return false;
        }
        return airBuffer.getValue() && !mc.thePlayer.onGround
                || !mc.thePlayer.onGround
                || groundDelay.getValue() && !airBuffer.getValue();
    }

    private void releaseDelayedVelocityIfReady() {
        if (!delayFlag || delayedVelocityPacket == null) {
            return;
        }
        boolean ready = isInLiquidOrWeb()
                || delayCounter >= delayTicks.getValue() && !airBuffer.getValue()
                || mc.thePlayer.onGround && !groundDelay.getValue() && !airBuffer.getValue()
                || airBuffer.getValue() && mc.thePlayer.onGround;
        if (!ready) {
            return;
        }
        Packet<?> packet = delayedVelocityPacket;
        delayedVelocityPacket = null;
        delayFlag = false;
        delayCounter = 0;
        if (packet instanceof S12PacketEntityVelocity) {
            markVelocityForPrediction((S12PacketEntityVelocity) packet);
        }
        ticksSinceVelocity = 0;
        hasReceivedVelocity = true;
        dbg("Velocity Delay/Buffer Released");
        PacketUtil.receivePacketNoEvent(packet);
    }

    private void handleJumpReset() {
        Scaffold scaffold = (Scaffold) YozakuraRuntime.moduleManager.modules.get(Scaffold.class);
        if (mc.thePlayer == null || mc.currentScreen instanceof GuiInventory || scaffold.isEnabled()) {
            return;
        }
        if (ticksSinceVelocity >= 0) {
            handleReset = true;
            if (ticksSinceVelocity <= 2 && mc.thePlayer.onGround) {
                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
            }
        }
        if (ticksSinceVelocity >= 4 && ticksSinceVelocity <= 9) {
            releaseJumpKey();
            handleReset = false;
        }
    }

    private void handleRotation(UpdateEvent event) {
        int maxTick = rotateTick.getValue();
        if (rotatoTickCounter <= 0 || rotatoTickCounter > maxTick) {
            return;
        }
        if (rotatoTickCounter == 1) {
            targetRotation = RotationUtil.getRotationsTo(-knockbackX, 0.0D, -knockbackZ,
                    event.getYaw(), event.getPitch());
        }
        if (targetRotation != null) {
            event.setRotation(targetRotation[0], targetRotation[1], 2);
            event.setPervRotation(targetRotation[0], 2);
        }
    }

    private void advanceRotation() {
        int maxTick = rotateTick.getValue();
        if (rotatoTickCounter <= 0 || rotatoTickCounter > maxTick) {
            return;
        }
        rotatoTickCounter++;
        if (rotatoTickCounter > maxTick) {
            rotatoTickCounter = 0;
            targetRotation = null;
            knockbackX = 0.0D;
            knockbackZ = 0.0D;
        }
    }

    private void handleReduceAttack(UpdateEvent event, double range, boolean limited) {
        if (!hasReceivedVelocity) {
            return;
        }
        if (limited && reduceTick >= attackTimes.getValue()) {
            reduceTick = 0;
            hasReceivedVelocity = false;
        }

        Entity target = rayCastEntity(event.getYaw(), event.getPitch(), range);
        if (target instanceof EntityPlayer && target != mc.thePlayer) {
            if (mc.thePlayer.isSprinting() || !onlySprinting.getValue()) {
                KillAura killAura = (KillAura) YozakuraRuntime.moduleManager.modules.get(KillAura.class);
                Entity auraTarget = killAura.getTarget();
                if (auraTarget != null) {
                    if (canReduceWithKillAura(killAura)) {
                        performVelocityAttack(auraTarget);
                    }
                } else {
                    performVelocityAttack(target);
                }
            }
        }

        if (limited) {
            reduceTick++;
        } else {
            hasReceivedVelocity = false;
        }
    }

    private boolean canReduceWithKillAura(KillAura killAura) {
        if (!reduceWhenCanAttack.getValue()) {
            return true;
        }
        int autoBlock = killAura.autoBlock.getValue();
        return killAura.blockTick == 0 && autoBlock == 2
                || autoBlock == 6 && killAura.blockTick == killAura.attackTick.getValue()
                || autoBlock != 6 && autoBlock != 2
                || autoBlock == 5 && killAura.blockTick == 0;
    }

    private void performVelocityAttack(Entity target) {
        if (target == null || target == mc.thePlayer || mc.getNetHandler() == null) {
            return;
        }
        AttackEvent event = EventManager.call(new AttackEvent(target));
        if (event.isCancelled()) {
            return;
        }
        mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
        mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
        mc.thePlayer.motionX *= 0.6D;
        mc.thePlayer.motionZ *= 0.6D;
        mc.thePlayer.setSprinting(false);
    }

    private Entity rayCastEntity(float yaw, float pitch, double range) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = RotationUtil.getLook(yaw, pitch);
        Vec3 end = eyePos.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);
        MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(eyePos, end, false, true, false);
        double maxDistance = range;
        if (blockHit != null && blockHit.hitVec != null) {
            maxDistance = blockHit.hitVec.distanceTo(eyePos);
        }

        Entity pointedEntity = null;
        double closest = maxDistance;
        AxisAlignedBB searchBox = mc.thePlayer.getEntityBoundingBox()
                .addCoord(look.xCoord * range, look.yCoord * range, look.zCoord * range)
                .expand(1.0D, 1.0D, 1.0D);
        List<Entity> entities = mc.theWorld.getEntitiesWithinAABBExcludingEntity(mc.thePlayer, searchBox);
        for (Entity entity : entities) {
            if (!(entity instanceof EntityPlayer) || entity == mc.thePlayer || !entity.canBeCollidedWith()) {
                continue;
            }
            float border = entity.getCollisionBorderSize();
            AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
            MovingObjectPosition intercept = box.calculateIntercept(eyePos, end);
            double distance;
            if (box.isVecInside(eyePos)) {
                distance = 0.0D;
            } else if (intercept != null && intercept.hitVec != null) {
                distance = intercept.hitVec.distanceTo(eyePos);
            } else {
                continue;
            }
            if (distance <= closest) {
                pointedEntity = entity;
                closest = distance;
            }
        }
        return pointedEntity;
    }

    private void updateFallDamageFlag(S12PacketEntityVelocity packet) {
        double packetDirection = Math.atan2(packet.getMotionX(), packet.getMotionZ());
        double degreePlayer = getDirection();
        double degreePacket = Math.floorMod((int) Math.toDegrees(packetDirection), 360);
        double angle = Math.abs(degreePacket + degreePlayer);
        angle = Math.floorMod((int) angle, 360);
        boolean inRange = angle >= 120.0D && angle <= 240.0D;
        if (inRange) {
            isFallDamage = false;
        }
    }

    private double getDirection() {
        float moveYaw = mc.thePlayer.rotationYaw;
        if (mc.thePlayer.moveForward != 0.0F && mc.thePlayer.moveStrafing == 0.0F) {
            moveYaw += mc.thePlayer.moveForward > 0.0F ? 0.0F : 180.0F;
        } else if (mc.thePlayer.moveForward != 0.0F && mc.thePlayer.moveStrafing != 0.0F) {
            if (mc.thePlayer.moveForward > 0.0F) {
                moveYaw += mc.thePlayer.moveStrafing > 0.0F ? -45.0F : 45.0F;
            } else {
                moveYaw -= mc.thePlayer.moveStrafing > 0.0F ? -45.0F : 45.0F;
            }
            moveYaw += mc.thePlayer.moveForward > 0.0F ? 0.0F : 180.0F;
        } else if (mc.thePlayer.moveStrafing != 0.0F && mc.thePlayer.moveForward == 0.0F) {
            moveYaw += mc.thePlayer.moveStrafing > 0.0F ? -90.0F : 90.0F;
        }
        return Math.floorMod((int) moveYaw, 360);
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || isInWeb(mc.thePlayer);
    }

    private boolean isInWeb(Entity entity) {
        try {
            if (isInWebField == null) {
                isInWebField = findField(Entity.class, "isInWeb", "field_70134_J", "H");
            }
            return isInWebField != null && isInWebField.getBoolean(entity);
        } catch (Throwable ignored) {
            isInWebField = null;
            return false;
        }
    }

    private void releaseJumpKey() {
        if (mc.gameSettings == null || mc.gameSettings.keyBindJump == null) {
            return;
        }
        int key = mc.gameSettings.keyBindJump.getKeyCode();
        boolean physicallyDown = key < 0 ? Mouse.isButtonDown(key + 100) : Keyboard.isKeyDown(key);
        KeyBindUtil.setKeyBindState(key, physicallyDown);
    }

    private void setS12Motion(S12PacketEntityVelocity packet, int motionX, int motionY, int motionZ) {
        try {
            if (s12MotionXField == null) {
                s12MotionXField = findField(S12PacketEntityVelocity.class, "motionX", "field_149415_b", "b");
                s12MotionYField = findField(S12PacketEntityVelocity.class, "motionY", "field_149416_c", "c");
                s12MotionZField = findField(S12PacketEntityVelocity.class, "motionZ", "field_149414_d", "d");
            }
            if (s12MotionXField != null) {
                s12MotionXField.setInt(packet, motionX);
            }
            if (s12MotionYField != null) {
                s12MotionYField.setInt(packet, motionY);
            }
            if (s12MotionZField != null) {
                s12MotionZField.setInt(packet, motionZ);
            }
        } catch (Throwable ignored) {
            s12MotionXField = null;
            s12MotionYField = null;
            s12MotionZField = null;
        }
    }

    private void setS27Motion(S27PacketExplosion packet, float motionX, float motionY, float motionZ) {
        try {
            if (s27MotionXField == null) {
                s27MotionXField = findField(S27PacketExplosion.class, "field_149152_f", "f");
                s27MotionYField = findField(S27PacketExplosion.class, "field_149153_g", "g");
                s27MotionZField = findField(S27PacketExplosion.class, "field_149159_h", "h");
            }
            if (s27MotionXField != null) {
                s27MotionXField.setFloat(packet, motionX);
            }
            if (s27MotionYField != null) {
                s27MotionYField.setFloat(packet, motionY);
            }
            if (s27MotionZField != null) {
                s27MotionZField.setFloat(packet, motionZ);
            }
        } catch (Throwable ignored) {
            s27MotionXField = null;
            s27MotionYField = null;
            s27MotionZField = null;
        }
    }

    private static Field findField(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private void dbg(String message) {
        if (debug.getValue()) {
            Helper.sendMessage(message);
        }
    }

    private void resetState() {
        releaseJumpKey();
        knockback = false;
        hasReceivedVelocity = false;
        allowNext = true;
        delayFlag = false;
        delayCounter = 0;
        delayedVelocityPacket = null;
        isFallDamage = false;
        jumpFlag = false;
        ticksSinceVelocity = -1;
        handleReset = false;
        rotatoTickCounter = 0;
        targetRotation = null;
        knockbackX = 0.0D;
        knockbackZ = 0.0D;
        reduceTick = -1;
    }
}
