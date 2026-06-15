package gq.vapulite.manager;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import gq.vapulite.event.bus.EventTarget;
import gq.vapulite.event.bus.types.EventType;
import gq.vapulite.event.bus.types.Priority;
import gq.vapulite.event.bridge.Render3DEvent;
import gq.vapulite.event.bridge.TickEvent;

public class RotationManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private float lastUpdate;
    private float yawDelta;
    private float pitchDelta;
    private int priority;
    private boolean rotated;

    public RotationManager() {
        this.lastUpdate = Float.NaN;
        this.yawDelta = Float.NaN;
        this.pitchDelta = Float.NaN;
        this.priority = Integer.MIN_VALUE;
        this.rotated = false;
    }

    private void applyRotation(float partialTicks) {
        this.lastUpdate = partialTicks;
    }

    private void resetRotationState() {
        this.lastUpdate = Float.NaN;
        this.yawDelta = Float.NaN;
        this.pitchDelta = Float.NaN;
        this.priority = Integer.MIN_VALUE;
        this.rotated = false;
    }

    public void setRotation(float yaw, float pitch, int priority, boolean force) {
        if (this.priority <= priority) {
            this.priority = priority;
            this.yawDelta = MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
            this.pitchDelta = MathHelper.clamp_float(pitch - mc.thePlayer.rotationPitch, -90.0F, 90.0F);
            this.lastUpdate = 0.0F;
            this.rotated = force;
        }
    }

    public boolean isRotated() {
        return this.rotated;
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        this.applyRotation(1.0F);
        this.resetRotationState();
    }

    @EventTarget(Priority.HIGHEST)
    public void onRender3D(Render3DEvent event) {
        this.applyRotation(event.getPartialTicks());
    }
}
