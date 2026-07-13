package gq.yozakura.bridge;

import gq.yozakura.event.bridge.Render2DEvent;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.engine.render.ShaderRenderer;
import gq.yozakura.engine.render.ui.RenderServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class StandaloneGuiIngame extends GuiIngame {
    private static boolean installFailureLogged;
    private static boolean installLogged;
    private static boolean unsupportedGuiLogged;
    private static boolean lunarBypassLogged;
    private static boolean uninstallFailureLogged;
    private static int dispatchLogCount;
    private static GuiIngame originalGui;
    private static StandaloneGuiIngame installedGui;

    public StandaloneGuiIngame(Minecraft minecraft) {
        super(minecraft);
    }

    private static boolean lunarDetected;
    private static boolean lunarCheckDone;

    public static boolean isLunarClient() {
        if (!lunarCheckDone) {
            lunarCheckDone = true;
            try {
                lunarDetected = Class.forName("com.moonsworth.lunar.genesis.Genesis", false,
                        Thread.currentThread().getContextClassLoader()) != null;
            } catch (Throwable ignored) {
            }
            if (!lunarDetected) {
                lunarDetected = System.getProperty("lunar.webosr.url") != null
                        || System.getProperty("ichor.logsFile") != null;
            }
            if (!lunarDetected) {
                String cmd = System.getProperty("sun.java.command", "");
                lunarDetected = cmd != null && (cmd.toLowerCase().contains(".lunarclient")
                        || cmd.toLowerCase().contains("com.moonsworth.lunar.genesis"));
            }
        }
        return lunarDetected;
    }

    public static void install(Minecraft minecraft) {
        // On Lunar Client, do NOT replace ingameGUI — Lunar's HUD Caching
        // relies on its own ingameGUI instance and replacing it kills FPS.
        // Render2DEvent is dispatched via StandaloneEntityRenderer only when its hook can be installed safely.
        if (isLunarClient()) {
            if (!lunarBypassLogged) {
                lunarBypassLogged = true;
                log("Standalone ingame GUI hook skipped on Lunar Client: preserving Lunar HUD lifecycle. "
                        + "Render2D dispatch requires a compatible client renderer hook.", null);
            }
            return;
        }
        if (minecraft == null) {
            return;
        }
        GuiIngame current = minecraft.ingameGUI;
        if (current == installedGui) {
            return;
        }
        if (current == null) {
            logUnsupportedGui("no GuiIngame is available");
            return;
        }
        if (current.getClass() != GuiIngame.class) {
            logUnsupportedGui("runtime GUI " + current.getClass().getName());
            return;
        }
        try {
            StandaloneGuiIngame hook = new StandaloneGuiIngame(minecraft);
            copyState(current, hook);
            originalGui = current;
            installedGui = hook;
            minecraft.ingameGUI = hook;
            if (!installLogged) {
                installLogged = true;
                log("Standalone ingame GUI hook installed: old="
                        + current.getClass().getName()
                        + ", new=" + hook.getClass().getName(), null);
            }
        } catch (Throwable throwable) {
            if (minecraft.ingameGUI == installedGui) {
                minecraft.ingameGUI = originalGui;
            }
            installedGui = null;
            originalGui = null;
            if (!installFailureLogged) {
                installFailureLogged = true;
                log("Failed to install standalone ingame GUI hook", throwable);
            }
        }
    }

    public static void uninstall(Minecraft minecraft) {
        StandaloneGuiIngame installed = installedGui;
        GuiIngame original = originalGui;
        try {
            if (minecraft != null && installed != null && minecraft.ingameGUI == installed) {
                minecraft.ingameGUI = original;
                log("Standalone ingame GUI hook removed", null);
            }
        } catch (Throwable throwable) {
            if (!uninstallFailureLogged) {
                uninstallFailureLogged = true;
                log("Failed to remove standalone ingame GUI hook", throwable);
            }
        } finally {
            installedGui = null;
            originalGui = null;
        }
    }

    private static void logUnsupportedGui(String guiDescription) {
        if (unsupportedGuiLogged) {
            return;
        }
        unsupportedGuiLogged = true;
        log("Standalone ingame GUI hook skipped: preserving " + guiDescription
                + ". Replacing a runtime GuiIngame subclass would discard its overlay lifecycle.", null);
    }

    @Override
    public void renderGameOverlay(float partialTicks) {
        renderGameOverlayHook(partialTicks);
    }

    private void renderGameOverlayHook(float partialTicks) {
        super.renderGameOverlay(partialTicks);
        dispatchRender2D(partialTicks);
    }

    /**
     * Entry point for Lunar Client where ingameGUI is not replaced.
     * Called from {@link StandaloneEntityRenderer} after world + 3D overlay rendering.
     */
    public static void dispatchRender2DExternally(float partialTicks) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null || minecraft.thePlayer == null) {
            return;
        }
        OverlayRenderState state = beginOverlayState(minecraft);
        try {
            dispatchRenderTick(gq.yozakura.bridge.forge.TickEvent.Phase.START, partialTicks);
            ShaderRenderer.beginOverlayFrame();
            RenderServices.beginHudEffectsFrame();
            try {
                EventManager.call(new Render2DEvent(partialTicks));
            } finally {
                RenderServices.flushHudEffectsFrame();
            }
            YozakuraEventBridge.markOverlayRendered();
            prepareOverlayState();
            EventManager.call(new gq.yozakura.bridge.forge.RenderGameOverlayEvent.Text(partialTicks));
            dispatchRenderTick(gq.yozakura.bridge.forge.TickEvent.Phase.END, partialTicks);
        } catch (Throwable throwable) {
            log("Standalone Render2D dispatch failed", throwable);
        } finally {
            endOverlayState(state);
        }
    }

    private void dispatchRender2D(float partialTicks) {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft.theWorld == null || minecraft.thePlayer == null) {
                return;
            }
            if (dispatchLogCount < 5) {
                dispatchLogCount++;
                log("Standalone Render2D dispatch #" + dispatchLogCount + " partialTicks=" + partialTicks, null);
            }
            OverlayRenderState state = beginOverlayState(minecraft);
            try {
                dispatchRenderTick(gq.yozakura.bridge.forge.TickEvent.Phase.START, partialTicks);
                ShaderRenderer.beginOverlayFrame();
                RenderServices.beginHudEffectsFrame();
                try {
                    EventManager.call(new Render2DEvent(partialTicks));
                } finally {
                    RenderServices.flushHudEffectsFrame();
                }
                YozakuraEventBridge.markOverlayRendered();
                prepareOverlayState();
                EventManager.call(new gq.yozakura.bridge.forge.RenderGameOverlayEvent.Text(partialTicks));
                dispatchRenderTick(gq.yozakura.bridge.forge.TickEvent.Phase.END, partialTicks);
            } finally {
                endOverlayState(state);
            }
        } catch (Throwable throwable) {
            log("Standalone Render2D dispatch failed", throwable);
        }
    }

    private static void dispatchRenderTick(gq.yozakura.bridge.forge.TickEvent.Phase phase, float partialTicks) {
        EventManager.call(new gq.yozakura.bridge.forge.TickEvent.RenderTickEvent(phase, partialTicks));
    }

    private static OverlayRenderState beginOverlayState(Minecraft minecraft) {
        OverlayRenderState state = new OverlayRenderState();
        state.matrixMode = getInteger(GL11.GL_MATRIX_MODE, GL11.GL_MODELVIEW);
        state.program = currentProgram();
        state.activeTexture = getInteger(GL13.GL_ACTIVE_TEXTURE, OpenGlHelper.defaultTexUnit);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        state.texture0 = getInteger(GL11.GL_TEXTURE_BINDING_2D, 0);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_SCISSOR_BIT
                | GL11.GL_STENCIL_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT
                | GL11.GL_TRANSFORM_BIT
                | GL11.GL_VIEWPORT_BIT);

        ScaledResolution scaled = new ScaledResolution(minecraft);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, scaled.getScaledWidth(), scaled.getScaledHeight(), 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
        prepareOverlayState();
        return state;
    }

    private static void prepareOverlayState() {
        GL20.glUseProgram(0);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        RenderHelper.disableStandardItemLighting();

        GL11.glColorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDepthMask(false);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.depthMask(false);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void endOverlayState(OverlayRenderState state) {
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(state == null ? GL11.GL_MODELVIEW : state.matrixMode);
        GL11.glPopAttrib();
        if (state != null) {
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, state.texture0);
            useProgram(state.program);
            OpenGlHelper.setActiveTexture(state.activeTexture);
        } else {
            useProgram(0);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        }
        gq.yozakura.engine.render.GLStateManager.syncToCurrent();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static int currentProgram() {
        try {
            return GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int getInteger(int target, int fallback) {
        try {
            return GL11.glGetInteger(target);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void useProgram(int program) {
        try {
            GL20.glUseProgram(program);
        } catch (Throwable ignored) {
        }
    }

    private static void copyState(GuiIngame source, GuiIngame target) {
        if (source == null || target == null) {
            return;
        }
        Class<?> type = GuiIngame.class;
        while (type != null && type != Object.class) {
            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    if (!field.isAccessible()) {
                        field.setAccessible(true);
                    }
                    field.set(target, field.get(source));
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }

    private static void log(String message, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraStandalone.log");
            PrintWriter writer = new PrintWriter(new FileWriter(log, true));
            try {
                writer.println(message);
                if (throwable != null) {
                    throwable.printStackTrace(writer);
                }
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private static final class OverlayRenderState {
        private int matrixMode;
        private int program;
        private int activeTexture;
        private int texture0;
    }
}
