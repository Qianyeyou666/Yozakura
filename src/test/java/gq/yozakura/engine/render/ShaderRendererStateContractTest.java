package gq.yozakura.engine.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The generic UI-shader entry point is also used by rounded panels and glass.
 * Glow-specific alpha rules must not leak into this shared path.
 */
public class ShaderRendererStateContractTest {
    @Test
    public void sharedUiShaderEntryRetainsVanillaAlphaTestAndBlendContract() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/engine/render/ShaderRenderer.java")), StandardCharsets.UTF_8);
        String beginProgram = method(source, "private static ShaderState beginProgram(Program program)",
                "private static void endProgram(ShaderState state)");

        assertTrue("rounded UI shaders need alpha testing for their soft edge cutoff",
                beginProgram.contains("GlStateManager.enableAlpha();"));
        assertTrue("normal UI shaders must preserve the legacy alpha blend destination contract",
                beginProgram.contains("GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);"));
        assertFalse("the generic UI path must not inherit the glow mask alpha state",
                beginProgram.contains("GlStateManager.disableAlpha();"));
    }

    @Test
    public void shaderExitResynchronizesCachedStateAfterRawAttribRestore() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/engine/render/ShaderRenderer.java")), StandardCharsets.UTF_8);
        String endProgram = method(source, "private static void endProgram(ShaderState state)",
                "private static TextureState saveTexture0State()");

        int popAttrib = endProgram.indexOf("GL11.glPopAttrib();");
        int restoreTexture = endProgram.indexOf("restoreTexture0State(state.textureState);");
        int syncState = endProgram.indexOf(
                "gq.yozakura.engine.render.GLStateManager.syncToCurrent();");
        int resetColor = endProgram.indexOf("GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);");

        assertTrue("raw GL attributes must be restored before texture and cache state", popAttrib >= 0);
        assertTrue("texture state must be restored after the raw attribute stack", restoreTexture > popAttrib);
        assertTrue("Minecraft's cached GL state must be synchronized after texture restoration",
                syncState > restoreTexture);
        assertTrue("the final white color reset must run after state synchronization", resetColor > syncState);
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue("missing beginProgram implementation", start >= 0);
        assertTrue("missing endProgram implementation", end > start);
        return source.substring(start, end);
    }
}
