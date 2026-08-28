package gq.yozakura.ui.click.yozakura;

import gq.yozakura.engine.render.GLStateManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;

/**
 * Keeps one GPU-side copy of the main-menu framebuffer while Panel ClickGUI is open.
 * The snapshot avoids rerunning GuiMainMenu's panorama and blur pipeline every frame.
 */
final class MainMenuBackdropSnapshot {
    private int textureId;
    private int textureWidth;
    private int textureHeight;
    private boolean captured;

    boolean capture() {
        Minecraft mc = Minecraft.getMinecraft();
        int width = mc.displayWidth;
        int height = mc.displayHeight;
        if (width <= 0 || height <= 0) {
            return false;
        }

        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushAttrib(GL11.GL_TEXTURE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            ensureTexture(width, height);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0,
                    0, 0, 0, 0, width, height);
            captured = true;
            return true;
        } catch (Throwable ignored) {
            captured = false;
            dispose();
            return false;
        } finally {
            GL11.glPopAttrib();
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
            GLStateManager.syncToCurrent();
        }
    }

    boolean draw(float width, float height) {
        if (!captured || textureId == 0 || width <= 0.0f || height <= 0.0f) {
            return false;
        }
        GLStateManager.beginTextured2D(1.0f);
        try {
            GLStateManager.bindTexture2D(textureId);
            GLStateManager.textureEnvModulate();
            GLStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0f, 1.0f);
            GL11.glVertex2f(0.0f, 0.0f);
            GL11.glTexCoord2f(0.0f, 0.0f);
            GL11.glVertex2f(0.0f, height);
            GL11.glTexCoord2f(1.0f, 0.0f);
            GL11.glVertex2f(width, height);
            GL11.glTexCoord2f(1.0f, 1.0f);
            GL11.glVertex2f(width, 0.0f);
            GL11.glEnd();
            return true;
        } finally {
            GLStateManager.endTextured2D();
        }
    }

    boolean isCaptured() {
        Minecraft mc = Minecraft.getMinecraft();
        return captured
                && textureWidth == mc.displayWidth
                && textureHeight == mc.displayHeight;
    }

    void dispose() {
        captured = false;
        textureWidth = 0;
        textureHeight = 0;
        if (textureId != 0) {
            GL11.glDeleteTextures(textureId);
            textureId = 0;
        }
    }

    private void ensureTexture(int width, int height) {
        if (textureId == 0) {
            textureId = GL11.glGenTextures();
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        if (textureWidth != width || textureHeight != height) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB,
                    width, height, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE,
                    (ByteBuffer) null);
            textureWidth = width;
            textureHeight = height;
        }
    }
}
