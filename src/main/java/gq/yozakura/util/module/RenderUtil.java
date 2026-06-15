package gq.vapulite.util.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Timer;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;

public final class RenderUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int RENDER_ATTRIB_MASK = GL11.GL_ENABLE_BIT
            | GL11.GL_COLOR_BUFFER_BIT
            | GL11.GL_CURRENT_BIT
            | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_HINT_BIT
            | GL11.GL_LINE_BIT
            | GL11.GL_POLYGON_BIT
            | GL11.GL_TEXTURE_BIT;
    private static Field timerField;

    private RenderUtil() {
    }

    public static void enableRenderState() {
        GL11.glPushAttrib(RENDER_ATTRIB_MASK);
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glLineWidth(2.0F);
    }

    public static void disableRenderState() {
        try {
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
            gq.vapulite.engine.render.GLStateManager.syncToCurrent();
        }
    }

    public static void drawEntityBox(Entity entity, int red, int green, int blue) {
        float partialTicks = getRenderPartialTicks();
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        AxisAlignedBB box = entity.getEntityBoundingBox()
                .offset(x - entity.posX, y - entity.posY, z - entity.posZ)
                .offset(-mc.getRenderManager().viewerPosX, -mc.getRenderManager().viewerPosY, -mc.getRenderManager().viewerPosZ)
                .expand(0.05D, 0.05D, 0.05D);
        GlStateManager.color(red / 255.0F, green / 255.0F, blue / 255.0F, 0.35F);
        RenderGlobal.drawSelectionBoundingBox(box);
    }

    private static float getRenderPartialTicks() {
        try {
            if (timerField == null) {
                for (String name : new String[]{"timer", "field_71428_T"}) {
                    try {
                        timerField = Minecraft.class.getDeclaredField(name);
                        timerField.setAccessible(true);
                        break;
                    } catch (Throwable ignored) {
                    }
                }
            }
            Object value = timerField == null ? null : timerField.get(mc);
            return value instanceof Timer ? ((Timer) value).renderPartialTicks : 1.0F;
        } catch (Throwable ignored) {
            timerField = null;
            return 1.0F;
        }
    }
}
