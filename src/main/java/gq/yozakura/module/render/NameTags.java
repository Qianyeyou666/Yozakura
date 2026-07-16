package gq.yozakura.module.render;

import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.glow.GlowRenderer;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.engine.render.ui.VisualPalette;
import gq.yozakura.event.bridge.Render3DEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.combat.AntiBot;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

/**
 * Compact NightBloom replacement for vanilla player name plates.
 */
public final class NameTags extends Module {
    private static final int PANEL_COLOR = 0xDC16161A;
    private static final int HEALTH_TRACK_COLOR = 0x70000000;
    private static final float PANEL_RADIUS = 4.0F;
    private static final float TEXT_GLOW_STRENGTH = 0.82F;

    private final Option<Boolean> showHealth = new Option<Boolean>("Health", "Health", true);
    private final Option<Boolean> showDistance = new Option<Boolean>("Distance", "Distance", true);
    private final Option<Boolean> showHealthBar = new Option<Boolean>("Health Bar", "HealthBar", true);
    private final Option<Boolean> throughWalls = new Option<Boolean>("Through Walls", "ThroughWalls", true);
    private final Option<Boolean> invisibles = new Option<Boolean>("Invisibles", "Invisibles", false);
    private final Numbers<Double> scale = new Numbers<Double>("Scale", "Scale", 1.0D, 0.65D, 1.6D, 0.05D);
    private final Numbers<Double> maxDistance =
            new Numbers<Double>("Max Distance", "MaxDistance", 96.0D, 16.0D, 192.0D, 4.0D);

    public NameTags() {
        super("NameTags", Keyboard.KEY_NONE, ModuleType.Render, "Show NightBloom player name tags");
        this.addValues(showHealth, showDistance, showHealthBar, throughWalls, invisibles, scale, maxDistance);
        Chinese = "名称标签";
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isInGame() || mc.getRenderManager() == null) {
            return;
        }

        GlowRenderer shadows = RenderServices.shadows();
        GlowRenderer glow = RenderServices.glow();
        boolean ownsShadowFrame = !shadows.isFrameOpen();
        boolean ownsGlowFrame = HUD.isGlowEnabled() && !glow.isFrameOpen();
        if (ownsShadowFrame) {
            RenderServices.shadows().beginFrame();
        }
        if (ownsGlowFrame) {
            RenderServices.glow().beginFrame();
        }
        try {
            for (Object object : mc.theWorld.playerEntities) {
                if (!(object instanceof EntityPlayer)) {
                    continue;
                }
                EntityPlayer player = (EntityPlayer) object;
                float distance = mc.thePlayer.getDistanceToEntity(player);
                if (isNameTagTarget(player) && distance <= maxDistance.getValue().floatValue()) {
                    drawNameTag(player, event.getPartialTicks(), distance);
                }
            }
        } finally {
            if (ownsShadowFrame && shadows.isFrameOpen()) {
                shadows.flush();
            }
            if (ownsGlowFrame && glow.isFrameOpen()) {
                glow.flush();
            }
        }
    }

    @SubscribeEvent
    public void onForgeNameTag(net.minecraftforge.client.event.RenderLivingEvent.Specials.Pre event) {
        if (event.entity instanceof EntityPlayer && isNameTagTarget((EntityPlayer) event.entity)) {
            event.setCanceled(true);
        }
    }

    @EventTarget
    public void onStandaloneNameTag(gq.yozakura.bridge.forge.RenderLivingEvent.Specials.Pre event) {
        if (event.entity instanceof EntityPlayer && isNameTagTarget((EntityPlayer) event.entity)) {
            event.setCanceled(true);
        }
    }

    private void drawNameTag(EntityPlayer player, float partialTicks, float distance) {
        String playerName = player.getDisplayName().getUnformattedText();
        String healthText = String.format(Locale.ROOT, "%.1f HP", player.getHealth());
        String distanceText = Math.round(distance) + "m";
        CFontRenderer nameFont = FontLoaders.TB16;
        CFontRenderer metadataFont = FontLoaders.C14;
        boolean healthVisible = Boolean.TRUE.equals(showHealth.getValue());
        boolean distanceVisible = Boolean.TRUE.equals(showDistance.getValue());
        NightBloomNameTagLayout.Layout layout = NightBloomNameTagLayout.measure(
                nameFont.getStringWidth(playerName), metadataFont.getStringWidth(healthText),
                metadataFont.getStringWidth(distanceText), healthVisible, distanceVisible);

        double x = interpolate(player.lastTickPosX, player.posX, partialTicks)
                - mc.getRenderManager().viewerPosX;
        double y = interpolate(player.lastTickPosY, player.posY, partialTicks)
                - mc.getRenderManager().viewerPosY + player.height + 0.56D;
        double z = interpolate(player.lastTickPosZ, player.posZ, partialTicks)
                - mc.getRenderManager().viewerPosZ;
        float worldScale = NightBloomNameTagLayout.worldScale(distance, scale.getValue().floatValue());

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            float pitch = mc.gameSettings.thirdPersonView == 2
                    ? -mc.getRenderManager().playerViewX : mc.getRenderManager().playerViewX;
            GlStateManager.rotate(pitch, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-worldScale, -worldScale, worldScale);
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.depthMask(false);
            if (Boolean.TRUE.equals(throughWalls.getValue())) {
                GlStateManager.disableDepth();
            } else {
                GlStateManager.enableDepth();
            }
            GlStateManager.enableTexture2D();

            drawPanel(player, playerName, healthText, distanceText, layout,
                    healthVisible, distanceVisible);
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
            gq.yozakura.engine.render.GLStateManager.syncToCurrent();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void drawPanel(EntityPlayer player, String playerName, String healthText, String distanceText,
                           NightBloomNameTagLayout.Layout layout,
                           boolean healthVisible, boolean distanceVisible) {
        float left = -layout.getWidth() * 0.5F;
        float top = -layout.getHeight();
        float right = left + layout.getWidth();
        float bottom = 0.0F;
        VisualPalette palette = ClickGUI.currentPalette();
        float healthFraction = NightBloomNameTagLayout.healthFraction(player.getHealth(), player.getMaxHealth());
        int healthColor = healthColor(palette, healthFraction);

        HUD.drawNightBloomShadow(left, top, right, bottom, PANEL_RADIUS, 0.72F);
        RenderServices.shapes().rounded(left, top, right, bottom, PANEL_RADIUS, PANEL_COLOR);

        String visibleName = trim(playerName, FontLoaders.TB16, layout.getNameMaxWidth());
        HUD.drawNightBloomText(FontLoaders.TB16, visibleName,
                left + NightBloomNameTagLayout.HORIZONTAL_PADDING, top + 5.0F,
                NightBloomHudLayout.PRIMARY_COLOR, NightBloomHudLayout.PRIMARY_COLOR, TEXT_GLOW_STRENGTH);
        if (healthVisible) {
            HUD.drawNightBloomText(FontLoaders.C14, healthText,
                    left + layout.getHealthX(), top + 7.0F,
                    healthColor, healthColor, 0.68F);
        }
        if (distanceVisible) {
            HUD.drawNightBloomText(FontLoaders.C14, distanceText,
                    left + layout.getDistanceX(), top + 7.0F,
                    NightBloomHudLayout.SECONDARY_COLOR, NightBloomHudLayout.SECONDARY_COLOR, 0.56F);
        }

        if (Boolean.TRUE.equals(showHealthBar.getValue())) {
            float barLeft = left + 2.5F;
            float barRight = right - 2.5F;
            float barTop = bottom - 2.25F;
            RenderServices.shapes().rounded(barLeft, barTop, barRight, bottom - 0.75F,
                    0.75F, HEALTH_TRACK_COLOR);
            float fillRight = barLeft + (barRight - barLeft) * healthFraction;
            if (fillRight > barLeft + 0.05F) {
                RenderServices.shapes().rounded(barLeft, barTop, fillRight, bottom - 0.75F,
                        0.75F, withAlpha(healthColor, 224));
            }
        }
    }

    private boolean isNameTagTarget(EntityPlayer player) {
        return isInGame() && player != null && player != mc.thePlayer && !player.isDead
                && player.deathTime <= 0 && (!player.isInvisible() || Boolean.TRUE.equals(invisibles.getValue()))
                && !AntiBot.isServerBot(player);
    }

    private static int healthColor(VisualPalette palette, float fraction) {
        if (fraction > 0.60F) {
            return palette.getHealthHigh();
        }
        if (fraction > 0.30F) {
            return palette.getHealthMid();
        }
        return palette.getHealthLow();
    }

    private static String trim(String text, CFontRenderer font, float maximumWidth) {
        if (font.getStringWidth(text) <= maximumWidth) {
            return text;
        }
        String suffix = "...";
        String visible = text;
        while (visible.length() > 1 && font.getStringWidth(visible + suffix) > maximumWidth) {
            visible = visible.substring(0, visible.length() - 1);
        }
        return visible + suffix;
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }
}
