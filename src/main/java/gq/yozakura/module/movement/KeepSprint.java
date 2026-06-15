package gq.yozakura.module.movement;

import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.runtime.Module;
import gq.yozakura.util.module.RotationUtil;
import gq.yozakura.value.properties.BooleanProperty;
import gq.yozakura.value.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;

public class KeepSprint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty prediction = new BooleanProperty("Prediction", false);
    public final PercentProperty slowdown = new PercentProperty("Slowdown", 0);
    public final BooleanProperty groundOnly = new BooleanProperty("Ground Only", false);
    public final BooleanProperty reachOnly = new BooleanProperty("Reach Only", false);
    private boolean can;

    public KeepSprint() {
        super("KeepSprint", false);
        setCategory(ModuleType.Movement);
        Chinese = "保持疾跑";
        Descript = "Keep sprint after attacking";
        About = Descript;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            can = false;
        }
        if (event.getType() == EventType.POST) {
            can = true;
        }
    }

    public boolean shouldKeepSprint() {
        return shouldKeepSprint(null);
    }

    public boolean shouldKeepSprint(Entity target) {
        if (!isEnabled() || mc.thePlayer == null) {
            return false;
        }
        if (prediction.getValue() && !can) {
            return false;
        }
        if (groundOnly.getValue() && !mc.thePlayer.onGround) {
            return false;
        }
        if (!reachOnly.getValue()) {
            return true;
        }
        if (isReachHitBeyondVanilla()) {
            return true;
        }
        return target != null && RotationUtil.distanceToEntity(target) > 3.0D;
    }

    private boolean isReachHitBeyondVanilla() {
        if (mc.objectMouseOver == null
                || mc.objectMouseOver.hitVec == null
                || mc.getRenderViewEntity() == null
                || mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.MISS) {
            return false;
        }
        return mc.objectMouseOver.hitVec.distanceTo(mc.getRenderViewEntity().getPositionEyes(1.0F)) > 3.0D;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{slowdown.getValue() + "%"};
    }
}
