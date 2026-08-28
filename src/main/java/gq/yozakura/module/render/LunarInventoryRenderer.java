package gq.yozakura.module.render;

import gq.yozakura.engine.render.ui.RenderServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import java.util.List;

/** Draws a player inventory without Lunar's replacement inventory texture. */
final class LunarInventoryRenderer {
    private static final float WIDTH = 176.0F;
    private static final float HEIGHT = 166.0F;
    private static final VanillaTooltipRenderer TOOLTIP_RENDERER = new VanillaTooltipRenderer();
    private static Field hoveredSlotField;

    private LunarInventoryRenderer() {
    }

    static boolean draw(GuiContainer container, int mouseX, int mouseY, float partialTicks,
                        int backgroundAlpha, float radius, boolean blur, float animationAlpha) {
        if (!(container instanceof GuiInventory)) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        float left = (container.width - WIDTH) * 0.5F;
        float top = (container.height - HEIGHT) * 0.5F;
        int alpha = Math.max(0, Math.min(255, Math.round(backgroundAlpha * animationAlpha)));
        int surface = withAlpha(ClickGUI.currentPalette().getSurfaceRaised(), alpha);
        int border = withAlpha(ClickGUI.currentPalette().getAccentAlt(), Math.min(alpha, 105));

        HUD.drawNightBloomShadow(left, top, left + WIDTH, top + HEIGHT, radius, 0.72F * animationAlpha);
        if (blur) {
            RenderServices.panels().panel(left, top, left + WIDTH, top + HEIGHT,
                    radius, 0.64F, surface, border);
        } else {
            RenderServices.shapes().roundedBorder(left, top, left + WIDTH, top + HEIGHT,
                    radius, 0.7F, surface, border);
        }

        Slot hoveredSlot = findHoveredSlot(container, left, top, mouseX, mouseY);
        setHoveredSlot(container, hoveredSlot);
        drawPlayerModel(mc, left, top, mouseX, mouseY, animationAlpha);
        drawSlots(container, left, top, hoveredSlot, animationAlpha);
        drawCursorStack(mc, mouseX, mouseY, animationAlpha);
        if (hoveredSlot != null && hoveredSlot.getHasStack()
                && mc.thePlayer != null && mc.thePlayer.inventory.getItemStack() == null) {
            drawVanillaTooltip(mc, hoveredSlot.getStack(), mouseX, mouseY, container.width, container.height);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static void drawSlots(GuiContainer container, float left, float top,
                                  Slot hoveredSlot, float animationAlpha) {
        Minecraft mc = Minecraft.getMinecraft();
        List<Slot> slots = container.inventorySlots.inventorySlots;
        int slotSurface = withAlpha(ClickGUI.currentPalette().getSurface(),
                Math.round(150.0F * animationAlpha));
        int hover = withAlpha(ClickGUI.currentPalette().getAccentPrimary(),
                Math.round(105.0F * animationAlpha));
        float visibility = itemVisibility(animationAlpha);

        for (Slot slot : slots) {
            float x = left + slot.xDisplayPosition;
            float y = top + slot.yDisplayPosition;
            RenderServices.shapes().rounded(x - 1.0F, y - 1.0F, x + 17.0F, y + 17.0F,
                    2.4F, slot == hoveredSlot ? hover : slotSurface);
        }

        if (visibility <= 0.01F) {
            return;
        }

        GlStateManager.pushMatrix();
        float previousZLevel = mc.getRenderItem().zLevel;
        try {
            mc.getRenderItem().zLevel = 100.0F;
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            RenderHelper.enableGUIStandardItemLighting();
            for (Slot slot : slots) {
                ItemStack stack = slot.getStack();
                if (stack == null) {
                    continue;
                }
                int x = Math.round(left + slot.xDisplayPosition);
                int y = Math.round(top + slot.yDisplayPosition);
                GlStateManager.pushMatrix();
                try {
                    GlStateManager.translate(x + 8.0F, y + 8.0F, 0.0F);
                    GlStateManager.scale(visibility, visibility, 1.0F);
                    GlStateManager.translate(-x - 8.0F, -y - 8.0F, 0.0F);
                    mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
                    mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRendererObj, stack, x, y, null);
                } finally {
                    GlStateManager.popMatrix();
                }
            }
        } finally {
            mc.getRenderItem().zLevel = previousZLevel;
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private static void drawPlayerModel(Minecraft mc, float left, float top, int mouseX, int mouseY,
                                        float animationAlpha) {
        float visibility = itemVisibility(animationAlpha);
        if (mc.thePlayer == null || visibility <= 0.01F) {
            return;
        }
        float centerX = left + 51.0F;
        float centerY = top + 75.0F;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(centerX, centerY, 0.0F);
            GlStateManager.scale(visibility, visibility, 1.0F);
            GlStateManager.translate(-centerX, -centerY, 0.0F);
            GuiInventory.drawEntityOnScreen(Math.round(centerX), Math.round(centerY), 30,
                    centerX - mouseX, top + 25.0F - mouseY, mc.thePlayer);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static void drawCursorStack(Minecraft mc, int mouseX, int mouseY, float animationAlpha) {
        float visibility = itemVisibility(animationAlpha);
        if (mc.thePlayer == null || visibility <= 0.01F) {
            return;
        }
        ItemStack carried = mc.thePlayer.inventory.getItemStack();
        if (carried == null) {
            return;
        }
        GlStateManager.pushMatrix();
        float previousZLevel = mc.getRenderItem().zLevel;
        try {
            GlStateManager.translate(0.0F, 0.0F, 320.0F);
            GlStateManager.translate(mouseX, mouseY, 0.0F);
            GlStateManager.scale(visibility, visibility, 1.0F);
            GlStateManager.translate(-mouseX, -mouseY, 0.0F);
            mc.getRenderItem().zLevel = 100.0F;
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableRescaleNormal();
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectIntoGUI(carried, mouseX - 8, mouseY - 8);
            mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRendererObj, carried,
                    mouseX - 8, mouseY - 8, null);
        } finally {
            mc.getRenderItem().zLevel = previousZLevel;
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.popMatrix();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @SuppressWarnings("unchecked")
    private static Slot findHoveredSlot(GuiContainer container, float left, float top,
                                        int mouseX, int mouseY) {
        if (mouseX < 0 || mouseY < 0) {
            return null;
        }
        List<Slot> slots = container.inventorySlots.inventorySlots;
        for (Slot slot : slots) {
            float x = left + slot.xDisplayPosition;
            float y = top + slot.yDisplayPosition;
            if (slot.canBeHovered() && mouseX >= x && mouseX < x + 16.0F
                    && mouseY >= y && mouseY < y + 16.0F) {
                return slot;
            }
        }
        return null;
    }

    private static void setHoveredSlot(GuiContainer container, Slot hoveredSlot) {
        try {
            hoveredSlotField().set(container, hoveredSlot);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to update GuiContainer hovered slot", exception);
        }
    }

    private static Field hoveredSlotField() {
        if (hoveredSlotField != null) {
            return hoveredSlotField;
        }
        for (String name : new String[]{"theSlot", "field_147006_u"}) {
            try {
                Field field = GuiContainer.class.getDeclaredField(name);
                field.setAccessible(true);
                hoveredSlotField = field;
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        for (Field field : GuiContainer.class.getDeclaredFields()) {
            if (field.getType() == Slot.class) {
                field.setAccessible(true);
                hoveredSlotField = field;
                return field;
            }
        }
        throw new IllegalStateException("Unable to locate GuiContainer hovered Slot field");
    }

    private static void drawVanillaTooltip(Minecraft mc, ItemStack stack, int mouseX, int mouseY,
                                           int screenWidth, int screenHeight) {
        TOOLTIP_RENDERER.draw(mc, stack, mouseX, mouseY, screenWidth, screenHeight);
    }

    private static float itemVisibility(float animationAlpha) {
        float progress = Math.max(0.0F, Math.min(1.0F, animationAlpha));
        return progress * progress * (3.0F - 2.0F * progress);
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | Math.max(0, Math.min(255, alpha)) << 24;
    }

    private static final class VanillaTooltipRenderer extends GuiScreen {
        private void draw(Minecraft minecraft, ItemStack stack, int mouseX, int mouseY,
                          int screenWidth, int screenHeight) {
            mc = minecraft;
            fontRendererObj = minecraft.fontRendererObj;
            itemRender = minecraft.getRenderItem();
            width = screenWidth;
            height = screenHeight;
            renderToolTip(stack, mouseX, mouseY);
        }
    }
}
