package gq.yozakura.module.render;

import gq.yozakura.bridge.YozakuraEventBridge;
import gq.yozakura.event.bridge.Render2DEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.module.combat.AntiBot;
import gq.yozakura.engine.render.ui.VisualPalette;
import gq.yozakura.util.color.ColorUtil;
import gq.yozakura.util.render.RenderUtil;
import gq.yozakura.util.render.ScreenSpaceGlowRenderer;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class ESP extends Module {
    public enum EspBoxMode {
        OUTLINE,
        FILLED,
        BOTH,
        GLOWESP,
        TWO_D,
        TWO_D_CORNERS,
        TWO_D_HALF_CORNERS
    }

    private static final FloatBuffer MODEL_VIEW = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer PROJECTED_POINT = BufferUtils.createFloatBuffer(3);

    private final Mode<EspBoxMode> boxMode =
            new Mode<EspBoxMode>("Mode", "Mode", EspBoxMode.values(), EspBoxMode.BOTH);
    private final Option<Boolean> paletteColors = new Option<Boolean>("Palette Colors", "PaletteColors", true);
    private final Option<Boolean> rainbow = new Option<Boolean>("Palette Rainbow", "Rainbow", true);
    private final Option<Boolean> players = new Option<Boolean>("Players", "Players", true);
    private final Option<Boolean> mobs = new Option<Boolean>("Mobs", "Mobs", false);
    private final Option<Boolean> animals = new Option<Boolean>("Animals", "Animals", false);
    private final Option<Boolean> invisible = new Option<Boolean>("Invisible", "Invisible", false);
    private final Option<Boolean> redOnDamage = new Option<Boolean>("Red On Damage", "RedOnDamage", true);
    private final Option<Boolean> distanceFade = new Option<Boolean>("Distance Fade", "DistanceFade", true);
    private final Option<Boolean> nameTags = new Option<Boolean>("Name Tags", "NameTags", true);
    private final Option<Boolean> healthBar = new Option<Boolean>("Health Bar", "HealthBar", true);
    private final Option<Boolean> heldItem = new Option<Boolean>("Held Item", "HeldItem", false);
    private final Option<Boolean> oppositeCorners = new Option<Boolean>("Opposite Corners", "OppositeCorners", false);
    private final Numbers<Double> red = new Numbers<Double>("Red", "Red", 95.0, 0.0, 255.0, 1.0);
    private final Numbers<Double> green = new Numbers<Double>("Green", "Green", 190.0, 0.0, 255.0, 1.0);
    private final Numbers<Double> blue = new Numbers<Double>("Blue", "Blue", 255.0, 0.0, 255.0, 1.0);
    private final Numbers<Double> alpha = new Numbers<Double>("Alpha", "Alpha", 160.0, 35.0, 255.0, 5.0);
    private final Numbers<Double> glowStrength = new Numbers<Double>("Glow Strength", "GlowStrength",
            0.88D, 0.20D, 1.0D, 0.02D);
    private final Numbers<Double> lineWidth = new Numbers<Double>("Line Width", "LineWidth", 1.0D, 0.5D, 3.0D, 0.5D);
    private final List<OverlayEntry> overlayEntries = new ArrayList<OverlayEntry>();

    public ESP() {
        super("ESP", Keyboard.KEY_NONE, ModuleType.Render, "Draw entity boxes");
        glowStrength.visibleWhen(() -> boxMode.getValue() == EspBoxMode.GLOWESP);
        distanceFade.visibleWhen(this::is2DMode);
        nameTags.visibleWhen(this::is2DMode);
        healthBar.visibleWhen(this::is2DMode);
        heldItem.visibleWhen(this::is2DMode);
        oppositeCorners.visibleWhen(() -> boxMode.getValue() == EspBoxMode.TWO_D_HALF_CORNERS);
        lineWidth.visibleWhen(this::is2DMode);
        rainbow.visibleWhen(() -> !Boolean.TRUE.equals(paletteColors.getValue()));
        red.visibleWhen(() -> !Boolean.TRUE.equals(paletteColors.getValue()));
        green.visibleWhen(() -> !Boolean.TRUE.equals(paletteColors.getValue()));
        blue.visibleWhen(() -> !Boolean.TRUE.equals(paletteColors.getValue()));
        this.addValues(boxMode, paletteColors, rainbow, players, mobs, animals, invisible, redOnDamage,
                distanceFade, nameTags, healthBar, heldItem, oppositeCorners, red, green, blue, alpha, lineWidth, glowStrength);
        Chinese = "实体框体";
    }

    @SubscribeEvent
    public void onWorld(RenderWorldLastEvent event) {
        if (!isInGame()) {
            return;
        }

        EspBoxMode currentMode = boxMode.getValue();
        if (is2DMode()) {
            collectOverlayEntries(event.partialTicks);
            return;
        }
        overlayEntries.clear();
        if (currentMode == EspBoxMode.GLOWESP) {
            renderScreenSpaceGlow(event.partialTicks);
            return;
        }
        int renderType = getRenderType();
        int rainbowColor = Boolean.TRUE.equals(rainbow.getValue()) ? withAlpha(ColorUtil.getRainbow().getRGB()) : 0;
        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase entity = (EntityLivingBase) object;
            if (!isValidTarget(entity)) {
                continue;
            }
            StorageESP.ee((Entity) entity, getColor(entity, rainbowColor), false, renderType);
        }
    }

    @EventTarget
    public void onOverlay(Render2DEvent event) {
        renderOverlayEntries();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onOverlay(RenderGameOverlayEvent.Text event) {
        if (!YozakuraEventBridge.hasRenderedOverlayThisFrame()) {
            renderOverlayEntries();
        }
    }

    @SubscribeEvent
    public void onRenderNameTag(RenderLivingEvent.Specials.Pre event) {
        if (!isInGame() || !is2DMode() || !Boolean.TRUE.equals(nameTags.getValue())
                || !(event.entity instanceof EntityLivingBase)) {
            return;
        }
        if (isValidTarget((EntityLivingBase) event.entity)) {
            event.setCanceled(true);
        }
    }

    private void renderScreenSpaceGlow(float partialTicks) {
        ScreenSpaceGlowRenderer renderer = ScreenSpaceGlowRenderer.shared();
        renderer.beginFrame(ClickGUI.currentPalette(), glowStrength.getValue().floatValue());
        try {
            for (Object object : mc.theWorld.loadedEntityList) {
                if (!(object instanceof EntityLivingBase)) {
                    continue;
                }
                EntityLivingBase entity = (EntityLivingBase) object;
                if (isValidTarget(entity)) {
                    renderer.collect(entity);
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

    private boolean isValidTarget(EntityLivingBase entity) {
        if (entity == null || entity == mc.thePlayer || entity.isDead || entity.deathTime > 0) {
            return false;
        }
        if (!Boolean.TRUE.equals(invisible.getValue()) && entity.isInvisible()) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            return Boolean.TRUE.equals(players.getValue()) && !AntiBot.isServerBot(entity);
        }
        if (entity instanceof EntityAnimal || entity instanceof EntityWaterMob || entity instanceof EntityAmbientCreature) {
            return Boolean.TRUE.equals(animals.getValue());
        }
        if (entity instanceof EntityMob || entity instanceof EntitySlime || entity instanceof IMob) {
            return Boolean.TRUE.equals(mobs.getValue());
        }
        return Boolean.TRUE.equals(mobs.getValue());
    }

    private boolean is2DMode() {
        EspBoxMode mode = boxMode.getValue();
        return mode == EspBoxMode.TWO_D || mode == EspBoxMode.TWO_D_CORNERS || mode == EspBoxMode.TWO_D_HALF_CORNERS;
    }

    private void collectOverlayEntries(float partialTicks) {
        overlayEntries.clear();
        if (!captureProjectionState()) {
            return;
        }
        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase entity = (EntityLivingBase) object;
            if (!isValidTarget(entity)) {
                continue;
            }
            EspOverlayGeometry.Bounds bounds = projectBounds(entity, partialTicks);
            if (bounds == null) {
                continue;
            }
            float opacity = Boolean.TRUE.equals(distanceFade.getValue())
                    ? MathHelper.clamp_float(1.0f - mc.thePlayer.getDistanceToEntity(entity) / 64.0f, 0.0f, 1.0f)
                    : 1.0f;
            if (opacity > 0.01f) {
                overlayEntries.add(new OverlayEntry(entity, bounds, opacity));
            }
        }
    }

    private boolean captureProjectionState() {
        try {
            MODEL_VIEW.clear();
            PROJECTION.clear();
            VIEWPORT.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODEL_VIEW);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
            GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
            MODEL_VIEW.rewind();
            PROJECTION.rewind();
            VIEWPORT.rewind();
            return VIEWPORT.get(2) > 0 && VIEWPORT.get(3) > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private EspOverlayGeometry.Bounds projectBounds(EntityLivingBase entity, float partialTicks) {
        double interpolatedX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double interpolatedY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double interpolatedZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        AxisAlignedBB rawBox = entity.getEntityBoundingBox();
        AxisAlignedBB box = new AxisAlignedBB(
                rawBox.minX - entity.posX + interpolatedX - 0.05D,
                rawBox.minY - entity.posY + interpolatedY,
                rawBox.minZ - entity.posZ + interpolatedZ - 0.05D,
                rawBox.maxX - entity.posX + interpolatedX + 0.05D,
                rawBox.maxY - entity.posY + interpolatedY + 0.10D,
                rawBox.maxZ - entity.posZ + interpolatedZ + 0.05D);
        double viewerX = mc.getRenderManager().viewerPosX;
        double viewerY = mc.getRenderManager().viewerPosY;
        double viewerZ = mc.getRenderManager().viewerPosZ;
        float[][] points = new float[][]{
                project(box.minX - viewerX, box.minY - viewerY, box.minZ - viewerZ),
                project(box.minX - viewerX, box.minY - viewerY, box.maxZ - viewerZ),
                project(box.minX - viewerX, box.maxY - viewerY, box.minZ - viewerZ),
                project(box.minX - viewerX, box.maxY - viewerY, box.maxZ - viewerZ),
                project(box.maxX - viewerX, box.minY - viewerY, box.minZ - viewerZ),
                project(box.maxX - viewerX, box.minY - viewerY, box.maxZ - viewerZ),
                project(box.maxX - viewerX, box.maxY - viewerY, box.minZ - viewerZ),
                project(box.maxX - viewerX, box.maxY - viewerY, box.maxZ - viewerZ)
        };
        return EspOverlayGeometry.bounds(points);
    }

    private float[] project(double x, double y, double z) {
        PROJECTED_POINT.clear();
        MODEL_VIEW.rewind();
        PROJECTION.rewind();
        VIEWPORT.rewind();
        if (!GLU.gluProject((float) x, (float) y, (float) z, MODEL_VIEW, PROJECTION, VIEWPORT, PROJECTED_POINT)) {
            return null;
        }
        int scaleFactor = new ScaledResolution(mc).getScaleFactor();
        return new float[]{PROJECTED_POINT.get(0) / scaleFactor,
                (mc.displayHeight - PROJECTED_POINT.get(1)) / scaleFactor, PROJECTED_POINT.get(2)};
    }

    private void renderOverlayEntries() {
        if (!isInGame() || !is2DMode() || overlayEntries.isEmpty()) {
            return;
        }
        for (OverlayEntry entry : overlayEntries) {
            if (entry.entity.isDead || entry.entity.deathTime > 0) {
                continue;
            }
            int color = EspOverlayGeometry.applyOpacity(getColor(entry.entity, 0), entry.opacity);
            draw2DBox(entry.bounds, color, entry.opacity);
            if (Boolean.TRUE.equals(healthBar.getValue())) {
                drawHealthBar(entry);
            }
            if (Boolean.TRUE.equals(nameTags.getValue())) {
                drawCenteredText(entry.entity.getDisplayName().getFormattedText(), entry.bounds.minX,
                        entry.bounds.maxX, entry.bounds.minY - mc.fontRendererObj.FONT_HEIGHT - 3.0f, entry.opacity, true);
            }
            if (Boolean.TRUE.equals(heldItem.getValue())) {
                ItemStack stack = entry.entity.getHeldItem();
                if (stack != null) {
                    drawCenteredText(stack.getDisplayName(), entry.bounds.minX, entry.bounds.maxX,
                            entry.bounds.maxY + 3.0f, entry.opacity, false);
                }
            }
        }
    }

    private void draw2DBox(EspOverlayGeometry.Bounds bounds, int color, float opacity) {
        float thickness = lineWidth.getValue().floatValue();
        int shadow = EspOverlayGeometry.applyOpacity(0xD9000000, opacity);
        if (boxMode.getValue() == EspBoxMode.TWO_D) {
            drawRectangleBorder(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY, thickness + 1.5f, shadow);
            drawRectangleBorder(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY, thickness, color);
            return;
        }
        boolean opposite = boxMode.getValue() == EspBoxMode.TWO_D_HALF_CORNERS
                && Boolean.TRUE.equals(oppositeCorners.getValue());
        drawCornerBox(bounds, thickness + 1.5f, shadow, opposite);
        drawCornerBox(bounds, thickness, color, opposite);
    }

    private void drawRectangleBorder(float minX, float minY, float maxX, float maxY, float thickness, int color) {
        RenderUtil.drawRect(minX - thickness, minY - thickness, maxX + thickness, minY, color);
        RenderUtil.drawRect(minX - thickness, maxY, maxX + thickness, maxY + thickness, color);
        RenderUtil.drawRect(minX - thickness, minY, minX, maxY, color);
        RenderUtil.drawRect(maxX, minY, maxX + thickness, maxY, color);
    }

    private void drawCornerBox(EspOverlayGeometry.Bounds bounds, float thickness, int color, boolean opposite) {
        float width = bounds.maxX - bounds.minX;
        float height = bounds.maxY - bounds.minY;
        float horizontal = Math.min(width * 0.33f, 18.0f);
        float vertical = Math.min(height * 0.25f, 24.0f);
        boolean half = boxMode.getValue() == EspBoxMode.TWO_D_HALF_CORNERS;
        if (!half || !opposite) {
            drawCorner(bounds.minX, bounds.minY, 1, 1, horizontal, vertical, thickness, color);
            drawCorner(bounds.maxX, bounds.maxY, -1, -1, horizontal, vertical, thickness, color);
        }
        if (!half || opposite) {
            drawCorner(bounds.maxX, bounds.minY, -1, 1, horizontal, vertical, thickness, color);
            drawCorner(bounds.minX, bounds.maxY, 1, -1, horizontal, vertical, thickness, color);
        }
    }

    private void drawCorner(float x, float y, int horizontalDirection, int verticalDirection,
                            float horizontalLength, float verticalLength, float thickness, int color) {
        float horizontalEnd = x + horizontalLength * horizontalDirection;
        float verticalEnd = y + verticalLength * verticalDirection;
        RenderUtil.drawRect(Math.min(x, horizontalEnd), y - thickness * 0.5f,
                Math.max(x, horizontalEnd), y + thickness * 0.5f, color);
        RenderUtil.drawRect(x - thickness * 0.5f, Math.min(y, verticalEnd),
                x + thickness * 0.5f, Math.max(y, verticalEnd), color);
    }

    private void drawHealthBar(OverlayEntry entry) {
        float health = MathHelper.clamp_float(entry.entity.getHealth() / Math.max(1.0f, entry.entity.getMaxHealth()), 0.0f, 1.0f);
        float barX = entry.bounds.minX - 4.0f;
        float bottom = entry.bounds.maxY;
        float top = entry.bounds.minY;
        int shadow = EspOverlayGeometry.applyOpacity(0xD9000000, entry.opacity);
        RenderUtil.drawRect(barX - 1.0f, top - 1.0f, barX + 2.0f, bottom + 1.0f, shadow);
        RenderUtil.drawRect(barX, bottom - (bottom - top) * health, barX + 1.0f, bottom,
                EspOverlayGeometry.applyOpacity(healthColor(health), entry.opacity));
    }

    private void drawCenteredText(String text, float minX, float maxX, float y, float opacity, boolean primary) {
        if (text == null || text.length() == 0) {
            return;
        }
        int base = Boolean.TRUE.equals(paletteColors.getValue())
                ? (primary ? ClickGUI.currentPalette().getTextPrimary() : ClickGUI.currentPalette().getTextSecondary())
                : 0xFFFFFFFF;
        int color = EspOverlayGeometry.applyOpacity(base, opacity);
        float x = (minX + maxX - mc.fontRendererObj.getStringWidth(text)) * 0.5f;
        mc.fontRendererObj.drawStringWithShadow(text, x, y, color);
    }

    private int healthColor(float health) {
        if (!Boolean.TRUE.equals(paletteColors.getValue())) {
            return health > 0.5f ? 0xFF67D992 : health > 0.25f ? 0xFFFFC65B : 0xFFFF5E70;
        }
        VisualPalette palette = ClickGUI.currentPalette();
        return health > 0.55f
                ? blend(palette.getHealthMid(), palette.getHealthHigh(), (health - 0.55f) / 0.45f)
                : blend(palette.getHealthLow(), palette.getHealthMid(), health / 0.55f);
    }

    private int blend(int first, int second, float progress) {
        float factor = MathHelper.clamp_float(progress, 0.0f, 1.0f);
        int red = Math.round(((first >>> 16) & 255) + (((second >>> 16) & 255) - ((first >>> 16) & 255)) * factor);
        int green = Math.round(((first >>> 8) & 255) + (((second >>> 8) & 255) - ((first >>> 8) & 255)) * factor);
        int blue = Math.round((first & 255) + ((second & 255) - (first & 255)) * factor);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private int getRenderType() {
        EspBoxMode current = boxMode.getValue();
        if (current == EspBoxMode.OUTLINE) {
            return 2;
        }
        if (current == EspBoxMode.FILLED) {
            return 3;
        }
        return 1;
    }

    private int getColor(EntityLivingBase entity, int rainbowColor) {
        if (Boolean.TRUE.equals(redOnDamage.getValue()) && entity.hurtTime > 0) {
            return withAlpha(ClickGUI.currentPalette().getEntityHurt());
        }
        if (Boolean.TRUE.equals(paletteColors.getValue())) {
            return withAlpha(paletteColorFor(entity));
        }
        if (Boolean.TRUE.equals(rainbow.getValue())) {
            return rainbowColor;
        }
        return withAlpha(0xFF000000 | clampColor(red.getValue().intValue()) << 16
                | clampColor(green.getValue().intValue()) << 8
                | clampColor(blue.getValue().intValue()));
    }

    private int paletteColorFor(EntityLivingBase entity) {
        VisualPalette palette = ClickGUI.currentPalette();
        if (entity.isInvisible()) {
            return palette.getEntityInvisible();
        }
        if (entity instanceof EntityPlayer) {
            return palette.getEntityPlayer();
        }
        if (entity instanceof EntityAnimal || entity instanceof EntityWaterMob || entity instanceof EntityAmbientCreature) {
            return palette.getEntityAnimal();
        }
        return palette.getEntityMob();
    }

    private int withAlpha(int color) {
        return (color & 0x00FFFFFF) | (clampColor(alpha.getValue().intValue()) << 24);
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static final class OverlayEntry {
        private final EntityLivingBase entity;
        private final EspOverlayGeometry.Bounds bounds;
        private final float opacity;

        private OverlayEntry(EntityLivingBase entity, EspOverlayGeometry.Bounds bounds, float opacity) {
            this.entity = entity;
            this.bounds = bounds;
            this.opacity = opacity;
        }
    }
}
