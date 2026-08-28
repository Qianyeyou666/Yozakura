package gq.yozakura.module.render;

import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.event.bridge.Render3DEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.render.RenderUtil;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class MiningProgress extends Module {
    private static final double FACE_OFFSET = 0.02D;

    private final Numbers<Double> heightOffset = new Numbers<Double>(
            "Height Offset", "WorldYOffset", 0.0D, -0.5D, 0.5D, 0.02D);
    private final Numbers<Double> scale = new Numbers<Double>(
            "Scale", "Scale", 1.0D, 0.6D, 2.0D, 0.05D);
    private final Option<Boolean> shadow = new Option<Boolean>("Shadow", "Shadow", true);

    private BlockPos displayBlock;
    private float displayProgress;
    private float visibility;

    public MiningProgress() {
        super("MiningProgress", Keyboard.KEY_NONE, ModuleType.Render,
                "Show the current block breaking percentage on the mined block");
        addValues(heightOffset, scale, shadow);
        Chinese = "挖掘进度";
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        renderWorldLabel(event.getPartialTicks());
    }

    @Override
    public void disable() {
        displayBlock = null;
        displayProgress = 0.0F;
        visibility = 0.0F;
    }

    private void renderWorldLabel(float partialTicks) {
        if (!isInGame() || mc.playerController == null) {
            disableDisplay();
            return;
        }

        float progress = MinecraftAccessor.getCurrentBlockDamage(mc.playerController);
        BlockPos currentBlock = MinecraftAccessor.getCurrentBlock(mc.playerController);
        boolean active = MinecraftAccessor.isHittingBlock(mc.playerController)
                && currentBlock != null && progress > 0.0F;
        if (active) {
            displayBlock = currentBlock;
            displayProgress = progress;
        }
        visibility = MiningProgressPresentation.approach(
                visibility, active ? 1.0F : 0.0F, active ? 0.35F : 0.24F);
        if (displayBlock == null || visibility <= 0.01F) {
            if (!active) {
                displayBlock = null;
            }
            return;
        }

        Entity viewer = mc.getRenderViewEntity();
        RenderManager renderManager = mc.getRenderManager();
        if (viewer == null || renderManager == null) {
            disableDisplay();
            return;
        }
        Vec3 viewerEyes = viewer.getPositionEyes(partialTicks);
        MiningProgressPresentation.Anchor anchor = MiningProgressPresentation.anchor(
                displayBlock.getX(), displayBlock.getY(), displayBlock.getZ(),
                viewerEyes.xCoord, viewerEyes.yCoord, viewerEyes.zCoord, FACE_OFFSET);
        drawLabel(anchor, renderManager, viewerEyes);
    }

    private void drawLabel(MiningProgressPresentation.Anchor anchor,
                           RenderManager renderManager, Vec3 viewerEyes) {
        double renderX = anchor.getX() - renderManager.viewerPosX;
        double renderY = anchor.getY() - renderManager.viewerPosY + heightOffset.getValue();
        double renderZ = anchor.getZ() - renderManager.viewerPosZ;
        double distance = viewerEyes.distanceTo(new Vec3(anchor.getX(), anchor.getY(), anchor.getZ()));
        float worldScale = MiningProgressPresentation.worldScale(distance, scale.getValue().floatValue());
        String text = MiningProgressPresentation.formatPercent(displayProgress);
        FontRenderer font = mc.fontRendererObj;
        int textWidth = font.getStringWidth(text);
        int alpha = Math.max(0, Math.min(255, Math.round(255.0F * visibility)));
        int textColor = RenderUtil.applyAlpha(0xFFE4E4E4, alpha);

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GlStateManager.translate(renderX, renderY, renderZ);
            GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
            float pitch = mc.gameSettings.thirdPersonView == 2
                    ? -renderManager.playerViewX : renderManager.playerViewX;
            GlStateManager.rotate(pitch, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-worldScale, -worldScale, worldScale);
            GlStateManager.disableLighting();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableCull();

            int textX = -textWidth / 2;
            int textY = -font.FONT_HEIGHT / 2;
            font.drawString(text, textX, textY, textColor, Boolean.TRUE.equals(shadow.getValue()));
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
            gq.yozakura.engine.render.GLStateManager.syncToCurrent();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void disableDisplay() {
        displayBlock = null;
        displayProgress = 0.0F;
        visibility = 0.0F;
    }
}
