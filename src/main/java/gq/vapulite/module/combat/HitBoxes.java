package gq.vapulite.module.combat;

import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;
import gq.vapulite.value.Numbers;
import gq.vapulite.value.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemSword;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class HitBoxes extends Module {
    private final Numbers<Double> expand = new Numbers<Double>("Expand", "Expand", 0.18, 0.0, 1.0, 0.01);
    private final Numbers<Double> range = new Numbers<Double>("Range", "Range", 3.2, 3.0, 6.0, 0.1);
    private final Option<Boolean> weaponOnly = new Option<Boolean>("Weapon Only", "WeaponOnly", false);
    private final Option<Boolean> players = new Option<Boolean>("Players", "Players", true);
    private final Option<Boolean> mobs = new Option<Boolean>("Mobs", "Mobs", true);
    private final Option<Boolean> animals = new Option<Boolean>("Animals", "Animals", true);
    private final Option<Boolean> throughWalls = new Option<Boolean>("Through Walls", "ThroughWalls", false);
    private MovingObjectPosition expandedHit;

    public HitBoxes() {
        super("HitBoxes", Keyboard.KEY_NONE, ModuleType.Combat, "Expand entity hit detection");
        this.addValues(expand, range, weaponOnly, players, mobs, animals, throughWalls);
        Chinese = "变胖";
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isInGame()) {
            expandedHit = null;
            return;
        }
        expandedHit = getExpandedMouseOver(1.0f);
        if (expandedHit != null && expandedHit.entityHit != null && mc.objectMouseOver != null
                && mc.objectMouseOver.entityHit == null) {
            mc.objectMouseOver = expandedHit;
            mc.pointedEntity = expandedHit.entityHit;
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.button == 0 && event.buttonstate && expandedHit != null) {
            mc.objectMouseOver = expandedHit;
            mc.pointedEntity = expandedHit.entityHit;
        }
    }

    private MovingObjectPosition getExpandedMouseOver(float partialTicks) {
        if (Boolean.TRUE.equals(weaponOnly.getValue()) && !isHoldingCombatTool()) {
            return null;
        }
        Entity view = mc.getRenderViewEntity();
        if (view == null || mc.theWorld == null) {
            return null;
        }
        double reach = range.getValue();
        Vec3 eyes = view.getPositionEyes(partialTicks);
        Vec3 look = view.getLook(partialTicks);
        Vec3 end = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        Entity pointed = null;
        Vec3 hitVec = null;
        double bestDistance = reach;
        List<Entity> entities = mc.theWorld.getEntitiesWithinAABBExcludingEntity(view,
                view.getEntityBoundingBox().addCoord(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach)
                        .expand(1.0D, 1.0D, 1.0D));

        for (Entity entity : entities) {
            if (!canHit(entity, reach)) {
                continue;
            }
            double amount = entity.getCollisionBorderSize() + expand.getValue();
            AxisAlignedBB box = entity.getEntityBoundingBox().expand(amount, amount, amount);
            MovingObjectPosition intercept = box.calculateIntercept(eyes, end);
            if (box.isVecInside(eyes)) {
                if (bestDistance >= 0.0D) {
                    pointed = entity;
                    hitVec = intercept == null ? eyes : intercept.hitVec;
                    bestDistance = 0.0D;
                }
            } else if (intercept != null) {
                double distance = eyes.distanceTo(intercept.hitVec);
                if (distance < bestDistance || bestDistance == 0.0D) {
                    pointed = entity;
                    hitVec = intercept.hitVec;
                    bestDistance = distance;
                }
            }
        }
        return pointed == null || hitVec == null ? null : new MovingObjectPosition(pointed, hitVec);
    }

    private boolean canHit(Entity entity, double maxRange) {
        if (!entity.canBeCollidedWith()) {
            return false;
        }
        if (entity instanceof EntityLivingBase) {
            return CombatUtil.isValidTarget((EntityLivingBase) entity, maxRange, 180.0D, players.getValue(),
                    mobs.getValue(), animals.getValue(), throughWalls.getValue());
        }
        return entity instanceof EntityItemFrame;
    }

    private boolean isHoldingCombatTool() {
        if (mc.thePlayer.getCurrentEquippedItem() == null) {
            return false;
        }
        return mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemSword
                || mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemAxe;
    }
}
