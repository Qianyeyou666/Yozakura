package gq.yozakura.module.render;

import gq.yozakura.bridge.ForgeEnvironment;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Adds the same soft scale/fade motion used by the Rise TargetHUD to inventories. */
public final class InventoryAnimation extends Module {
    private static InventoryAnimation lunarActive;
    private final Numbers<Double> duration = new Numbers<Double>(
            "Duration", "Duration", 180.0D, 80.0D, 500.0D, 5.0D);
    private final Numbers<Double> startScale = new Numbers<Double>(
            "Start Scale", "StartScale", 0.88D, 0.70D, 1.0D, 0.01D);
    private final Option<Boolean> fade = new Option<Boolean>("Fade", "Fade", true);
    private final Numbers<Double> backgroundAlpha = new Numbers<Double>(
            "Background Alpha", "BackgroundAlpha", 210.0D, 0.0D, 255.0D, 1.0D);
    private final Numbers<Double> cornerRadius = new Numbers<Double>(
            "Corner Radius", "CornerRadius", 6.0D, 0.0D, 14.0D, 0.5D);
    private final Option<Boolean> backgroundBlur = new Option<Boolean>(
            "Background Blur", "BackgroundBlur", true);

    private final RiseTargetHudAnimation motion = new RiseTargetHudAnimation(
            RiseTargetHudAnimation.Easing.EASE_OUT_CUBIC, 180L);
    private GuiContainer closingScreen;
    private long closingStartedAt;
    private InventoryForgeListener forgeListener;
    private GuiContainer lunarScreen;
    private boolean lunarClosing;

    public InventoryAnimation() {
        super("InventoryAnimation", Keyboard.KEY_NONE, ModuleType.Render,
                "Animate inventory opening and closing");
        Chinese = "背包动画";
        addValues(duration, startScale, fade, backgroundAlpha, cornerRadius, backgroundBlur);
    }

    @Override
    public void enable() {
        closingScreen = null;
        closingStartedAt = 0L;
        lunarScreen = null;
        lunarClosing = false;
        motion.setDuration(duration.getValue().longValue());
        if (isPlayerInventory(mc.currentScreen)) {
            motion.snap(1.0D);
        } else {
            motion.snap(0.0D);
        }
        if (ForgeEnvironment.isForgeAvailable()
                && !gq.yozakura.bridge.StandaloneGuiIngame.isLunarClient()) {
            forgeListener = new InventoryForgeListener(this);
            ForgeEnvironment.register(forgeListener);
        }
        if (gq.yozakura.bridge.StandaloneGuiIngame.isLunarClient()) {
            lunarActive = this;
        }
    }

    @Override
    public void disable() {
        closingScreen = null;
        closingStartedAt = 0L;
        motion.snap(0.0D);
        lunarScreen = null;
        lunarClosing = false;
        if (lunarActive == this) {
            lunarActive = null;
        }
        if (forgeListener != null) {
            ForgeEnvironment.unregister(forgeListener);
            forgeListener = null;
        }
    }

    public static GuiContainer prepareLunarScreen(GuiScreen currentScreen) {
        InventoryAnimation animation = lunarActive;
        if (animation == null) {
            return null;
        }
        if (isPlayerInventory(currentScreen)) {
            GuiContainer container = (GuiContainer) currentScreen;
            if (animation.lunarScreen != container) {
                animation.lunarScreen = container;
                animation.lunarClosing = false;
                animation.motion.setDuration(animation.duration.getValue().longValue());
                animation.motion.snap(0.0D);
            }
            return container;
        }
        if (currentScreen == null && animation.lunarScreen != null) {
            if (!animation.lunarClosing) {
                animation.lunarClosing = true;
                animation.motion.setDuration(animation.duration.getValue().longValue());
                animation.motion.snap(1.0D);
                animation.motion.run(0.0D);
            }
            return animation.lunarScreen;
        }
        animation.lunarScreen = null;
        animation.lunarClosing = false;
        return null;
    }

    private static boolean isPlayerInventory(GuiScreen screen) {
        return screen instanceof GuiInventory;
    }

    public static void renderLunarScreen(GuiContainer container, int mouseX, int mouseY, float partialTicks) {
        InventoryAnimation animation = lunarActive;
        if (animation == null || container == null || animation.lunarScreen != container) {
            return;
        }
        animation.motion.setDuration(animation.duration.getValue().longValue());
        animation.motion.run(animation.lunarClosing ? 0.0D : 1.0D);
        float progress = (float) animation.motion.getValue();
        if (animation.lunarClosing && progress <= 0.001F) {
            animation.lunarScreen = null;
            animation.lunarClosing = false;
            return;
        }
        animation.renderContainer(container, mouseX, mouseY, partialTicks, progress);
    }

    private float fadeAlpha(float progress) {
        return Boolean.TRUE.equals(fade.getValue()) ? progress : 1.0F;
    }

    private float scaleAt(float progress) {
        return startScale.getValue().floatValue()
                + (1.0F - startScale.getValue().floatValue()) * progress;
    }

    private void applyTransform(GuiScreen screen, float progress, float alpha) {
        float scale = scaleAt(progress);
        float centerX = screen.width * 0.5F;
        float centerY = screen.height * 0.5F;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        GlStateManager.translate(-centerX, -centerY, 0.0F);
        if (alpha < 0.999F) {
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        }
    }

    private void renderContainer(GuiContainer container, int mouseX, int mouseY, float partialTicks, float progress) {
        float scale = scaleAt(progress);
        float centerX = container.width * 0.5F;
        float centerY = container.height * 0.5F;
        int logicalMouseX = Math.round(InventoryAnimationPointer.toLogicalCoordinate(mouseX, centerX, scale));
        int logicalMouseY = Math.round(InventoryAnimationPointer.toLogicalCoordinate(mouseY, centerY, scale));
        applyTransform(container, progress, fadeAlpha(progress));
        try {
            if (!LunarInventoryRenderer.draw(container, logicalMouseX, logicalMouseY, partialTicks,
                    backgroundAlpha.getValue().intValue(), cornerRadius.getValue().floatValue(),
                    Boolean.TRUE.equals(backgroundBlur.getValue()), progress)) {
                container.drawScreen(mouseX, mouseY, partialTicks);
            }
        } finally {
            GL11.glPopAttrib();
            GlStateManager.popMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /** Kept separate so Lunar can inspect the module without resolving Forge-only event classes. */
    private static final class InventoryForgeListener {
        private final InventoryAnimation owner;

        private InventoryForgeListener(InventoryAnimation owner) {
            this.owner = owner;
        }

        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(
                priority = net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
        public void onGuiOpen(net.minecraftforge.client.event.GuiOpenEvent event) {
            if (isPlayerInventory(event.gui)) {
                owner.closingScreen = null;
                owner.closingStartedAt = 0L;
                owner.motion.setDuration(owner.duration.getValue().longValue());
                owner.motion.snap(0.0D);
                owner.motion.run(1.0D);
                return;
            }
            if (event.gui == null && isPlayerInventory(mc.currentScreen)) {
                owner.closingScreen = (GuiContainer) mc.currentScreen;
                owner.closingStartedAt = System.currentTimeMillis();
                owner.motion.setDuration(owner.duration.getValue().longValue());
                owner.motion.snap(1.0D);
                owner.motion.run(0.0D);
            }
        }

        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(
                priority = net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
        public void onDrawPre(net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Pre event) {
            if (!isPlayerInventory(event.gui) || event.gui != mc.currentScreen) {
                return;
            }
            owner.motion.setDuration(owner.duration.getValue().longValue());
            owner.motion.run(1.0D);
            float progress = (float) owner.motion.getValue();
            owner.applyTransform(event.gui, progress, owner.fadeAlpha(progress));
        }

        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(
                priority = net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
        public void onDrawPost(net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post event) {
            if (isPlayerInventory(event.gui) && event.gui == mc.currentScreen) {
                GL11.glPopAttrib();
                GlStateManager.popMatrix();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }

        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(
                priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
        public void onOverlay(net.minecraftforge.client.event.RenderGameOverlayEvent.Pre event) {
            if (event.type != net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL
                    || owner.closingScreen == null) {
                return;
            }
            long elapsed = System.currentTimeMillis() - owner.closingStartedAt;
            owner.motion.setDuration(owner.duration.getValue().longValue());
            owner.motion.run(0.0D);
            float progress = (float) owner.motion.getValue();
            if (progress <= 0.001F || elapsed >= owner.duration.getValue().longValue() + 40L) {
                owner.closingScreen = null;
                owner.closingStartedAt = 0L;
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                return;
            }
            owner.renderContainer(owner.closingScreen, -1, -1, event.partialTicks, progress);
        }
    }
}
