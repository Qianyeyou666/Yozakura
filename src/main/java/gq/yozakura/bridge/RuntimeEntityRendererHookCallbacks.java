package gq.yozakura.bridge;

/**
 * Public, Minecraft-signature-free boundary for generated renderer subclasses.
 * Keeping the generated method descriptors to primitives/Object lets the class
 * be verified before the Minecraft renderer implementation is linked.
 */
public final class RuntimeEntityRendererHookCallbacks {
    private RuntimeEntityRendererHookCallbacks() {
    }

    public static void beginRuntimeRenderWorld(float partialTicks) {
        StandaloneEntityRenderer.beginRuntimeRenderWorld(partialTicks);
    }

    public static void finishRuntimeRenderWorld(Object renderer, float partialTicks) {
        StandaloneEntityRenderer.finishRuntimeRenderWorld(renderer, partialTicks);
    }

    public static void abortRuntimeRenderWorld() {
        StandaloneEntityRenderer.abortRuntimeRenderWorld();
    }

    public static void dispatchRuntimeMouseOver(float partialTicks) {
        StandaloneEntityRenderer.dispatchRuntimeMouseOver(partialTicks);
    }

    public static Object beginRuntimeFrame(float partialTicks) {
        return StandaloneEntityRenderer.beginRuntimeFrame(partialTicks);
    }

    public static void finishRuntimeFrame(Object renderer, Object frameState, float partialTicks) {
        StandaloneEntityRenderer.finishRuntimeFrame(renderer, frameState, partialTicks);
    }

    public static void abortRuntimeFrame(Object frameState) {
        StandaloneEntityRenderer.abortRuntimeFrame(frameState);
    }

}
