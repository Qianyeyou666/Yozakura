package gq.vapulite.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.util.Stack;

public final class RenderState {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final int ATTRIB_MASK = GL11.GL_ENABLE_BIT
            | GL11.GL_CURRENT_BIT
            | GL11.GL_COLOR_BUFFER_BIT
            | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_LINE_BIT
            | GL11.GL_SCISSOR_BIT;
    private static final Stack<int[]> SCISSORS = new Stack<int[]>();

    private RenderState() {
    }

    public static void begin2D() {
        GL11.glPushAttrib(ATTRIB_MASK);
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
    }

    public static void end2D() {
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void beginTextured2D(float alpha) {
        GL11.glPushAttrib(ATTRIB_MASK);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDepthMask(false);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, clamp01(alpha));
    }

    public static void endTextured2D() {
        GL11.glDepthMask(true);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void color(int color) {
        float alpha = (color >> 24 & 0xFF) / 255.0f;
        float red = (color >> 16 & 0xFF) / 255.0f;
        float green = (color >> 8 & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;
        GL11.glColor4f(red, green, blue, alpha);
    }

    public static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public static float clampRadius(float radius, float width, float height) {
        return Math.max(0.0f, Math.min(radius, Math.min(Math.abs(width), Math.abs(height)) / 2.0f));
    }

    public static void applyScissor(float x, float y, float width, float height) {
        int[] bounds = scaledScissor(x, y, width, height);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    public static void disableScissor() {
        SCISSORS.clear();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static void pushScissor(float x, float y, float width, float height) {
        int[] bounds = scaledScissor(x, y, width, height);

        if (!SCISSORS.isEmpty()) {
            int[] parent = SCISSORS.peek();
            int x2 = Math.min(bounds[0] + bounds[2], parent[0] + parent[2]);
            int y2 = Math.min(bounds[1] + bounds[3], parent[1] + parent[3]);
            bounds[0] = Math.max(bounds[0], parent[0]);
            bounds[1] = Math.max(bounds[1], parent[1]);
            bounds[2] = Math.max(0, x2 - bounds[0]);
            bounds[3] = Math.max(0, y2 - bounds[1]);
        }

        SCISSORS.push(bounds);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    private static int[] scaledScissor(float x, float y, float width, float height) {
        ScaledResolution scaled = new ScaledResolution(MC);
        int scale = scaled.getScaleFactor();
        int scaledX = Math.round(x * scale);
        int scaledY = Math.round(MC.displayHeight - (y + height) * scale);
        int scaledWidth = Math.round(width * scale);
        int scaledHeight = Math.round(height * scale);
        return new int[]{scaledX, scaledY, Math.max(0, scaledWidth), Math.max(0, scaledHeight)};
    }

    public static void popScissor() {
        if (SCISSORS.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return;
        }

        SCISSORS.pop();
        if (SCISSORS.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return;
        }

        int[] bounds = SCISSORS.peek();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(bounds[0], bounds[1], bounds[2], bounds[3]);
    }
}
