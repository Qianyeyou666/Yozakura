package gq.yozakura.engine.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * The generic UI-shader entry point is also used by rounded panels and glass.
 * Glow-specific alpha rules must not leak into this shared path.
 */
public class ShaderRendererStateContractTest {
    private static final String SHADER_RENDERER_PATH =
            "src/main/java/gq/yozakura/engine/render/ShaderRenderer.java";

    @Test
    public void sharedUiShaderEntryRetainsVanillaAlphaTestAndBlendContract() throws IOException {
        String source = readShaderRenderer();
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
        String source = readShaderRenderer();
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

    @Test
    public void programUseSkipsRedundantGlUseProgramViaActiveProgramCache() throws IOException {
        String source = readShaderRenderer();
        // The cache field must exist and default to "unknown" so the first use()
        // always issues a real glUseProgram.
        assertTrue("activeProgramId tracking field must exist",
                source.contains("private static int activeProgramId = -1;"));
        // Program.use() must compare against the cache before issuing glUseProgram.
        String useMethod = method(source, "private void use() {",
                "private int uniform(String name) {");
        assertTrue("Program.use() must consult activeProgramId before rebinding",
                useMethod.contains("if (activeProgramId != id)"));
        assertTrue("Program.use() must update activeProgramId after binding",
                useMethod.contains("GL20.glUseProgram(id);"));
        assertTrue("Program.use() must update activeProgramId after binding",
                useMethod.contains("activeProgramId = id;"));
        // invalidateProgramCache() must be exposed so external code (e.g. vanilla
        // MC passes that swap programs behind our back) can force a rebind.
        assertTrue("invalidateProgramCache() must be exposed for external program swaps",
                source.contains("public static void invalidateProgramCache()"));
        assertTrue("invalidateProgramCache() must reset the cache to unknown (-1)",
                source.contains("activeProgramId = -1;"));
    }

    @Test
    public void beginShapeBatchIssuesSingleAttribStackFrameAndTextureSave() throws IOException {
        String source = readShaderRenderer();
        String begin = method(source, "public static void beginShapeBatch() {",
                "public static void endShapeBatch() {");

        // Re-entering beginShapeBatch must be a no-op so nested call sites
        // (e.g. drawSharedSurfaces calling into another batched helper) do not
        // push a second attrib frame.
        assertTrue("beginShapeBatch must guard against re-entry when batchMode is already true",
                begin.contains("if (batchMode)"));
        // The batch owner issues exactly ONE pushAttrib for the whole batch.
        assertTrue("beginShapeBatch must push the SHADER_ATTRIB_MASK once for the batch",
                begin.contains("GL11.glPushAttrib(SHADER_ATTRIB_MASK);"));
        // Texture-0 binding must be saved once here so per-draw beginProgram
        // can skip saveTexture0State while batchMode is true.
        assertTrue("beginShapeBatch must save texture-0 state once for the batch",
                begin.contains("batchSavedTextureState = saveTexture0State();"));
        // The batch must apply the shared alpha/blend/depth state up-front so
        // per-draw beginProgram calls remain no-ops for these uniforms.
        assertTrue("beginShapeBatch must apply shared alpha state",
                begin.contains("GlStateManager.enableAlpha();"));
        assertTrue("beginShapeBatch must apply shared blend state",
                begin.contains("GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);"));
        assertTrue("beginShapeBatch must disable depth for the whole batch",
                begin.contains("GlStateManager.disableDepth();"));
    }

    @Test
    public void endShapeBatchRestoresAttribStackAndTextureState() throws IOException {
        String source = readShaderRenderer();
        // endShapeBatch is the last public method on ShaderRenderer; capture
        // from its signature to the close of the class.
        int start = source.indexOf("public static void endShapeBatch() {");
        assertTrue("endShapeBatch must exist", start >= 0);
        int end = source.indexOf("public static void beginOverlayFrame()", start);
        assertTrue("endShapeBatch must be followed by beginOverlayFrame", end > start);
        String endBatch = source.substring(start, end);

        // Re-entering endShapeBatch when no batch is active must be a no-op.
        assertTrue("endShapeBatch must guard against re-entry when batchMode is already false",
                endBatch.contains("if (!batchMode)"));
        // The batch flag must be cleared BEFORE restoring state so a re-entered
        // endProgram during restoration does not skip its own restore.
        int clearFlag = endBatch.indexOf("batchMode = false;");
        int popAttrib = endBatch.indexOf("GL11.glPopAttrib();");
        assertTrue("endShapeBatch must clear batchMode before popping the attrib stack",
                clearFlag >= 0 && popAttrib > clearFlag);
        // Texture-0 binding saved at beginShapeBatch must be restored here.
        assertTrue("endShapeBatch must restore the saved texture-0 binding",
                endBatch.contains("restoreTexture0State(batchSavedTextureState);"));
        // GL state must be re-synced and color reset to white after the batch
        // — matching the per-draw endProgram contract.
        assertTrue("endShapeBatch must re-sync Minecraft's cached GL state",
                endBatch.contains("gq.yozakura.engine.render.GLStateManager.syncToCurrent();"));
        assertTrue("endShapeBatch must reset color to white",
                endBatch.contains("GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);"));
    }

    @Test
    public void beginProgramSkipsPerDrawAttribAndTextureSaveInBatchMode() throws IOException {
        String source = readShaderRenderer();
        String beginProgram = method(source, "private static ShaderState beginProgram(Program program)",
                "private static void endProgram(ShaderState state)");

        // In batch mode, saveTexture0State must be skipped (returns null) so
        // per-draw texture changes are not restored until endShapeBatch.
        assertTrue("beginProgram must skip saveTexture0State when batchMode is true",
                beginProgram.contains("TextureState textureState = batchMode ? null : saveTexture0State();"));
        // glPushAttrib must be skipped per-draw in batch mode — the batch owner
        // already pushed once for the whole batch.
        assertTrue("beginProgram must skip glPushAttrib when batchMode is true",
                beginProgram.contains("if (!batchMode)"));
        assertTrue("beginProgram must push SHADER_ATTRIB_MASK only outside batch mode",
                beginProgram.contains("GL11.glPushAttrib(SHADER_ATTRIB_MASK);"));
    }

    @Test
    public void endProgramSkipsPerDrawAttribRestoreInBatchMode() throws IOException {
        String source = readShaderRenderer();
        String endProgram = method(source, "private static void endProgram(ShaderState state)",
                "private static TextureState saveTexture0State()");

        // glPopAttrib must be skipped per-draw in batch mode — the batch owner
        // pops once at endShapeBatch.
        int popAttribCheck = endProgram.indexOf("if (!batchMode)");
        int popAttrib = endProgram.indexOf("GL11.glPopAttrib();");
        assertTrue("endProgram must guard glPopAttrib with the batchMode flag",
                popAttribCheck >= 0);
        assertTrue("endProgram must skip glPopAttrib when batchMode is true",
                popAttrib > popAttribCheck);
        // Program id tracking must still be restored per-draw so a non-batched
        // draw following a batched draw sees the correct previous program.
        assertTrue("endProgram must restore activeProgramId to the previous program",
                endProgram.contains("activeProgramId = state.previousProgram;"));
        // restoreTexture0State must still be called per-draw — but in batch
        // mode state.textureState is null, so it becomes a no-op (verified by
        // the null check inside restoreTexture0State).
        assertTrue("endProgram must still call restoreTexture0State (no-op when batched)",
                endProgram.contains("restoreTexture0State(state.textureState);"));
    }

    private static String readShaderRenderer() throws IOException {
        return new String(Files.readAllBytes(Paths.get(SHADER_RENDERER_PATH)), StandardCharsets.UTF_8);
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue("missing beginProgram implementation", start >= 0);
        assertTrue("missing endProgram implementation", end > start);
        return source.substring(start, end);
    }
}
