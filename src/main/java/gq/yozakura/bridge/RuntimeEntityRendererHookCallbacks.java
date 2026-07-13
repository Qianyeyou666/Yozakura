package gq.yozakura.bridge;

/**
 * Public, Minecraft-signature-free boundary for generated renderer subclasses.
 * Keeping the generated method descriptors to primitives/Object lets the class
 * be verified before the Minecraft renderer implementation is linked.
 */
public final class RuntimeEntityRendererHookCallbacks {
    private RuntimeEntityRendererHookCallbacks() {
    }

    public static void beginRuntimeRenderWorld() {
        StandaloneEntityRenderer.beginRuntimeRenderWorld();
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
}
