package gq.yozakura.bridge;

import gq.yozakura.event.bridge.MouseOverEvent;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class StandaloneEntityRenderer extends EntityRenderer {
    private static boolean installFailureLogged;
    private static boolean renderFailureLogged;
    private static boolean cameraFailureLogged;
    private static boolean installLogged;
    private static boolean uninstallFailureLogged;
    private static int dispatchLogCount;
    private static Method setupCameraTransformMethod;
    private static Object unsafe;
    private static Method unsafeAllocateInstance;
    private static EntityRenderer originalRenderer;
    private static EntityRenderer installedRenderer;

    public StandaloneEntityRenderer(Minecraft minecraft) {
        super(minecraft, minecraft.getResourceManager());
    }

    public static void install(Minecraft minecraft) {
        if (minecraft == null) {
            throw new IllegalStateException("Cannot install standalone EntityRenderer hook without Minecraft");
        }
        EntityRenderer current = minecraft.entityRenderer;
        if (current == installedRenderer) {
            return;
        }
        if (current == null) {
            throw new IllegalStateException("Cannot install standalone EntityRenderer hook: no EntityRenderer is available");
        }
        EntityRenderer hook = null;
        try {
            if (current.getClass() == EntityRenderer.class) {
                hook = new StandaloneEntityRenderer(minecraft);
            } else {
                hook = createRuntimeSubclassHook(current);
            }
            copyState(current, hook);
            originalRenderer = current;
            installedRenderer = hook;
            minecraft.entityRenderer = hook;
            if (!installLogged) {
                installLogged = true;
                log("Standalone entity renderer hook installed: old="
                        + current.getClass().getName()
                        + ", new=" + hook.getClass().getName(), null);
            }
        } catch (Throwable throwable) {
            if (minecraft.entityRenderer == hook) {
                minecraft.entityRenderer = current;
            }
            installedRenderer = null;
            originalRenderer = null;
            throw reportInstallFailure(current, throwable);
        }
    }

    public static void uninstall(Minecraft minecraft) {
        EntityRenderer installed = installedRenderer;
        EntityRenderer original = originalRenderer;
        try {
            if (minecraft != null && installed != null && minecraft.entityRenderer == installed) {
                minecraft.entityRenderer = original;
                log("Standalone entity renderer hook removed", null);
            }
        } catch (Throwable throwable) {
            if (!uninstallFailureLogged) {
                uninstallFailureLogged = true;
                log("Failed to remove standalone entity renderer hook", throwable);
            }
        } finally {
            installedRenderer = null;
            originalRenderer = null;
        }
    }

    @Override
    public void renderWorld(float partialTicks, long finishTimeNano) {
        renderWorldHook(partialTicks, finishTimeNano);
    }

    @Override
    public void getMouseOver(float partialTicks) {
        super.getMouseOver(partialTicks);
        dispatchRuntimeMouseOver(partialTicks);
    }

    private void renderWorldHook(float partialTicks, long finishTimeNano) {
        beginRuntimeRenderWorld();
        try {
            super.renderWorld(partialTicks, finishTimeNano);
        } finally {
            abortRuntimeRenderWorld();
        }
        dispatchRuntimeRenderEvents(this, partialTicks);
    }

    /** Called by the generated Lunar renderer subclass before invokespecial. */
    public static void beginRuntimeRenderWorld() {
        boolean rotationPrepared = false;
        try {
            MovementInputBridge.prepareRotationForRender();
            rotationPrepared = true;
            StandaloneLivingRendererBridge.install(Minecraft.getMinecraft());
        } catch (Throwable throwable) {
            if (rotationPrepared) {
                MovementInputBridge.restoreRotationForRender();
            }
            throw throwable;
        }
    }

    /** Called after the generated subclass returns normally from invokespecial. */
    public static void finishRuntimeRenderWorld(Object renderer, float partialTicks) {
        MovementInputBridge.restoreRotationForRender();
        if (!(renderer instanceof EntityRenderer)) {
            throw new IllegalArgumentException("Generated renderer hook is not an EntityRenderer: "
                    + (renderer == null ? "null" : renderer.getClass().getName()));
        }
        dispatchRuntimeRenderEvents((EntityRenderer) renderer, partialTicks);
    }

    /** Called from the generated exception handler if the client renderer throws. */
    public static void abortRuntimeRenderWorld() {
        MovementInputBridge.restoreRotationForRender();
    }

    /** Called by the generated Lunar renderer subclass after invokespecial. */
    public static void dispatchRuntimeMouseOver(float partialTicks) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld != null && minecraft.thePlayer != null) {
            EventManager.call(new MouseOverEvent(partialTicks));
        }
    }

    private static void dispatchRuntimeRenderEvents(EntityRenderer renderer, float partialTicks) {
        dispatchRender3D(renderer, partialTicks);
        // On Lunar Client, StandaloneGuiIngame.install() is a no-op to preserve
        // Lunar's HUD Caching. Dispatch 2D overlay here instead.
        if (StandaloneGuiIngame.isLunarClient()) {
            StandaloneGuiIngame.dispatchRender2DExternally(partialTicks);
        }
    }

    private static void dispatchRender3D(EntityRenderer renderer, float partialTicks) {
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
                restoreWorldCamera(renderer, partialTicks);
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

    private static void restoreWorldCamera(EntityRenderer renderer, float partialTicks) {
        try {
            Method method = setupCameraTransformMethod;
            if (method == null) {
                method = findSetupCameraTransform();
                setupCameraTransformMethod = method;
            }
            if (method != null) {
                method.invoke(renderer, partialTicks, 0);
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

    private static EntityRenderer createRuntimeSubclassHook(EntityRenderer current) throws Exception {
        ClassLoader loader = StandaloneEntityRenderer.class.getClassLoader();
        if (!(loader instanceof VanillaRemapClassLoader)) {
            throw new IllegalStateException("Cannot hook runtime EntityRenderer " + current.getClass().getName()
                    + ": the standalone bridge was not loaded by VanillaRemapClassLoader");
        }
        Class<?> generatedType = ((VanillaRemapClassLoader) loader)
                .defineRuntimeEntityRendererHook(current.getClass());
        Object instance = allocateInstance(generatedType);
        if (!(instance instanceof EntityRenderer)) {
            throw new IllegalStateException("Generated runtime renderer hook does not extend EntityRenderer: "
                    + generatedType.getName());
        }
        return (EntityRenderer) instance;
    }

    private static Object allocateInstance(Class<?> type) throws Exception {
        Object unsafeInstance = unsafe;
        Method allocate = unsafeAllocateInstance;
        if (unsafeInstance == null || allocate == null) {
            synchronized (StandaloneEntityRenderer.class) {
                unsafeInstance = unsafe;
                allocate = unsafeAllocateInstance;
                if (unsafeInstance == null || allocate == null) {
                    Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
                    Field field = unsafeType.getDeclaredField("theUnsafe");
                    field.setAccessible(true);
                    unsafeInstance = field.get(null);
                    allocate = unsafeType.getMethod("allocateInstance", Class.class);
                    unsafe = unsafeInstance;
                    unsafeAllocateInstance = allocate;
                }
            }
        }
        try {
            return allocate.invoke(unsafeInstance, type);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Unsafe.allocateInstance failed for " + type.getName(), cause);
        }
    }

    private static void copyState(Object source, Object target) {
        if (source == null || target == null) {
            throw new IllegalArgumentException("Cannot copy EntityRenderer state from or to null");
        }
        Class<?> type = source.getClass();
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
                } catch (Throwable throwable) {
                    throw new IllegalStateException("Unable to copy runtime EntityRenderer field "
                            + type.getName() + '.' + field.getName(), throwable);
                }
            }
            type = type.getSuperclass();
        }
    }

    private static IllegalStateException reportInstallFailure(EntityRenderer current, Throwable throwable) {
        IllegalStateException failure = new IllegalStateException("Failed to install standalone EntityRenderer hook for "
                + current.getClass().getName() + "; Render2D/Render3D bridge is unavailable", throwable);
        if (!installFailureLogged) {
            installFailureLogged = true;
            log(failure.getMessage(), failure);
        }
        return failure;
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
