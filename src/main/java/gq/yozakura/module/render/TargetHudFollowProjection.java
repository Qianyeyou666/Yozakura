package gq.yozakura.module.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Captures a world-space body anchor while the 3D camera matrices are active. */
final class TargetHudFollowProjection {
    private static final float EDGE_MARGIN = 2.0F;
    private static final float SIDE_GAP = 12.0F;
    private static final float FOLLOW_SPEED = 18.0F;

    private final FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer projected = BufferUtils.createFloatBuffer(3);

    private int targetId = Integer.MIN_VALUE;
    private float screenX;
    private float screenY;
    private boolean valid;
    private long lastCaptureNanos;

    void capture(EntityLivingBase target, float partialTicks, Minecraft minecraft) {
        if (target == null || minecraft == null || minecraft.theWorld == null) {
            clear();
            return;
        }
        try {
            captureMatrices();
            RenderManager renderManager = minecraft.getRenderManager();
            double x = interpolate(target.lastTickPosX, target.posX, partialTicks) - renderManager.viewerPosX;
            double y = interpolate(target.lastTickPosY, target.posY, partialTicks) - renderManager.viewerPosY
                    + target.height * 0.55D;
            double z = interpolate(target.lastTickPosZ, target.posZ, partialTicks) - renderManager.viewerPosZ;
            projected.clear();
            modelView.rewind();
            projection.rewind();
            viewport.rewind();
            if (!GLU.gluProject((float) x, (float) y, (float) z,
                    modelView, projection, viewport, projected)) {
                valid = false;
                return;
            }

            float depth = projected.get(2);
            int scaleFactor = new ScaledResolution(minecraft).getScaleFactor();
            float nextX = projected.get(0) / scaleFactor;
            float nextY = (minecraft.displayHeight - projected.get(1)) / scaleFactor;
            ScaledResolution resolution = new ScaledResolution(minecraft);
            if (depth < 0.0F || depth > 1.0F || nextX < 0.0F
                    || nextX > resolution.getScaledWidth() || nextY < 0.0F
                    || nextY > resolution.getScaledHeight()) {
                valid = false;
                return;
            }

            long now = System.nanoTime();
            if (!valid || targetId != target.getEntityId() || lastCaptureNanos == 0L) {
                screenX = nextX;
                screenY = nextY;
            } else {
                float deltaSeconds = Math.min(0.1F, Math.max(0.0F,
                        (now - lastCaptureNanos) / 1_000_000_000.0F));
                float blend = 1.0F - (float) Math.exp(-FOLLOW_SPEED * deltaSeconds);
                screenX += (nextX - screenX) * blend;
                screenY += (nextY - screenY) * blend;
            }
            targetId = target.getEntityId();
            lastCaptureNanos = now;
            valid = true;
        } catch (Throwable ignored) {
            valid = false;
        }
    }

    Position position(EntityLivingBase target, float width, float height, ScaledResolution resolution) {
        if (!valid || target == null || resolution == null || target.getEntityId() != targetId) {
            return null;
        }
        return placeBeside(screenX, screenY, width, height,
                resolution.getScaledWidth(), resolution.getScaledHeight());
    }

    void clear() {
        targetId = Integer.MIN_VALUE;
        valid = false;
        lastCaptureNanos = 0L;
    }

    static Position place(float anchorX, float anchorY, float width, float height,
                          float screenWidth, float screenHeight) {
        return placeBeside(anchorX, anchorY, width, height, screenWidth, screenHeight);
    }

    static Position placeBeside(float anchorX, float anchorY, float width, float height,
                                float screenWidth, float screenHeight) {
        float maxX = Math.max(EDGE_MARGIN, screenWidth - width - EDGE_MARGIN);
        float maxY = Math.max(EDGE_MARGIN, screenHeight - height - EDGE_MARGIN);
        float rightX = anchorX + SIDE_GAP;
        float leftX = anchorX - width - SIDE_GAP;
        float x = rightX + width <= screenWidth - EDGE_MARGIN ? rightX : leftX;
        return new Position(clamp(x, EDGE_MARGIN, maxX),
                clamp(anchorY - height * 0.5F, EDGE_MARGIN, maxY));
    }

    private void captureMatrices() {
        modelView.clear();
        projection.clear();
        viewport.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        float progress = Math.max(0.0F, Math.min(1.0F, partialTicks));
        return previous + (current - previous) * progress;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static final class Position {
        private final float x;
        private final float y;

        Position(float x, float y) {
            this.x = x;
            this.y = y;
        }

        float getX() {
            return x;
        }

        float getY() {
            return y;
        }
    }
}
