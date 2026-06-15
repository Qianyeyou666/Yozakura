package gq.yozakura.module.combat;

import gq.yozakura.event.bridge.LeftClickMouseEvent;
import gq.yozakura.event.bridge.MouseOverEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemSword;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Reach extends Module {
    private final Option<Boolean> randomReach = new Option<Boolean>("Random Reach", "RandomReach", true);
    private final Option<Boolean> weaponOnly = new Option<Boolean>("Weapon Only", "weaponOnly", false);
    private final Option<Boolean> movingOnly = new Option<Boolean>("Moving Only", "movingOnly", false);
    private final Option<Boolean> sprintOnly = new Option<Boolean>("Sprint Only", "sprintOnly", false);
    private final Option<Boolean> hitThroughBlocks = new Option<Boolean>("Through Blocks", "hitThroughBlocks", false);
    private final Option<Boolean> players = new Option<Boolean>("Players", "Players", true);
    private final Option<Boolean> mobs = new Option<Boolean>("Mobs", "Mobs", true);
    private final Option<Boolean> animals = new Option<Boolean>("Animals", "Animals", true);
    private final Numbers<Double> minReach = new Numbers<Double>("Min Reach", "Reach", 3.2, 3.0, 6.0, 0.1);
    private final Numbers<Double> maxReach = new Numbers<Double>("Max Reach", "MaxReach", 3.6, 3.0, 6.0, 0.1);
    private final Numbers<Double> expand = new Numbers<Double>("Expand", "Expand", 0.08, 0.0, 1.0, 0.01);
    private MovingObjectPosition lastReachHit;

    public Reach() {
        super("Reach", Keyboard.KEY_NONE, ModuleType.Combat, "Extend attack ray distance");
        this.addValues(weaponOnly, movingOnly, sprintOnly, hitThroughBlocks, players, mobs, animals,
                randomReach, minReach, maxReach, expand);
        Chinese = "长臂猿";
    }

    @Override
    public void disable() {
        lastReachHit = null;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isInGame()) {
            lastReachHit = null;
            return;
        }
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) {
            return;
        }
        applyReach(1.0f);
    }

    @EventTarget
    public void onBridgeTick(gq.yozakura.event.bridge.TickEvent event) {
        if (event.getType() != EventType.POST || !isInGame()) {
            lastReachHit = null;
            return;
        }
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) {
            return;
        }
        applyReach(1.0f);
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.button != 0 || !event.buttonstate || !isInGame()) {
            return;
        }
        handleLeftClick();
    }

    @EventTarget
    public void onBridgeLeftClick(LeftClickMouseEvent event) {
        if (!isInGame()) {
            return;
        }
        handleLeftClick();
    }

    @EventTarget
    public void onMouseOver(MouseOverEvent event) {
        if (!isInGame()) {
            lastReachHit = null;
            return;
        }
        if (isAttackHeld()) {
            applyReach(event.getPartialTicks());
        }
    }

    private void handleLeftClick() {
        if (lastReachHit != null) {
            applyHit(lastReachHit);
            return;
        }
        applyReach(1.0f);
    }

    private void applyReach(float partialTicks) {
        if (!canReach()) {
            return;
        }
        MovingObjectPosition hit = rayTraceEntity(getReachDistance(), expand.getValue(), partialTicks);
        if (hit == null || hit.entityHit == null) {
            lastReachHit = null;
            return;
        }
        lastReachHit = hit;
        applyHit(hit);
    }

    private void applyHit(MovingObjectPosition hit) {
        mc.objectMouseOver = hit;
        mc.pointedEntity = hit.entityHit;
    }

    private boolean isAttackHeld() {
        if (mc.gameSettings.keyBindAttack.isKeyDown()) {
            return true;
        }
        int key = mc.gameSettings.keyBindAttack.getKeyCode();
        return key < 0 && Mouse.isCreated() && Mouse.isButtonDown(key + 100);
    }

    private boolean canReach() {
        if (Boolean.TRUE.equals(weaponOnly.getValue()) && !isHoldingCombatTool()) {
            return false;
        }
        if (Boolean.TRUE.equals(movingOnly.getValue()) && mc.thePlayer.moveForward == 0.0f && mc.thePlayer.moveStrafing == 0.0f) {
            return false;
        }
        if (Boolean.TRUE.equals(sprintOnly.getValue()) && !mc.thePlayer.isSprinting()) {
            return false;
        }
        if (!Boolean.TRUE.equals(hitThroughBlocks.getValue()) && mc.objectMouseOver != null) {
            BlockPos pos = mc.objectMouseOver.getBlockPos();
            if (pos != null && mc.theWorld.getBlockState(pos).getBlock() != Blocks.air) {
                return false;
            }
        }
        return true;
    }

    private boolean isHoldingCombatTool() {
        if (mc.thePlayer.getCurrentEquippedItem() == null) {
            return false;
        }
        return mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemSword
                || mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemAxe;
    }

    private double getReachDistance() {
        if (!Boolean.TRUE.equals(randomReach.getValue())) {
            return maxReach.getValue();
        }
        double min = Math.min(minReach.getValue(), maxReach.getValue());
        double max = Math.max(minReach.getValue(), maxReach.getValue());
        return min + ThreadLocalRandom.current().nextDouble() * (max - min + 0.001D);
    }

    private MovingObjectPosition rayTraceEntity(double distance, double hitboxExpand, float partialTicks) {
        Entity view = mc.getRenderViewEntity();
        if (view == null || mc.theWorld == null) {
            return null;
        }
        Vec3 eyes = view.getPositionEyes(partialTicks);
        Vec3 look = view.getLook(partialTicks);
        Vec3 end = eyes.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
        Entity pointed = null;
        Vec3 hitVec = null;
        double bestDistance = distance;
        List<Entity> entities = mc.theWorld.getEntitiesWithinAABBExcludingEntity(view,
                view.getEntityBoundingBox().addCoord(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance)
                        .expand(1.0D, 1.0D, 1.0D));

        for (Entity entity : entities) {
            if (!canHitEntity(entity, distance)) {
                continue;
            }
            double border = entity.getCollisionBorderSize() + hitboxExpand;
            AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
            MovingObjectPosition intercept = box.calculateIntercept(eyes, end);
            if (box.isVecInside(eyes)) {
                if (bestDistance >= 0.0D) {
                    pointed = entity;
                    hitVec = intercept == null ? eyes : intercept.hitVec;
                    bestDistance = 0.0D;
                }
            } else if (intercept != null) {
                double currentDistance = eyes.distanceTo(intercept.hitVec);
                if (currentDistance < bestDistance || bestDistance == 0.0D) {
                    if (entity == view.ridingEntity && !entity.canRiderInteract()) {
                        continue;
                    }
                    pointed = entity;
                    hitVec = intercept.hitVec;
                    bestDistance = currentDistance;
                }
            }
        }
        if (pointed == null || hitVec == null) {
            return null;
        }
        return new MovingObjectPosition(pointed, hitVec);
    }

    private boolean canHitEntity(Entity entity, double distance) {
        if (!entity.canBeCollidedWith()) {
            return false;
        }
        if (entity instanceof EntityLivingBase) {
            return CombatUtil.isValidTarget((EntityLivingBase) entity, distance, 180.0D, players.getValue(),
                    mobs.getValue(), animals.getValue(), hitThroughBlocks.getValue());
        }
        return entity instanceof EntityItemFrame;
    }
}
