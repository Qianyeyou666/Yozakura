package gq.vapulite.util.render;

import gq.vapulite.util.math.Vec3f;
import gq.vapulite.engine.render.GLStateManager;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;


public final class GLUtils {
    public static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
    public static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    public static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    public static final FloatBuffer TO_SCREEN_BUFFER = BufferUtils.createFloatBuffer(3);
    public static final FloatBuffer TO_WORLD_BUFFER = BufferUtils.createFloatBuffer(3);

    private GLUtils() {
    }

    public static void init() {
    }

    public static float[] getColor(int hex) {
        return new float[]{(float)(hex >> 16 & 255) / 255.0f, (float)(hex >> 8 & 255) / 255.0f, (float)(hex & 255) / 255.0f, (float)(hex >> 24 & 255) / 255.0f};
    }

    public static void glColor(int hex) {
        float[] color = GLUtils.getColor(hex);
        GLStateManager.color(color[0], color[1], color[2], color[3]);
    }

    public static void rotateX(float angle, double x2, double y2, double z2) {
        GlStateManager.translate(x2, y2, z2);
        GlStateManager.rotate(angle, 1.0f, 0.0f, 0.0f);
        GlStateManager.translate(- x2, - y2, - z2);
    }

    public static void rotateY(float angle, double x2, double y2, double z2) {
        GlStateManager.translate(x2, y2, z2);
        GlStateManager.rotate(angle, 0.0f, 1.0f, 0.0f);
        GlStateManager.translate(- x2, - y2, - z2);
    }

    public static void rotateZ(float angle, double x2, double y2, double z2) {
        GlStateManager.translate(x2, y2, z2);
        GlStateManager.rotate(angle, 0.0f, 0.0f, 1.0f);
        GlStateManager.translate(- x2, - y2, - z2);
    }

    public static Vec3f toScreen(Vec3f pos) {
        return GLUtils.toScreen(pos.getX(), pos.getY(), pos.getZ());
    }


    public static void startSmooth() {
        GLStateManager.lineSmooth(true);
        GLStateManager.polygonSmooth(true);
        GLStateManager.pointSmooth(true);
        GLStateManager.enableBlend();
        GLStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GLStateManager.hint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GLStateManager.hint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
        GLStateManager.hint(GL11.GL_POINT_SMOOTH_HINT, GL11.GL_NICEST);
    }

    public static void endSmooth() {
        GLStateManager.lineSmooth(false);
        GLStateManager.polygonSmooth(false);
        GLStateManager.pointSmooth(false);
    }


    public static Vec3f toScreen(double x2, double y2, double z2) {
        boolean result = GLU.gluProject((float)x2, (float)y2, (float)z2, MODELVIEW, PROJECTION, VIEWPORT, (FloatBuffer)TO_SCREEN_BUFFER.clear());
        if (result) {
            return new Vec3f(TO_SCREEN_BUFFER.get(0), (float)Display.getHeight() - TO_SCREEN_BUFFER.get(1), TO_SCREEN_BUFFER.get(2));
        }
        return null;
    }

    public static Vec3f toWorld(Vec3f pos) {
        return GLUtils.toWorld(pos.getX(), pos.getY(), pos.getZ());
    }

    public static Vec3f toWorld(double x2, double y2, double z2) {
        boolean result = GLU.gluUnProject((float)x2, (float)y2, (float)z2, MODELVIEW, PROJECTION, VIEWPORT, (FloatBuffer)TO_WORLD_BUFFER.clear());
        if (result) {
            return new Vec3f(TO_WORLD_BUFFER.get(0), TO_WORLD_BUFFER.get(1), TO_WORLD_BUFFER.get(2));
        }
        return null;
    }

    public static FloatBuffer getModelview() {
        return MODELVIEW;
    }

    public static FloatBuffer getProjection() {
        return PROJECTION;
    }

    public static IntBuffer getViewport() {
        return VIEWPORT;
    }
}
