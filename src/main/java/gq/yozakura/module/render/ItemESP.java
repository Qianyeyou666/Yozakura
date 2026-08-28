package gq.yozakura.module.render;

import gq.yozakura.event.bridge.Render3DEvent;
import gq.yozakura.event.bridge.RenderFrameGuard;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.render.RenderUtil;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Highlights selected dropped resources and shows their stack sizes in world space. */
public final class ItemESP extends Module {
    private final Option<Boolean> renderIron = new Option<Boolean>("Render Iron", "RenderIron", true);
    private final Option<Boolean> renderGold = new Option<Boolean>("Render Gold", "RenderGold", true);
    private final Numbers<Double> maxDistance = new Numbers<Double>(
            "Max Distance", "MaxDistance", 128.0D, 32.0D, 256.0D, 8.0D);
    private final List<ItemRenderState> renderStates = new ArrayList<ItemRenderState>();
    private final HashMap<Double, Integer> stackCounts = new HashMap<Double, Integer>();
    private int renderStateCount;
    private long lastStandaloneFrame;

    public ItemESP() {
        super("ItemESP", Keyboard.KEY_NONE, ModuleType.Render,
                "Highlight dropped iron, gold, diamond and emerald items");
        addValues(renderIron, renderGold, maxDistance);
        Chinese = "物品透视";
    }

    @Override
    public void enable() {
        renderStateCount = 0;
        lastStandaloneFrame = 0L;
    }

    @Override
    public void disable() {
        renderStateCount = 0;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            updateRenderStates();
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        renderFrame(event.getPartialTicks());
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        renderFrame(event.partialTicks);
    }

    private void updateRenderStates() {
        renderStateCount = 0;
        stackCounts.clear();
        if (!isInGame()) {
            return;
        }
        double maxDistanceSquared = maxDistance.getValue() * maxDistance.getValue();
        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityItem)) {
                continue;
            }
            EntityItem entity = (EntityItem) object;
            if (entity.isDead || entity.ticksExisted < 3 || entity.getEntityItem() == null
                    || entity.getEntityItem().stackSize <= 0
                    || entity.getDistanceSqToEntity(mc.thePlayer) > maxDistanceSquared) {
                continue;
            }
            Item item = entity.getEntityItem().getItem();
            int color = colorFor(item);
            if (color == 0) {
                continue;
            }
            double groupKey = groupKey(item, entity.posX, entity.posY, entity.posZ);
            Integer previousCount = stackCounts.get(groupKey);
            stackCounts.put(groupKey, (previousCount == null ? 0 : previousCount)
                    + entity.getEntityItem().stackSize);
            if (renderStateCount >= renderStates.size()) {
                renderStates.add(new ItemRenderState());
            }
            renderStates.get(renderStateCount++).set(entity, color, groupKey);
        }
    }

    private int colorFor(Item item) {
        if (item == Items.iron_ingot && Boolean.TRUE.equals(renderIron.getValue())) {
            return 0xFFE6EDF5;
        }
        if (item == Items.gold_ingot && Boolean.TRUE.equals(renderGold.getValue())) {
            return 0xFFFFD34D;
        }
        if (item == Items.diamond) {
            return 0xFF59E6FF;
        }
        if (item == Items.emerald) {
            return 0xFF55E69A;
        }
        return 0;
    }

    private void renderFrame(float partialTicks) {
        if (skipDuplicateStandaloneFrame() || !isInGame() || renderStateCount == 0) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_TEXTURE_BIT);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GlStateManager.disableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GL11.glLineWidth(2.0F);
            for (int i = 0; i < renderStateCount; i++) {
                ItemRenderState state = renderStates.get(i);
                EntityItem entity = state.entity;
                if (entity == null || entity.isDead || entity.getEntityItem() == null) {
                    continue;
                }
                double x = interpolate(entity.lastTickPosX, entity.posX, partialTicks)
                        - mc.getRenderManager().viewerPosX;
                double y = interpolate(entity.lastTickPosY, entity.posY, partialTicks)
                        - mc.getRenderManager().viewerPosY;
                double z = interpolate(entity.lastTickPosZ, entity.posZ, partialTicks)
                        - mc.getRenderManager().viewerPosZ;
                double distance = Math.sqrt(entity.getDistanceSqToEntity(mc.thePlayer));
                double half = clamp(0.20D + distance * 0.002D, 0.20D, 0.38D);
                AxisAlignedBB box = new AxisAlignedBB(x - half, y, z - half,
                        x + half, y + Math.max(0.28D, entity.height + 0.08D), z + half);
                drawBox(box, state.color);
                Integer count = stackCounts.get(state.groupKey);
                if (count != null) {
                    drawCount(count, state.color, x, y + 0.58D, z, distance);
                }
            }
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
        }
    }

    private void drawBox(AxisAlignedBB box, int color) {
        float red = ((color >>> 16) & 255) / 255.0F;
        float green = ((color >>> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        GL11.glColor4f(red, green, blue, 0.13F);
        RenderUtil.drawBoundingBox(box);
        GL11.glColor4f(red, green, blue, 0.95F);
        RenderUtil.drawOutlinedBoundingBox(box);
    }

    private void drawCount(int stackCount, int color, double x, double y, double z, double distance) {
        String count = String.valueOf(stackCount);
        float scale = (float) clamp(0.022D + distance * 0.0012D, 0.022D, 0.065D);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate((mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F)
                    * mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-scale, -scale, -scale);
            GlStateManager.enableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            mc.fontRendererObj.drawStringWithShadow(count,
                    -mc.fontRendererObj.getStringWidth(count) / 2.0F, 0.0F, color);
            GlStateManager.depthMask(true);
            GlStateManager.disableTexture2D();
        } finally {
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }
    }

    private boolean skipDuplicateStandaloneFrame() {
        long frame = RenderFrameGuard.currentStandalone3DFrame();
        if (frame == 0L) {
            return false;
        }
        if (lastStandaloneFrame == frame) {
            return true;
        }
        lastStandaloneFrame = frame;
        return false;
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double groupKey(Item item, double x, double y, double z) {
        double safeX = x == 0.0D ? 1.0D : x;
        double safeY = y == 0.0D ? 1.0D : y;
        double safeZ = z == 0.0D ? 1.0D : z;
        double key = Math.round((safeX + 1.0D) * Math.floor(safeY) * (safeZ + 2.0D));
        if (item == Items.iron_ingot) {
            return key + 0.155D;
        }
        if (item == Items.gold_ingot) {
            return key + 0.255D;
        }
        if (item == Items.diamond) {
            return key + 0.355D;
        }
        return key + 0.455D;
    }

    private static final class ItemRenderState {
        private EntityItem entity;
        private int color;
        private double groupKey;

        private void set(EntityItem entity, int color, double groupKey) {
            this.entity = entity;
            this.color = color;
            this.groupKey = groupKey;
        }
    }
}
