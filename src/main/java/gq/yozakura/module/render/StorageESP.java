package gq.yozakura.module.render;

import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.engine.render.ui.VisualPalette;
import gq.yozakura.util.render.ScreenSpaceGlowRenderer;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Timer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.lang.reflect.Field;

public class StorageESP extends Module {
    public enum StorageVisualMode {
        OUTLINE,
        GLOWESP
    }

    private Option<Boolean> Chest = new Option<Boolean>("Chest","Chest", true);
    private Option<Boolean> EnderChest = new Option<Boolean>("EnderChest","EnderChest", false);
    private final Mode<StorageVisualMode> visualMode = new Mode<StorageVisualMode>("Mode", "Mode",
            StorageVisualMode.values(), StorageVisualMode.OUTLINE);
    private final Numbers<Double> glowStrength = new Numbers<Double>("Glow Strength", "GlowStrength",
            0.86D, 0.20D, 1.0D, 0.02D);

    public StorageESP() {
        super("StorageESP", Keyboard.KEY_NONE, ModuleType.Render,"Chest Renderer ESP");
        glowStrength.visibleWhen(() -> visualMode.getValue() == StorageVisualMode.GLOWESP);
        this.addValues(this.Chest, this.EnderChest, visualMode, glowStrength);
        Chinese="容器透视";
    }

    @SubscribeEvent
    public void o(final RenderWorldLastEvent ev) {
        if (!isInGame()) {
            return;
        }
        if (visualMode.getValue() == StorageVisualMode.GLOWESP) {
            renderScreenSpaceGlow(ev.partialTicks);
            return;
        }
        renderStorageBatch();
    }

    private void renderScreenSpaceGlow(float partialTicks) {
        ScreenSpaceGlowRenderer renderer = ScreenSpaceGlowRenderer.shared();
        renderer.beginFrame(ClickGUI.currentPalette(), glowStrength.getValue().floatValue());
        try {
            for (TileEntity te : mc.theWorld.loadedTileEntityList) {
                if (te instanceof TileEntityChest && this.Chest.getValue()) {
                    renderer.collect(te.getPos());
                }
                if (te instanceof TileEntityEnderChest && this.EnderChest.getValue()) {
                    renderer.collect(te.getPos());
                }
            }
            renderer.renderMask(partialTicks);
            renderer.composite();
        } finally {
            if (renderer.isFrameOpen()) {
                renderer.discard();
            }
        }
    }

    private void renderStorageBatch() {
        if (!hasSelectedStorage()) {
            return;
        }
        VisualPalette palette = ClickGUI.currentPalette();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(3042);
        GL11.glLineWidth(2.0f);
        GL11.glDisable(3553);
        GL11.glDisable(2929);
        GL11.glDepthMask(false);
        try {
            renderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            appendStorageOutlines(renderer, palette);
            tessellator.draw();

            renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            appendStorageFills(renderer, palette);
            tessellator.draw();
        } finally {
            GL11.glEnable(3553);
            GL11.glEnable(2929);
            GL11.glDepthMask(true);
            GL11.glDisable(3042);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private boolean hasSelectedStorage() {
        for (TileEntity tileEntity : mc.theWorld.loadedTileEntityList) {
            if (tileEntity instanceof TileEntityChest && Boolean.TRUE.equals(Chest.getValue())) {
                return true;
            }
            if (tileEntity instanceof TileEntityEnderChest && Boolean.TRUE.equals(EnderChest.getValue())) {
                return true;
            }
        }
        return false;
    }

    private void appendStorageOutlines(WorldRenderer renderer, VisualPalette palette) {
        for (TileEntity tileEntity : mc.theWorld.loadedTileEntityList) {
            if (tileEntity instanceof TileEntityChest && Boolean.TRUE.equals(Chest.getValue())) {
                appendStorageOutline(renderer, tileEntity.getPos(), palette.getStorageChest());
            } else if (tileEntity instanceof TileEntityEnderChest && Boolean.TRUE.equals(EnderChest.getValue())) {
                appendStorageOutline(renderer, tileEntity.getPos(), palette.getStorageEnderChest());
            }
        }
    }

    private void appendStorageFills(WorldRenderer renderer, VisualPalette palette) {
        for (TileEntity tileEntity : mc.theWorld.loadedTileEntityList) {
            if (tileEntity instanceof TileEntityChest && Boolean.TRUE.equals(Chest.getValue())) {
                appendStorageFill(renderer, tileEntity.getPos(), palette.getStorageChest());
            } else if (tileEntity instanceof TileEntityEnderChest && Boolean.TRUE.equals(EnderChest.getValue())) {
                appendStorageFill(renderer, tileEntity.getPos(), palette.getStorageEnderChest());
            }
        }
    }

    private void appendStorageOutline(WorldRenderer renderer, BlockPos position, int color) {
        double minX = position.getX() - mc.getRenderManager().viewerPosX;
        double minY = position.getY() - mc.getRenderManager().viewerPosY;
        double minZ = position.getZ() - mc.getRenderManager().viewerPosZ;
        double maxX = minX + 1.0d;
        double maxY = minY + 1.0d;
        double maxZ = minZ + 1.0d;
        float red = (color >> 16 & 0xFF) / 255.0f;
        float green = (color >> 8 & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;
        float alpha = (color >> 24 & 0xFF) / 255.0f;

        line(renderer, minX, minY, minZ, maxX, minY, minZ, red, green, blue, alpha);
        line(renderer, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, alpha);
        line(renderer, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha);
        line(renderer, minX, minY, maxZ, minX, minY, minZ, red, green, blue, alpha);
        line(renderer, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        line(renderer, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
        line(renderer, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        line(renderer, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha);
        line(renderer, minX, minY, minZ, minX, maxY, minZ, red, green, blue, alpha);
        line(renderer, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        line(renderer, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
        line(renderer, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
    }

    private void appendStorageFill(WorldRenderer renderer, BlockPos position, int color) {
        double minX = position.getX() - mc.getRenderManager().viewerPosX;
        double minY = position.getY() - mc.getRenderManager().viewerPosY;
        double minZ = position.getZ() - mc.getRenderManager().viewerPosZ;
        double maxX = minX + 1.0d;
        double maxY = minY + 1.0d;
        double maxZ = minZ + 1.0d;
        float red = (color >> 16 & 0xFF) / 255.0f;
        float green = (color >> 8 & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;
        float alpha = 0.25f;

        doubleSidedQuad(renderer, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ,
                red, green, blue, alpha);
        doubleSidedQuad(renderer, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ,
                red, green, blue, alpha);
        doubleSidedQuad(renderer, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ,
                red, green, blue, alpha);
        doubleSidedQuad(renderer, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ,
                red, green, blue, alpha);
        doubleSidedQuad(renderer, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ,
                red, green, blue, alpha);
        doubleSidedQuad(renderer, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ,
                red, green, blue, alpha);
    }

    private static void line(WorldRenderer renderer, double startX, double startY, double startZ,
                             double endX, double endY, double endZ, float red, float green, float blue, float alpha) {
        renderer.pos(startX, startY, startZ).color(red, green, blue, alpha).endVertex();
        renderer.pos(endX, endY, endZ).color(red, green, blue, alpha).endVertex();
    }

    private static void doubleSidedQuad(WorldRenderer renderer,
                                        double x1, double y1, double z1, double x2, double y2, double z2,
                                        double x3, double y3, double z3, double x4, double y4, double z4,
                                        float red, float green, float blue, float alpha) {
        renderer.pos(x1, y1, z1).color(red, green, blue, alpha).endVertex();
        renderer.pos(x2, y2, z2).color(red, green, blue, alpha).endVertex();
        renderer.pos(x3, y3, z3).color(red, green, blue, alpha).endVertex();
        renderer.pos(x4, y4, z4).color(red, green, blue, alpha).endVertex();
        renderer.pos(x4, y4, z4).color(red, green, blue, alpha).endVertex();
        renderer.pos(x3, y3, z3).color(red, green, blue, alpha).endVertex();
        renderer.pos(x2, y2, z2).color(red, green, blue, alpha).endVertex();
        renderer.pos(x1, y1, z1).color(red, green, blue, alpha).endVertex();
    }

    private static Minecraft mc;
    private static Field timerField;

    public static void re(final BlockPos bp, final int color) {
        if (bp == null) {
            return;
        }
        final double x = bp.getX() - StorageESP.mc.getRenderManager().viewerPosX;
        final double y = bp.getY() - StorageESP.mc.getRenderManager().viewerPosY;
        final double z = bp.getZ() - StorageESP.mc.getRenderManager().viewerPosZ;
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(3042);
        GL11.glLineWidth(2.0f);
        GL11.glDisable(3553);
        GL11.glDisable(2929);
        GL11.glDepthMask(false);
        final float a = (color >> 24 & 0xFF) / 255.0f;
        final float r = (color >> 16 & 0xFF) / 255.0f;
        final float g = (color >> 8 & 0xFF) / 255.0f;
        final float b = (color & 0xFF) / 255.0f;
        GL11.glColor4d((double)r, (double)g, (double)b, (double)a);
        final AxisAlignedBB box = new AxisAlignedBB(x, y, z, x + 1.0, y + 1.0, z + 1.0);
        RenderGlobal.drawSelectionBoundingBox(box);
        dbb(box, r, g, b);
        GL11.glEnable(3553);
        GL11.glEnable(2929);
        GL11.glDepthMask(true);
        GL11.glDisable(3042);
    }
    public static Timer gt() {
        try {
            if (timerField == null) {
                timerField = resolveTimerField();
            }
            return (Timer) timerField.get(mc);
        } catch (Exception er) {
            timerField = null;
            return null;
        }
    }

    private static Field resolveTimerField() throws NoSuchFieldException {
        try {
            Field field = Minecraft.class.getDeclaredField(new String(new char[] { 't', 'i', 'm', 'e', 'r' }));
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ignored) {
            Field field = Minecraft.class.getDeclaredField(new String(new char[] { 'f', 'i', 'e', 'l', 'd', '_', '7', '1', '4', '2', '8', '_', 'T' }));
            field.setAccessible(true);
            return field;
        }
    }

    public static void ee(final Entity e, int color, final boolean damage, final int type) {
        if (e == null) {
            return;
        }
        final float partialTicks = gt().renderPartialTicks;
        final double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * partialTicks - StorageESP.mc.getRenderManager().viewerPosX;
        final double y = e.lastTickPosY + (e.posY - e.lastTickPosY) * partialTicks - StorageESP.mc.getRenderManager().viewerPosY;
        final double z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * partialTicks - StorageESP.mc.getRenderManager().viewerPosZ;
        final AxisAlignedBB sourceBox = e.getEntityBoundingBox();
        final AxisAlignedBB renderBox = new AxisAlignedBB(
                sourceBox.minX - 0.05 - e.posX + (e.posX - StorageESP.mc.getRenderManager().viewerPosX),
                sourceBox.minY - e.posY + (e.posY - StorageESP.mc.getRenderManager().viewerPosY),
                sourceBox.minZ - 0.05 - e.posZ + (e.posZ - StorageESP.mc.getRenderManager().viewerPosZ),
                sourceBox.maxX + 0.05 - e.posX + (e.posX - StorageESP.mc.getRenderManager().viewerPosX),
                sourceBox.maxY + 0.1 - e.posY + (e.posY - StorageESP.mc.getRenderManager().viewerPosY),
                sourceBox.maxZ + 0.05 - e.posZ + (e.posZ - StorageESP.mc.getRenderManager().viewerPosZ));
        if (e instanceof EntityPlayer && damage && ((EntityPlayer)e).hurtTime != 0) {
            color = Color.RED.getRGB();
        }
        final float a = (color >> 24 & 0xFF) / 255.0f;
        final float r = (color >> 16 & 0xFF) / 255.0f;
        final float g = (color >> 8 & 0xFF) / 255.0f;
        final float b = (color & 0xFF) / 255.0f;
        if (type == 1) {
            GlStateManager.pushMatrix();
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3042);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);
            GL11.glLineWidth(3.0f);
            GL11.glColor4f(r, g, b, a);
            RenderGlobal.drawSelectionBoundingBox(renderBox);
            dbb(renderBox, r, g, b);
            GL11.glEnable(3553);
            GL11.glEnable(2929);
            GL11.glDepthMask(true);
            GL11.glDisable(3042);
            GlStateManager.popMatrix();
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
        else if (type == 2 || type == 3) {
            final boolean mode = type == 2;
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3042);
            GL11.glLineWidth(3.0f);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);
            GL11.glColor4d((double)r, (double)g, (double)b, (double)a);
            if (mode) {
                RenderGlobal.drawSelectionBoundingBox(renderBox);
            }
            else {
                dbb(renderBox, r, g, b);
            }
            GL11.glEnable(3553);
            GL11.glEnable(2929);
            GL11.glDepthMask(true);
            GL11.glDisable(3042);
        }
        else if (type == 4) {
            GL11.glPushMatrix();
            GL11.glTranslated(x, y - 0.2, z);
            GL11.glScalef(0.03f, 0.03f, 0.03f);
            GL11.glRotated((double)(-StorageESP.mc.getRenderManager().playerViewY), 0.0, 1.0, 0.0);
            GlStateManager.disableDepth();
            Gui.drawRect(-20, -1, -26, 75, Color.black.getRGB());
            Gui.drawRect(-21, 0, -25, 74, color);
            Gui.drawRect(20, -1, 26, 75, Color.black.getRGB());
            Gui.drawRect(21, 0, 25, 74, color);
            Gui.drawRect(-20, -1, 21, 5, Color.black.getRGB());
            Gui.drawRect(-21, 0, 24, 4, color);
            Gui.drawRect(-20, 70, 21, 75, Color.black.getRGB());
            Gui.drawRect(-21, 71, 25, 74, color);
            GlStateManager.enableDepth();
            GL11.glPopMatrix();
        }
    }

    public static void dbb(final AxisAlignedBB abb, final float r, final float g, final float b) {
        final float a = 0.25f;
        final Tessellator ts = Tessellator.getInstance();
        final WorldRenderer vb = ts.getWorldRenderer();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        ts.draw();
    }

    public static void dtl(final Entity e, final int color, final float lw) {
        if (e == null) {
            return;
        }
        final float partialTicks = gt().renderPartialTicks;
        final double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * partialTicks - StorageESP.mc.getRenderManager().viewerPosX;
        final double y = e.getEyeHeight() + e.lastTickPosY + (e.posY - e.lastTickPosY) * partialTicks - StorageESP.mc.getRenderManager().viewerPosY;
        final double z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * partialTicks - StorageESP.mc.getRenderManager().viewerPosZ;
        final float a = (color >> 24 & 0xFF) / 255.0f;
        final float r = (color >> 16 & 0xFF) / 255.0f;
        final float g = (color >> 8 & 0xFF) / 255.0f;
        final float b = (color & 0xFF) / 255.0f;
        GL11.glPushMatrix();
        GL11.glEnable(3042);
        GL11.glEnable(2848);
        GL11.glDisable(2929);
        GL11.glDisable(3553);
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(3042);
        GL11.glLineWidth(lw);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(2);
        GL11.glVertex3d(0.0, 0.0 + StorageESP.mc.thePlayer.getEyeHeight(), 0.0);
        GL11.glVertex3d(x, y, z);
        GL11.glEnd();
        GL11.glDisable(3042);
        GL11.glEnable(3553);
        GL11.glEnable(2929);
        GL11.glDisable(2848);
        GL11.glDisable(3042);
        GL11.glPopMatrix();
    }

    static {
        StorageESP.mc = Minecraft.getMinecraft();
    }
}
