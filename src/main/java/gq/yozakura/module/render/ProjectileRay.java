package gq.yozakura.module.render;

import gq.yozakura.event.bridge.Render3DEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.render.RenderUtil;
import gq.yozakura.value.Numbers;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.List;

public final class ProjectileRay extends Module {
    private static final int BOW_COLOR = 0xFF67D7FF;
    private static final int THROWABLE_COLOR = 0xFFFFC857;
    private static final double PROJECTILE_RADIUS = 0.25D;

    private final Numbers<Double> traceTicks =
            new Numbers<Double>("Trace Ticks", "TraceTicks", 80.0D, 20.0D, 160.0D, 5.0D);

    public ProjectileRay() {
        super("ProjectileRay", Keyboard.KEY_NONE, ModuleType.Render,
                "Preview the trajectory and landing point of the held projectile");
        this.addValues(traceTicks);
        Chinese = "投掷物射线";
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isInGame() || mc.getRenderManager() == null) {
            return;
        }
        ItemStack heldItem = mc.thePlayer.inventory.getCurrentItem();
        ProjectileRayPolicy.ProjectileKind kind = projectileKind(heldItem);
        if (kind == null) {
            return;
        }

        int useDuration = kind == ProjectileRayPolicy.ProjectileKind.BOW
                ? mc.thePlayer.getItemInUseDuration() : 0;
        float partialTicks = event.getPartialTicks();
        float yaw = interpolateRotation(mc.thePlayer.prevRotationYaw,
                mc.thePlayer.rotationYaw, partialTicks);
        float pitch = interpolateRotation(mc.thePlayer.prevRotationPitch,
                mc.thePlayer.rotationPitch, partialTicks);
        ProjectileRayPolicy.LaunchSpec spec = ProjectileRayPolicy.launchSpec(
                kind, useDuration, yaw, pitch);
        if (spec == null) {
            return;
        }

        ProjectileRayPolicy.Point start = launchPosition(yaw, partialTicks);
        List<ProjectileRayPolicy.Point> path = ProjectileRayPolicy.trace(
                start, spec, traceTicks.getValue().intValue());
        TraceResult result = traceCollision(path);
        renderPath(result, kind == ProjectileRayPolicy.ProjectileKind.BOW
                ? BOW_COLOR : THROWABLE_COLOR);
        if (result.hit != null) {
            drawLandingMarker(result.hit, kind == ProjectileRayPolicy.ProjectileKind.BOW
                    ? BOW_COLOR : THROWABLE_COLOR);
        }
    }

    private ProjectileRayPolicy.ProjectileKind projectileKind(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        Item item = stack.getItem();
        if (item instanceof ItemBow) {
            return ProjectileRayPolicy.ProjectileKind.BOW;
        }
        if (item == Items.snowball) {
            return ProjectileRayPolicy.ProjectileKind.SNOWBALL;
        }
        if (item == Items.egg) {
            return ProjectileRayPolicy.ProjectileKind.EGG;
        }
        if (item == Items.ender_pearl) {
            return ProjectileRayPolicy.ProjectileKind.ENDER_PEARL;
        }
        return null;
    }

    private ProjectileRayPolicy.Point launchPosition(float yaw, float partialTicks) {
        double playerX = interpolate(mc.thePlayer.lastTickPosX, mc.thePlayer.posX, partialTicks);
        double playerY = interpolate(mc.thePlayer.lastTickPosY, mc.thePlayer.posY, partialTicks);
        double playerZ = interpolate(mc.thePlayer.lastTickPosZ, mc.thePlayer.posZ, partialTicks);
        double yawRadians = Math.toRadians(yaw);
        return new ProjectileRayPolicy.Point(
                playerX - Math.cos(yawRadians) * 0.16D,
                playerY + mc.thePlayer.getEyeHeight() - 0.1D,
                playerZ - Math.sin(yawRadians) * 0.16D);
    }

    private TraceResult traceCollision(List<ProjectileRayPolicy.Point> path) {
        TraceResult result = new TraceResult();
        if (path.isEmpty()) {
            return result;
        }
        result.points.add(path.get(0));
        ProjectileRayPolicy.Point previous = path.get(0);
        for (int index = 1; index < path.size(); index++) {
            ProjectileRayPolicy.Point point = path.get(index);
            Vec3 start = toVec(previous);
            Vec3 end = toVec(point);
            MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(
                    start, end, false, true, false);
            MovingObjectPosition entityHit = findEntityHit(start, end);
            MovingObjectPosition nearest = nearestHit(start, blockHit, entityHit);
            if (nearest != null) {
                result.hit = nearest.hitVec;
                result.points.add(new ProjectileRayPolicy.Point(
                        result.hit.xCoord, result.hit.yCoord, result.hit.zCoord));
                return result;
            }
            result.points.add(point);
            previous = point;
        }
        return result;
    }

    private MovingObjectPosition findEntityHit(Vec3 start, Vec3 end) {
        AxisAlignedBB swept = new AxisAlignedBB(
                Math.min(start.xCoord, end.xCoord),
                Math.min(start.yCoord, end.yCoord),
                Math.min(start.zCoord, end.zCoord),
                Math.max(start.xCoord, end.xCoord),
                Math.max(start.yCoord, end.yCoord),
                Math.max(start.zCoord, end.zCoord)).expand(
                PROJECTILE_RADIUS, PROJECTILE_RADIUS, PROJECTILE_RADIUS);
        List<Entity> entities = mc.theWorld.getEntitiesWithinAABBExcludingEntity(
                mc.thePlayer, swept);
        MovingObjectPosition nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (Entity entity : entities) {
            if (entity == null || !entity.canBeCollidedWith()) {
                continue;
            }
            float border = entity.getCollisionBorderSize();
            AxisAlignedBB box = entity.getEntityBoundingBox().expand(
                    border + PROJECTILE_RADIUS,
                    border + PROJECTILE_RADIUS,
                    border + PROJECTILE_RADIUS);
            MovingObjectPosition intercept = box.calculateIntercept(start, end);
            if (intercept == null) {
                continue;
            }
            double distance = start.squareDistanceTo(intercept.hitVec);
            if (distance < nearestDistance) {
                nearest = intercept;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private MovingObjectPosition nearestHit(Vec3 start, MovingObjectPosition blockHit,
                                            MovingObjectPosition entityHit) {
        if (blockHit == null) {
            return entityHit;
        }
        if (entityHit == null) {
            return blockHit;
        }
        return start.squareDistanceTo(entityHit.hitVec)
                < start.squareDistanceTo(blockHit.hitVec) ? entityHit : blockHit;
    }

    private void renderPath(TraceResult result, int color) {
        if (result.points.size() < 2) {
            return;
        }
        RenderManager renderManager = mc.getRenderManager();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
            GL11.glLineWidth(2.0F);
            GL11.glColor4f((color >> 16 & 255) / 255.0F,
                    (color >> 8 & 255) / 255.0F,
                    (color & 255) / 255.0F, 0.92F);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (ProjectileRayPolicy.Point point : result.points) {
                GL11.glVertex3d(point.getX() - renderManager.viewerPosX,
                        point.getY() - renderManager.viewerPosY,
                        point.getZ() - renderManager.viewerPosZ);
            }
            GL11.glEnd();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            gq.yozakura.engine.render.GLStateManager.syncToCurrent();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void drawLandingMarker(Vec3 hit, int color) {
        RenderManager renderManager = mc.getRenderManager();
        double radius = 0.18D;
        AxisAlignedBB marker = new AxisAlignedBB(
                hit.xCoord - renderManager.viewerPosX - radius,
                hit.yCoord - renderManager.viewerPosY - radius,
                hit.zCoord - renderManager.viewerPosZ - radius,
                hit.xCoord - renderManager.viewerPosX + radius,
                hit.yCoord - renderManager.viewerPosY + radius,
                hit.zCoord - renderManager.viewerPosZ + radius);
        RenderUtil.drawBox(marker, color & 0x45FFFFFF, true);
        RenderUtil.drawOutlinedBox(marker, color, true);
    }

    private static Vec3 toVec(ProjectileRayPolicy.Point point) {
        return new Vec3(point.getX(), point.getY(), point.getZ());
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    private static float interpolateRotation(float previous, float current, float partialTicks) {
        float difference = current - previous;
        while (difference < -180.0F) {
            difference += 360.0F;
        }
        while (difference >= 180.0F) {
            difference -= 360.0F;
        }
        return previous + difference * partialTicks;
    }

    private static final class TraceResult {
        private final java.util.ArrayList<ProjectileRayPolicy.Point> points =
                new java.util.ArrayList<ProjectileRayPolicy.Point>();
        private Vec3 hit;
    }
}
