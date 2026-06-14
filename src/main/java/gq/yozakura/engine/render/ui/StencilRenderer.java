package gq.yozakura.engine.render.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.EXTPackedDepthStencil;
import org.lwjgl.opengl.GL11;

public final class StencilRenderer {
    private final Minecraft mc = Minecraft.getMinecraft();

    public void initWrite() {
        mc.getFramebuffer().bindFramebuffer(false);
        checkSetupFbo(mc.getFramebuffer());
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 1);
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE);
        GL11.glColorMask(false, false, false, false);
    }

    public void read(int ref) {
        GL11.glColorMask(true, true, true, true);
        GL11.glStencilFunc(GL11.GL_EQUAL, ref, 1);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
    }

    public void end() {
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GlStateManager.bindTexture(0);
    }

    private void checkSetupFbo(Framebuffer framebuffer) {
        if (framebuffer == null || framebuffer.depthBuffer <= -1) {
            return;
        }
        setupFbo(framebuffer);
        framebuffer.depthBuffer = -1;
    }

    private void setupFbo(Framebuffer framebuffer) {
        EXTFramebufferObject.glDeleteRenderbuffersEXT(framebuffer.depthBuffer);
        int stencilDepthBufferId = EXTFramebufferObject.glGenRenderbuffersEXT();
        EXTFramebufferObject.glBindRenderbufferEXT(
                EXTFramebufferObject.GL_RENDERBUFFER_EXT, stencilDepthBufferId);
        EXTFramebufferObject.glRenderbufferStorageEXT(
                EXTFramebufferObject.GL_RENDERBUFFER_EXT,
                EXTPackedDepthStencil.GL_DEPTH_STENCIL_EXT,
                mc.displayWidth,
                mc.displayHeight);
        EXTFramebufferObject.glFramebufferRenderbufferEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                EXTFramebufferObject.GL_STENCIL_ATTACHMENT_EXT,
                EXTFramebufferObject.GL_RENDERBUFFER_EXT,
                stencilDepthBufferId);
        EXTFramebufferObject.glFramebufferRenderbufferEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                EXTFramebufferObject.GL_DEPTH_ATTACHMENT_EXT,
                EXTFramebufferObject.GL_RENDERBUFFER_EXT,
                stencilDepthBufferId);
    }
}
