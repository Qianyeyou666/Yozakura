package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class TargetEspGlStateContractTest {
    @Test
    public void shaderSetupIsInsideTheRenderStateTransaction() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/TargetESP.java")), StandardCharsets.UTF_8);
        String drawTarget = source.substring(source.indexOf("    private void drawTarget("),
                source.indexOf("    private static int currentProgram()"));

        int stateScope = drawTarget.indexOf("try {");
        int pushAttrib = drawTarget.indexOf("GL11.glPushAttrib(");
        int shaderBegin = drawTarget.indexOf("TargetShader.begin(");

        assertTrue("TargetESP must enter a try/finally scope before mutating GL state", stateScope >= 0);
        assertTrue("GL attrib setup must be protected even when shader setup fails", stateScope < pushAttrib);
        assertTrue("TargetShader.begin must be protected by the render-state transaction", pushAttrib < shaderBegin);
    }

    @Test
    public void cleanupRestoresProgramThenRawGlStateAndMinecraftCache() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/TargetESP.java")), StandardCharsets.UTF_8);

        int restoreMethod = source.indexOf("private static void restoreTargetRenderState(");
        int nextMethod = source.indexOf("    private int interpolate(", restoreMethod);
        String cleanup = source.substring(restoreMethod, nextMethod);

        int restoreProgram = cleanup.indexOf("useProgram(previousProgram);");
        int popMatrix = cleanup.indexOf("GL11.glPopMatrix();");
        int popAttrib = cleanup.indexOf("GL11.glPopAttrib();");
        int syncCache = cleanup.indexOf("GLStateManager.syncToCurrent();");

        assertTrue("program restoration must be unconditional", restoreProgram >= 0);
        assertTrue("matrix restoration must follow program restoration", restoreProgram < popMatrix);
        assertTrue("attrib restoration must follow matrix restoration", popMatrix < popAttrib);
        assertTrue("Minecraft's cached GL state must be synchronized after raw attrib restoration", popAttrib < syncCache);
    }
}
