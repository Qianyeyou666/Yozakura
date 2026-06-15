package gq.yozakura.bridge;

import gq.yozakura.event.bridge.Render3DEvent;
import gq.yozakura.event.bridge.RenderFrameGuard;
import gq.yozakura.event.bus.EventManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class StandaloneEntityRenderer extends EntityRenderer {
    private static boolean installFailureLogged;
    private static boolean renderFailureLogged;
    private static boolean cameraFailureLogged;
    private static boolean installLogged;
    private static int dispatchLogCount;
    private static Method setupCameraTransformMethod;

    public StandaloneEntityRenderer(Minecraft minecraft) {
        super(minecraft, minecraft.getResourceManager());
    }

    public static void install(Minecraft minecraft) {
        if (minecraft == null || minecraft.entityRenderer instanceof StandaloneEntityRenderer) {
            return;
        }
        try {
            EntityRenderer oldRenderer = minecraft.entityRenderer;
            StandaloneEntityRenderer hook = new StandaloneEntityRenderer(minecraft);
            copyState(oldRenderer, hook);
            minecraft.entityRenderer = hook;
            if (!installLogged) {
                installLogged = true;
                log("Standalone entity renderer hook installed: old="
                        + (oldRenderer == null ? "null" : oldRenderer.getClass().getName())
                        + ", new=" + hook.getClass().getName(), null);
            }
        } catch (Throwable throwable) {
            if (!installFailureLogged) {
                installFailureLogged = true;
                log("Failed to install standalone entity renderer hook", throwable);
            }
        }
    }

    @Override
    public void renderWorld(float partialTicks, long finishTimeNano) {
        renderWorldHook(partialTicks, finishTimeNano);
    }

    public void a(float partialTicks, long finishTimeNano) {
        renderWorldHook(partialTicks, finishTimeNano);
    }

    public void b(float partialTicks, long finishTimeNano) {
        renderWorldHook(partialTicks, finishTimeNano);
    }

    private void renderWorldHook(float partialTicks, long finishTimeNano) {
        super.renderWorld(partialTicks, finishTimeNano);
        dispatchRender3D(partialTicks);
    }

    private void dispatchRender3D(float partialTicks) {
        try {
            if (Minecraft.getMinecraft().theWorld == null || Minecraft.getMinecraft().thePlayer == null) {
                return;
            }
            if (dispatchLogCount < 5) {
                dispatchLogCount++;
                log("Standalone Render3D dispatch #" + dispatchLogCount + " partialTicks=" + partialTicks, null);
            }
            WorldRenderState state = beginWorldOverlayState();
            try {
                RenderFrameGuard.nextStandalone3DFrame();
                restoreWorldCamera(partialTicks);
                prepareWorldOverlayState();
                EventManager.call(new Render3DEvent(partialTicks));
                prepareWorldOverlayState();
                EventManager.call(new gq.yozakura.bridge.forge.RenderWorldLastEvent(partialTicks));
            } finally {
                endWorldOverlayState(state);
            }
        } catch (Throwable throwable) {
            if (!renderFailureLogged) {
                renderFailureLogged = true;
                log("Standalone Render3D dispatch failed", throwable);
            }
        }
    }

    private void restoreWorldCamera(float partialTicks) {
        try {
            Method method = setupCameraTransformMethod;
            if (method == null) {
                method = findSetupCameraTransform();
                setupCameraTransformMethod = method;
            }
            if (method != null) {
                method.invoke(this, partialTicks, 0);
            }
        } catch (Throwable throwable) {
            if (!cameraFailureLogged) {
                cameraFailureLogged = true;
                log("Failed to restore standalone world camera matrix", throwable);
            }
        }
    }

    private static Method findSetupCameraTransform() {
        Method fallback = null;
        Method[] methods = EntityRenderer.class.getDeclaredMethods();
        for (Method method : methods) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 2
                    || parameters[0] != Float.TYPE
                    || parameters[1] != Integer.TYPE
                    || method.getReturnType() != Void.TYPE) {
                continue;
            }
            String name = method.getName();
            if ("setupCameraTransform".equals(name) || "func_78479_a".equals(name) || "a".equals(name)) {
                method.setAccessible(true);
                return method;
            }
            if (fallback == null) {
                fallback = method;
            }
        }
        if (fallback != null) {
            fallback.setAccessible(true);
        }
        return fallback;
    }

    private static WorldRenderState beginWorldOverlayState() {
        WorldRenderState state = new WorldRenderState();
        state.matrixMode = getInteger(GL11.GL_MATRIX_MODE, GL11.GL_MODELVIEW);
        state.program = currentProgram();
        state.activeTexture = getInteger(GL13.GL_ACTIVE_TEXTURE, GL13.GL_TEXTURE0);
        setActiveTexture(GL13.GL_TEXTURE0);
        state.texture0 = getInteger(GL11.GL_TEXTURE_BINDING_2D, 0);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_HINT_BIT
                | GL11.GL_LINE_BIT
                | GL11.GL_POLYGON_BIT
                | GL11.GL_SCISSOR_BIT
                | GL11.GL_STENCIL_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT
                | GL11.GL_TRANSFORM_BIT
                | GL11.GL_VIEWPORT_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        return state;
    }

    private static void prepareWorldOverlayState() {
        useProgram(0);
        setActiveTexture(GL13.GL_TEXTURE0);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GlStateManager.disableLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void endWorldOverlayState(WorldRenderState state) {
        try {
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(state == null ? GL11.GL_MODELVIEW : state.matrixMode);
            GL11.glPopAttrib();
            if (state != null) {
                setActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, state.texture0);
                useProgram(state.program);
                setActiveTexture(state.activeTexture);
            } else {
                useProgram(0);
                setActiveTexture(GL13.GL_TEXTURE0);
            }
            gq.yozakura.engine.render.GLStateManager.syncToCurrent();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        } catch (Throwable throwable) {
            if (!renderFailureLogged) {
                renderFailureLogged = true;
                log("Failed to restore standalone world render state", throwable);
            }
        }
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

    private static void setActiveTexture(int texture) {
        try {
            OpenGlHelper.setActiveTexture(texture);
        } catch (Throwable ignored) {
            try {
                GL13.glActiveTexture(texture);
            } catch (Throwable ignoredAgain) {
            }
        }
    }

    private static void copyState(EntityRenderer source, EntityRenderer target) {
        if (source == null || target == null) {
            return;
        }
        Class<?> type = EntityRenderer.class;
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

    private static final class WorldRenderState {
        private int matrixMode;
        private int program;
        private int activeTexture;
        private int texture0;
    }
}
