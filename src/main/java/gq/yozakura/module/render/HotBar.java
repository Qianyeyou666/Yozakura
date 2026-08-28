package gq.yozakura.module.render;

import gq.yozakura.bridge.YozakuraEventBridge;
import gq.yozakura.bridge.ForgeEnvironment;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;

/** Rise-style rounded hotbar with a smooth selected-slot indicator. */
public final class HotBar extends Module {
    private static final float WIDTH = 182.0F;
    private static final float HEIGHT = 22.0F;
    private static final float SLOT_SIZE = 20.0F;
    private static final float CORNER_RADIUS = 7.0F;

    private final Numbers<Double> backgroundAlpha = new Numbers<Double>(
            "Background Alpha", "BackgroundAlpha", 170.0D, 0.0D, 255.0D, 1.0D);
    private final Numbers<Double> cornerRadius = new Numbers<Double>(
            "Corner Radius", "CornerRadius", 7.0D, 0.0D, 11.0D, 0.5D);
    private final Option<Boolean> smoothSelection = new Option<Boolean>(
            "Smooth Selection", "SmoothSelection", true);
    private final Option<Boolean> backgroundBlur = new Option<Boolean>(
            "Background Blur", "BackgroundBlur", true);
    private final Option<Boolean> customSelectionColor = new Option<Boolean>(
            "Custom Selection Color", "CustomSelectionColor", false);
    private final Numbers<Double> selectionRed = new Numbers<Double>(
            "Selection Red", "SelectionRed", 233.0D, 0.0D, 255.0D, 1.0D);
    private final Numbers<Double> selectionGreen = new Numbers<Double>(
            "Selection Green", "SelectionGreen", 139.0D, 0.0D, 255.0D, 1.0D);
    private final Numbers<Double> selectionBlue = new Numbers<Double>(
            "Selection Blue", "SelectionBlue", 193.0D, 0.0D, 255.0D, 1.0D);

    private float selectedX;
    private long lastFrameMillis;
    private boolean replaceVanillaThisFrame;
    private float replacementPartialTicks;
    private HotBarForgeListener forgeListener;
    private static HotBar lunarActive;
    private int lunarBackgroundTexture;
    private int lunarTextureWidth;
    private int lunarTextureHeight;
    private float lunarCaptureLeft;
    private float lunarCaptureTop;
    private float lunarCaptureRight;
    private float lunarCaptureBottom;
    private boolean lunarBackgroundCaptured;

    public HotBar() {
        super("HotBar", Keyboard.KEY_NONE, ModuleType.Render, "Render a modern rounded hotbar");
        Chinese = "物品栏美化";
        selectionRed.visibleWhen(() -> Boolean.TRUE.equals(customSelectionColor.getValue()));
        selectionGreen.visibleWhen(() -> Boolean.TRUE.equals(customSelectionColor.getValue()));
        selectionBlue.visibleWhen(() -> Boolean.TRUE.equals(customSelectionColor.getValue()));
        addValues(backgroundAlpha, cornerRadius, smoothSelection, backgroundBlur, customSelectionColor,
                selectionRed, selectionGreen, selectionBlue);
    }

    @Override
    public void enable() {
        selectedX = Float.NaN;
        lastFrameMillis = System.currentTimeMillis();
        replaceVanillaThisFrame = false;
        if (ForgeEnvironment.isForgeAvailable()
                && !gq.yozakura.bridge.StandaloneGuiIngame.isLunarClient()) {
            forgeListener = new HotBarForgeListener(this);
            ForgeEnvironment.register(forgeListener);
        }
        lunarActive = this;
    }

    @Override
    public void disable() {
        selectedX = Float.NaN;
        replaceVanillaThisFrame = false;
        if (lunarActive == this) {
            lunarActive = null;
        }
        disposeLunarBackground();
        if (forgeListener != null) {
            ForgeEnvironment.unregister(forgeListener);
            forgeListener = null;
        }
    }

    @EventTarget
    public void onStandaloneOverlay(gq.yozakura.bridge.forge.RenderGameOverlayEvent.Text event) {
        replaceVanillaThisFrame = false;
        if (mc.currentScreen instanceof GuiContainer) {
            return;
        }
        renderHotBar(event.partialTicks);
    }

    public static void renderLunarFrame(float partialTicks) {
        HotBar hotBar = lunarActive;
        if (hotBar != null) {
            hotBar.renderHotBar(partialTicks);
        }
    }

    public static void captureLunarBackground() {
        HotBar hotBar = lunarActive;
        if (hotBar != null) {
            hotBar.captureLunarBackgroundRegion();
        }
    }

    public static void restoreLunarBackground() {
        HotBar hotBar = lunarActive;
        if (hotBar != null) {
            hotBar.restoreLunarBackgroundRegion();
        }
    }

    private void renderHotBar(float partialTicks) {
        if (!isInGame() || !(mc.getRenderViewEntity() instanceof EntityPlayer)) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        float left = resolution.getScaledWidth() * 0.5F - WIDTH * 0.5F;
        boolean lunarOverlay = gq.yozakura.bridge.StandaloneGuiIngame.isLunarClient();
        float top = resolution.getScaledHeight() - (lunarOverlay ? 24.0F : 26.0F);
        float panelInset = 0.0F;
        float panelLeft = left - panelInset;
        float panelTop = top - panelInset;
        float panelRight = left + WIDTH + panelInset;
        float panelBottom = top + HEIGHT + panelInset;
        float radius = Math.max(0.0F, Math.min((panelBottom - panelTop) * 0.5F,
                cornerRadius.getValue().floatValue() + panelInset));
        float targetX = left + mc.thePlayer.inventory.currentItem * SLOT_SIZE;
        if (Float.isNaN(selectedX)) {
            selectedX = targetX;
        }
        long now = System.currentTimeMillis();
        float delta = Math.max(0.0F, Math.min(0.1F, (now - lastFrameMillis) / 1000.0F));
        lastFrameMillis = now;
        if (Boolean.TRUE.equals(smoothSelection.getValue())) {
            float factor = 1.0F - (float) Math.pow(0.001D, delta);
            selectedX += (targetX - selectedX) * factor;
        } else {
            selectedX = targetX;
        }

        int alpha = Math.max(0, Math.min(255, backgroundAlpha.getValue().intValue()));
        int surface = withAlpha(0xFF111217, alpha);
        int border = withAlpha(0xFFFFFFFF, Math.min(32, alpha));
        HUD.drawNightBloomShadow(panelLeft, panelTop, panelRight, panelBottom, radius, 0.58F);
        if (Boolean.TRUE.equals(backgroundBlur.getValue())) {
            RenderServices.panels().panel(panelLeft, panelTop, panelRight, panelBottom,
                    radius, 0.6F, surface, border);
        } else {
            RenderServices.shapes().rounded(panelLeft, panelTop, panelRight, panelBottom, radius, surface);
        }
        int selectionColor = selectionColor();
        RenderServices.shapes().rounded(selectedX + 1.0F, top + 1.0F,
                selectedX + SLOT_SIZE - 1.0F, top + HEIGHT - 1.0F, Math.max(0.0F, radius - 1.0F),
                withAlpha(selectionColor, Math.min(225, alpha + 40)));

        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            RenderHelper.enableGUIStandardItemLighting();
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
                if (stack == null) {
                    continue;
                }
                float itemX = left + 2.0F + slot * SLOT_SIZE;
                float itemY = top + 3.0F;
                float animation = stack.animationsToGo - partialTicks;
                if (animation > 0.0F) {
                    GlStateManager.pushMatrix();
                    float itemScale = 1.0F + Math.min(1.0F, animation / 5.0F);
                    GlStateManager.translate(itemX + 8.0F, itemY + 8.0F, 0.0F);
                    GlStateManager.scale(1.0F / itemScale, (itemScale + 1.0F) * 0.5F, 1.0F);
                    GlStateManager.translate(-itemX - 8.0F, -itemY - 8.0F, 0.0F);
                }
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, Math.round(itemX), Math.round(itemY));
                mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRendererObj, stack,
                        Math.round(itemX), Math.round(itemY), null);
                if (animation > 0.0F) {
                    GlStateManager.popMatrix();
                }
            }
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private int selectionColor() {
        if (Boolean.TRUE.equals(customSelectionColor.getValue())) {
            int red = selectionRed.getValue().intValue();
            int green = selectionGreen.getValue().intValue();
            int blue = selectionBlue.getValue().intValue();
            return red << 16 | green << 8 | blue;
        }
        return ClickGUI.currentPalette().getAccentPrimary();
    }

    private void captureLunarBackgroundRegion() {
        if (!isInGame() || mc.displayWidth <= 0 || mc.displayHeight <= 0) {
            lunarBackgroundCaptured = false;
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        int scale = Math.max(1, resolution.getScaleFactor());
        lunarCaptureLeft = Math.max(0.0F, resolution.getScaledWidth() * 0.5F - 98.0F);
        lunarCaptureRight = Math.min(resolution.getScaledWidth(), resolution.getScaledWidth() * 0.5F + 98.0F);
        lunarCaptureTop = Math.max(0.0F, resolution.getScaledHeight() - 28.0F);
        lunarCaptureBottom = resolution.getScaledHeight();

        int sourceX = Math.max(0, Math.round(lunarCaptureLeft * scale));
        int sourceY = Math.max(0, mc.displayHeight - Math.round(lunarCaptureBottom * scale));
        int width = Math.min(mc.displayWidth - sourceX,
                Math.max(1, Math.round((lunarCaptureRight - lunarCaptureLeft) * scale)));
        int height = Math.min(mc.displayHeight - sourceY,
                Math.max(1, Math.round((lunarCaptureBottom - lunarCaptureTop) * scale)));
        if (width <= 0 || height <= 0) {
            lunarBackgroundCaptured = false;
            return;
        }

        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        OpenGlHelper.setActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            ensureLunarBackgroundTexture(width, height);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
                    sourceX, sourceY, width, height);
            lunarBackgroundCaptured = true;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            OpenGlHelper.setActiveTexture(activeTexture);
            gq.yozakura.engine.render.GLStateManager.syncToCurrent();
        }
    }

    private void restoreLunarBackgroundRegion() {
        if (!lunarBackgroundCaptured || lunarBackgroundTexture == 0) {
            return;
        }
        lunarBackgroundCaptured = false;
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        OpenGlHelper.setActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL20.glUseProgram(0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, lunarBackgroundTexture);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0F, 1.0F);
            GL11.glVertex2f(lunarCaptureLeft, lunarCaptureTop);
            GL11.glTexCoord2f(0.0F, 0.0F);
            GL11.glVertex2f(lunarCaptureLeft, lunarCaptureBottom);
            GL11.glTexCoord2f(1.0F, 0.0F);
            GL11.glVertex2f(lunarCaptureRight, lunarCaptureBottom);
            GL11.glTexCoord2f(1.0F, 1.0F);
            GL11.glVertex2f(lunarCaptureRight, lunarCaptureTop);
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
            OpenGlHelper.setActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL20.glUseProgram(program);
            OpenGlHelper.setActiveTexture(activeTexture);
            gq.yozakura.engine.render.GLStateManager.syncToCurrent();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void ensureLunarBackgroundTexture(int width, int height) {
        if (lunarBackgroundTexture == 0) {
            lunarBackgroundTexture = GL11.glGenTextures();
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, lunarBackgroundTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        if (lunarTextureWidth != width || lunarTextureHeight != height) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, width, height, 0,
                    GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            lunarTextureWidth = width;
            lunarTextureHeight = height;
        }
    }

    private void disposeLunarBackground() {
        lunarBackgroundCaptured = false;
        lunarTextureWidth = 0;
        lunarTextureHeight = 0;
        if (lunarBackgroundTexture != 0) {
            GL11.glDeleteTextures(lunarBackgroundTexture);
            lunarBackgroundTexture = 0;
        }
    }

    /** Kept separate so Lunar never resolves Forge-only event parameter types while registering this module. */
    private static final class HotBarForgeListener {
        private final HotBar owner;

        private HotBarForgeListener(HotBar owner) {
            this.owner = owner;
        }

        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(
                priority = net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
        public void onVanillaHotBar(net.minecraftforge.client.event.RenderGameOverlayEvent.Pre event) {
            if (event.type != net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.HOTBAR) {
                return;
            }
            owner.replaceVanillaThisFrame = true;
            owner.replacementPartialTicks = event.partialTicks;
            event.setCanceled(true);
        }

        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(
                priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
        public void onOverlay(net.minecraftforge.client.event.RenderGameOverlayEvent.Text event) {
            if (mc.currentScreen instanceof GuiContainer) {
                owner.replaceVanillaThisFrame = false;
                return;
            }
            if (owner.replaceVanillaThisFrame) {
                owner.replaceVanillaThisFrame = false;
                owner.renderHotBar(owner.replacementPartialTicks);
            } else if (!YozakuraEventBridge.hasRenderedOverlayThisFrame()) {
                owner.renderHotBar(event.partialTicks);
            }
        }

        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(
                priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
        public void onContainerPost(net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post event) {
            if (event.gui instanceof GuiContainer && event.gui == mc.currentScreen) {
                owner.renderHotBar(event.renderPartialTicks);
            }
        }
    }
}
