package gq.vapulite.module.combat;

import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;
import gq.vapulite.util.time.TimerUtil;
import gq.vapulite.util.minecraft.RotationUtil;
import gq.vapulite.value.Numbers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.ThreadLocalRandom;

public class BowAimBot extends Module {
    private final TimerUtil timer = new TimerUtil();
    private final Numbers<Double> yawSpeed = new Numbers<Double>("Yaw Speed", "YawSpeed", 24.0, 2.0, 90.0, 1.0);
    private final Numbers<Double> pitchSpeed = new Numbers<Double>("Pitch Speed", "PitchSpeed", 18.0, 2.0, 90.0, 1.0);
    private final Numbers<Double> prediction = new Numbers<Double>("Prediction", "Prediction", 0.55, 0.0, 2.0, 0.05);
    public EntityLivingBase target;
    public float rangeAimVelocity = 0.0f;
    private final RotationUtil.State rotationState = new RotationUtil.State();

    public BowAimBot() {
        super("BowAimBot", Keyboard.KEY_NONE, ModuleType.Combat,"AutoAim Target when you using bow");
        this.addValues(yawSpeed, pitchSpeed, prediction);
        Chinese="弓箭自瞄";
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (!isInGame()) {
            this.target = null;
            rotationState.reset();
            return;
        }
        ItemStack itemStack = mc.thePlayer.inventory.getCurrentItem();
        if (itemStack == null || !(itemStack.getItem() instanceof ItemBow)) {
            this.target = null;
            rotationState.reset();
            return;
        }
        if (!mc.gameSettings.keyBindUseItem.isKeyDown()) {
            this.target = null;
            rotationState.reset();
            return;
        }
        this.target = this.getClosestEntity();
        if (this.target == null) {
            rotationState.reset();
            return;
        }
        int rangeCharge = mc.thePlayer.getItemInUseCount();
        this.rangeAimVelocity = rangeCharge / 20.0f;
        this.rangeAimVelocity = (this.rangeAimVelocity * this.rangeAimVelocity + this.rangeAimVelocity * 2.0f) / 3.0f;
        if (this.rangeAimVelocity > 1.0f) {
            this.rangeAimVelocity = 1.0f;
        }
        if (this.rangeAimVelocity < 0.12f) {
            return;
        }
        double predict = prediction.getValue();
        double targetX = this.target.posX + (this.target.posX - this.target.lastTickPosX) * predict;
        double targetY = this.target.posY + (this.target.posY - this.target.lastTickPosY) * Math.min(0.8D, predict)
                + this.target.getEyeHeight() - 0.15D;
        double targetZ = this.target.posZ + (this.target.posZ - this.target.lastTickPosZ) * predict;
        double posX = targetX - mc.thePlayer.posX;
        double posY = targetY - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
        double posZ = targetZ - mc.thePlayer.posZ;
        double y2 = Math.sqrt(posX * posX + posZ * posZ);
        float g = 0.006f;
        float tmp = (float) ((double) (this.rangeAimVelocity * this.rangeAimVelocity * this.rangeAimVelocity * this.rangeAimVelocity) - (double) g * ((double) g * (y2 * y2) + 2.0 * posY * (double) (this.rangeAimVelocity * this.rangeAimVelocity)));
        if (tmp < 0.0f || y2 <= 0.0D) {
            return;
        }
        float[] rotations = RotationUtil.getRotationsTo(mc, targetX, targetY, targetZ);
        float pitch = (float) (-Math.toDegrees(Math.atan(((double) (this.rangeAimVelocity * this.rangeAimVelocity) - Math.sqrt(tmp)) / ((double) g * y2))));
        RotationUtil.applyToPlayer(mc, rotations[0], pitch, yawSpeed.getValue().floatValue(),
                pitchSpeed.getValue().floatValue(), false, 0.10f, rotationState, 0.34f, 0.18f, true);
    }

    public boolean check(EntityLivingBase entity) {
        if (entity instanceof EntityArmorStand) {
            return false;
        }
        if (entity == mc.thePlayer) {
            return false;
        }
        if (entity.isDead) {
            return false;
        }
        if (AntiBot.isServerBot(entity)) {
            return false;
        }
        return mc.thePlayer.canEntityBeSeen(entity);
    }

    EntityLivingBase getClosestEntity() {
        EntityLivingBase closestEntity = null;
        java.util.List<Entity> entities = Aimbot.getEntityList();
        if (entities == null) {
            return null;
        }
        for (Entity o : entities) {
            EntityLivingBase entity;
            if (!(o instanceof EntityLivingBase) || o instanceof EntityArmorStand || !this.check(entity = (EntityLivingBase) o) || closestEntity != null && !(mc.thePlayer.getDistanceToEntity(entity) < mc.thePlayer.getDistanceToEntity(closestEntity)))
                continue;
            closestEntity = entity;
        }
        return closestEntity;
    }
}
