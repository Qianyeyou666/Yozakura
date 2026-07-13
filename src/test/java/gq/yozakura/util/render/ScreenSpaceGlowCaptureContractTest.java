package gq.yozakura.util.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScreenSpaceGlowCaptureContractTest {
    @Test
    public void entityMaskUsesNativeOutlineCaptureWithoutNameTagsOrDebugHitboxes() throws IOException {
        String source = source();

        assertTrue(source.contains("renderManager.setRenderOutlines(true);"));
        assertTrue(source.contains("renderManager.renderEntityStatic(entity, partialTicks, true);"));
        assertTrue(source.contains("renderManager.setRenderOutlines(previousOutlines);"));
    }

    @Test
    public void blockMaskUsesFilledAabbGeometryRatherThanASelectionWireframe() throws IOException {
        String source = source();

        assertTrue(source.contains("drawSolidAabb("));
        assertTrue(source.contains("GL11.glBegin(GL11.GL_QUADS);"));
        assertFalse(source.contains("drawSelectionBoundingBox"));
    }

    @Test
    public void rendererPreservesTheRequiredFramebufferProgramAndTextureBindings() throws IOException {
        String source = source();

        assertTrue(source.contains("EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT"));
        assertTrue(source.contains("GL20.GL_CURRENT_PROGRAM"));
        assertFalse(source.contains("PRESERVED_TEXTURE_UNIT"));
        assertFalse(source.contains("texture16"));
        assertTrue(source.contains("try {\n            if (matricesPushed)"));
        assertTrue(source.contains("GLStateManager.syncToCurrent();"));
    }

    @Test
    public void viewportStateBufferMeetsLwjglGetIntegerCapacityRequirement() throws IOException {
        String source = source();

        assertTrue(source.contains("BufferUtils.createIntBuffer(16)"));
        assertFalse(source.contains("BufferUtils.createIntBuffer(4)"));
    }

    @Test
    public void textureMatrixTransactionUsesOneKnownTextureUnit() throws IOException {
        String source = source();

        assertTextureMatrixUsesMaskUnit(method(source, "private static void pushMatrices()",
                "private static void loadIdentityMatrices()"));
        assertTextureMatrixUsesMaskUnit(method(source, "private static void popMatrices(",
                "private static void restoreTransaction("));
        assertTextureMatrixUsesMaskUnit(method(source, "private static void loadIdentityMatrices()",
                "private static void popMatrices("));
    }

    @Test
    public void outlineStateLookupDoesNotRequireForgeReflectionHelper() throws IOException {
        String source = source();

        assertFalse(source.contains("net.minecraftforge.fml.relauncher.ReflectionHelper"));
        assertTrue(source.contains("java.lang.reflect.Field"));
        assertTrue(source.contains("resolveRenderOutlinesField"));
        assertTrue(source.contains("field_178639_r"));
    }

    @Test
    public void storageOnlyBatchesSkipEntityOutlineStateLookup() throws IOException {
        String source = source();
        String entityMask = method(source, "private void renderEntityMasks(",
                "private void restoreRendererOutlineFlags(");

        assertTrue(entityMask.contains("if (entities.isEmpty())"));
    }

    private static void assertTextureMatrixUsesMaskUnit(String method) {
        int textureMode = method.indexOf("GL11.glMatrixMode(GL11.GL_TEXTURE);");
        int textureUnit = method.lastIndexOf("setActiveTexture(MASK_TEXTURE_UNIT);", textureMode);

        assertTrue("texture matrix work must select a stable texture unit", textureUnit >= 0);
        assertTrue("texture unit selection must happen before texture matrix work", textureUnit < textureMode);
    }

    private static String method(String source, String start, String end) {
        int begin = source.indexOf(start);
        int finish = source.indexOf(end, begin);
        return source.substring(begin, finish);
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/util/render/ScreenSpaceGlowRenderer.java")),
                StandardCharsets.UTF_8);
    }
}
