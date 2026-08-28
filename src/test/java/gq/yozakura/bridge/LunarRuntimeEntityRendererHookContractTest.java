package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Contract coverage for the Lunar renderer hook. The generated type must extend
 * the live renderer class so Lunar's renderer overrides remain in the call path.
 */
public class LunarRuntimeEntityRendererHookContractTest {
    @Test
    public void runtimeSubclassHookIsGeneratedInTheRemapLoaderAndOverridesAllBridgePoints() throws Exception {
        VanillaRemapClassLoader loader = new VanillaRemapClassLoader(new URL[0],
                VanillaRemapClassLoader.class.getClassLoader(), true);
        Method factory = VanillaRemapClassLoader.class.getMethod("defineRuntimeEntityRendererHook", Class.class);

        Class<?> hook = (Class<?>) factory.invoke(loader, PublicRendererShape.class);

        assertSame("The runtime hook must be defined beside the remapped standalone classes", loader,
                hook.getClassLoader());
        assertEquals("The generated hook must retain the actual Lunar renderer as its direct superclass",
                PublicRendererShape.class, hook.getSuperclass());
        assertEquals("The generated hook must own the renderWorld interception point", hook,
                hook.getMethod("renderWorld", Float.TYPE, Long.TYPE).getDeclaringClass());
        assertEquals("The generated hook must own the getMouseOver interception point", hook,
                hook.getMethod("getMouseOver", Float.TYPE).getDeclaringClass());
        assertEquals("The generated hook must own the complete frame interception point", hook,
                hook.getMethod("updateCameraAndRender", Float.TYPE, Long.TYPE).getDeclaringClass());
        assertTrue("The constructor-free generated hook must support Unsafe allocation",
                PublicRendererShape.class.isInstance(allocateWithoutConstructor(hook)));
        assertSame("The remap loader must reuse its hook class for the same runtime renderer type", hook,
                factory.invoke(loader, PublicRendererShape.class));
    }

    @Test
    public void nonSubclassableRuntimeRendererFailsExplicitly() throws Exception {
        VanillaRemapClassLoader loader = new VanillaRemapClassLoader(new URL[0],
                VanillaRemapClassLoader.class.getClassLoader(), true);
        Method factory = VanillaRemapClassLoader.class.getMethod("defineRuntimeEntityRendererHook", Class.class);

        try {
            factory.invoke(loader, FinalRendererShape.class);
            fail("A final runtime renderer cannot be silently replaced by a vanilla wrapper");
        } catch (InvocationTargetException exception) {
            assertTrue("The rejection must state that the runtime type cannot be subclassed",
                    exception.getCause() instanceof IllegalArgumentException
                            && exception.getCause().getMessage().contains("not subclassable"));
        }
    }

    @Test
    public void generatedMethodsInvokeTheRuntimeOverrideAndBalanceRenderCallbacks() throws Exception {
        Method generator = RuntimeEntityRendererHookGenerator.class.getDeclaredMethod("generate",
                String.class, Class.class, Class.class);
        generator.setAccessible(true);
        byte[] bytes = (byte[]) generator.invoke(null,
                "gq.yozakura.bridge.generated.TestRuntimeEntityRendererHook",
                PublicRendererShape.class, TestCallbacks.class);
        Class<?> hook = new DefiningClassLoader(PublicRendererShape.class.getClassLoader())
                .define("gq.yozakura.bridge.generated.TestRuntimeEntityRendererHook", bytes);
        Object instance = allocateWithoutConstructor(hook);

        PublicRendererShape.reset();
        TestCallbacks.reset();
        hook.getMethod("renderWorld", Float.TYPE, Long.TYPE).invoke(instance, 0.5F, 42L);
        hook.getMethod("getMouseOver", Float.TYPE).invoke(instance, 0.5F);
        hook.getMethod("updateCameraAndRender", Float.TYPE, Long.TYPE).invoke(instance, 0.5F, 42L);

        assertEquals("invokespecial must execute the runtime renderer's renderWorld override", 1,
                PublicRendererShape.renderWorldCalls);
        assertEquals("The generated render hook must prepare exactly once", 1, TestCallbacks.beginCalls);
        assertEquals("The pre-world callback must receive the renderer's interpolation time",
                0.5F, TestCallbacks.beginPartialTicks, 0.0001F);
        assertEquals("The generated render hook must complete normally after the runtime override", 1,
                TestCallbacks.finishCalls);
        assertEquals("The abort path must not run after a normal runtime override", 0, TestCallbacks.abortCalls);
        assertSame("The generated hook instance must be passed to the finish callback", instance,
                TestCallbacks.finishedRenderer);
        assertEquals("invokespecial must execute the runtime renderer's getMouseOver override", 1,
                PublicRendererShape.mouseOverCalls);
        assertEquals("The generated mouse hook must dispatch after the runtime override", 1,
                TestCallbacks.mouseOverCalls);
        assertEquals("The generated frame hook must prepare exactly once", 1, TestCallbacks.frameBeginCalls);
        assertEquals("invokespecial must execute the runtime renderer's complete frame override", 1,
                PublicRendererShape.frameCalls);
        assertEquals("The generated frame hook must finish exactly once", 1, TestCallbacks.frameFinishCalls);
        assertEquals("The generated frame hook must preserve its restoration token", TestCallbacks.frameToken,
                TestCallbacks.finishedFrameToken);

        PublicRendererShape.reset();
        PublicRendererShape.throwFromRenderWorld = true;
        TestCallbacks.reset();
        try {
            hook.getMethod("renderWorld", Float.TYPE, Long.TYPE).invoke(instance, 0.5F, 42L);
            fail("The generated hook must preserve an exception from the runtime renderer override");
        } catch (InvocationTargetException exception) {
            assertTrue(exception.getCause() instanceof IllegalStateException);
        }
        assertEquals("The generated hook must still begin before a throwing runtime override", 1,
                TestCallbacks.beginCalls);
        assertEquals("The generated hook must restore state through abort when the runtime override throws", 1,
                TestCallbacks.abortCalls);
        assertEquals("The normal finish callback must not run after a throwing runtime override", 0,
                TestCallbacks.finishCalls);
    }

    @Test
    public void sourceUsesTheRuntimeSubclassInsteadOfSkippingLunarRenderHooks() throws IOException {
        String entityRenderer = source("src/main/java/gq/yozakura/bridge/StandaloneEntityRenderer.java");
        String remapLoader = source("src/main/java/gq/yozakura/bridge/VanillaRemapClassLoader.java");
        String generator = source("src/main/java/gq/yozakura/bridge/RuntimeEntityRendererHookGenerator.java");

        assertTrue("Custom renderers must request a generated subclass from the active remap loader",
                entityRenderer.contains("defineRuntimeEntityRendererHook(current.getClass())"));
        assertTrue("A constructor-bypassing instance is required because Lunar renderers have unknown constructors",
                entityRenderer.contains("allocateInstance"));
        assertTrue("Every renderer layer, including Lunar's private fields, must be copied",
                entityRenderer.contains("Class<?> type = source.getClass()"));
        assertTrue("The remap loader must generate a public subclass with invokespecial super calls",
                remapLoader.contains("defineRuntimeEntityRendererHook")
                        && generator.contains("writeInvokeSpecial")
                        && generator.contains("renderWorld")
                        && generator.contains("getMouseOver")
                        && generator.contains("updateCameraAndRender"));
        assertTrue("A custom renderer may not be silently skipped after the dynamic hook is available",
                !entityRenderer.contains("current.getClass() != EntityRenderer.class"));
        assertTrue("The world-only framebuffer must be captured before Lunar composites its native HotBar",
                entityRenderer.contains("HotBar.captureLunarBackground()"));
        assertTrue("The captured world region must replace Lunar's HotBar before custom HUD dispatch",
                entityRenderer.contains("HotBar.restoreLunarBackground()"));
        assertTrue("All non-container screens must keep the complete HUD instead of using the HotBar-only path",
                entityRenderer.contains("!(minecraft.currentScreen instanceof GuiContainer)"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static Object allocateWithoutConstructor(Class<?> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field field = unsafeType.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        return unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
    }

    /**
     * The test runtime deliberately has no Minecraft classes. This public shape
     * exercises the generated class-file and invokespecial descriptors directly.
     */
    public static class PublicRendererShape {
        private static int renderWorldCalls;
        private static int mouseOverCalls;
        private static int frameCalls;
        private static boolean throwFromRenderWorld;

        public void renderWorld(float partialTicks, long finishTimeNano) {
            renderWorldCalls++;
            if (throwFromRenderWorld) {
                throw new IllegalStateException("expected test renderer failure");
            }
        }

        public void getMouseOver(float partialTicks) {
            mouseOverCalls++;
        }

        public void updateCameraAndRender(float partialTicks, long nanoTime) {
            frameCalls++;
        }

        private static void reset() {
            renderWorldCalls = 0;
            mouseOverCalls = 0;
            frameCalls = 0;
            throwFromRenderWorld = false;
        }
    }

    public static final class FinalRendererShape {
        public void renderWorld(float partialTicks, long finishTimeNano) {
        }

        public void getMouseOver(float partialTicks) {
        }
    }

    public static final class TestCallbacks {
        private static int beginCalls;
        private static int finishCalls;
        private static int abortCalls;
        private static int mouseOverCalls;
        private static int frameBeginCalls;
        private static int frameFinishCalls;
        private static float beginPartialTicks;
        private static Object finishedRenderer;
        private static Object finishedFrameToken;
        private static final Object frameToken = new Object();

        public static void beginRuntimeRenderWorld(float partialTicks) {
            beginCalls++;
            beginPartialTicks = partialTicks;
        }

        public static void finishRuntimeRenderWorld(Object renderer, float partialTicks) {
            finishCalls++;
            finishedRenderer = renderer;
        }

        public static void abortRuntimeRenderWorld() {
            abortCalls++;
        }

        public static void dispatchRuntimeMouseOver(float partialTicks) {
            mouseOverCalls++;
        }

        public static Object beginRuntimeFrame(float partialTicks) {
            frameBeginCalls++;
            return frameToken;
        }

        public static void finishRuntimeFrame(Object renderer, Object token, float partialTicks) {
            frameFinishCalls++;
            finishedFrameToken = token;
        }

        public static void abortRuntimeFrame(Object token) {
            finishedFrameToken = token;
        }

        private static void reset() {
            beginCalls = 0;
            finishCalls = 0;
            abortCalls = 0;
            mouseOverCalls = 0;
            frameBeginCalls = 0;
            frameFinishCalls = 0;
            beginPartialTicks = -1.0F;
            finishedRenderer = null;
            finishedFrameToken = null;
        }
    }

    private static final class DefiningClassLoader extends ClassLoader {
        private DefiningClassLoader(ClassLoader parent) {
            super(parent);
        }

        private Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
