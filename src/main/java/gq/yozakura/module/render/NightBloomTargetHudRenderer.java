package gq.yozakura.module.render;

import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.engine.render.ui.VisualPalette;
import gq.yozakura.util.render.HudDockingCoordinator;
import gq.yozakura.util.render.HudDrag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

final class NightBloomTargetHudRenderer {
    static final float WIDTH = 190.0F;
    static final float HEIGHT = 48.0F;
    private static final float AVATAR_TILE_WIDTH = 46.0F;
    private static final float FUSION_GAP = 6.0F;

    private static final VisualPalette PALETTE = VisualPalette.nightBloom();
    private final Minecraft minecraft = Minecraft.getMinecraft();

    void draw(float x, float y, float uiScale, Content current, Content previous,
              TargetHudMotion motion, boolean editMode, boolean showAvatar) {
        float panelAlpha = editMode ? 1.0F : smoothStep(motion.getVisibility());
        if (panelAlpha <= 0.001F || current == null) {
            return;
        }

        float width = WIDTH * uiScale;
        float height = HEIGHT * uiScale;
        boolean dockGeometryLocked = hasDockingLink("target_hud");
        float drawY = dockGeometryLocked || editMode ? y : y + motion.getPanelYOffset() * uiScale;
        float panelScale = dockGeometryLocked || editMode ? 1.0F : motion.getPanelScale();
        float radius = NightBloomHudLayout.PANEL_RADIUS * uiScale;
        boolean externallyDocked = dockGeometryLocked;

        if (externallyDocked) {
            NightBloomHudDockRenderer.drawPanel("target_hud", x, drawY, width, height, radius, panelAlpha,
                    multiplyAlpha(NightBloomHudLayout.SURFACE_COLOR, 0.86F));
        }

        GlStateManager.pushMatrix();
        try {
            if (Math.abs(panelScale - 1.0F) > 0.0001F) {
                float centerX = x + width * 0.5F;
                float centerY = drawY + height * 0.5F;
                GlStateManager.translate(centerX, centerY, 0.0F);
                GlStateManager.scale(panelScale, panelScale, 1.0F);
                GlStateManager.translate(-centerX, -centerY, 0.0F);
            }

            float fusionProgress = editMode ? 1.0F : smoothStep(motion.getVisibility());
            float joinGap = showAvatar ? FUSION_GAP * uiScale * (1.0F - fusionProgress) : 0.0F;
            if (externallyDocked) {
                drawDockedAvatarWell(x, drawY, width, height, radius, uiScale, panelAlpha, showAvatar);
            } else if (showAvatar) {
                drawFusedPanel(x, drawY, width, height, radius, uiScale, panelAlpha, fusionProgress, joinGap);
            } else {
                drawPanel(x, drawY, width, height, radius, uiScale, panelAlpha);
            }
            drawHealth(x, drawY, width, uiScale, motion, panelAlpha, showAvatar, joinGap);

            float previousAlpha = editMode ? 0.0F : motion.getPreviousContentAlpha();
            if (previous != null && previousAlpha > 0.001F) {
                drawContent(previous, x, drawY, width, uiScale, previousAlpha, showAvatar, joinGap);
            }
            float currentAlpha = editMode ? 1.0F : motion.getCurrentContentAlpha();
            if (currentAlpha > 0.001F) {
                drawContent(current, x, drawY, width, uiScale, currentAlpha, showAvatar, joinGap);
            }
        } finally {
            GlStateManager.popMatrix();
            resetRenderState();
        }
    }

    private static boolean hasDockingLink(String id) {
        HudDockingCoordinator.Snapshot snapshot = HudDrag.getDockingSnapshot();
        return snapshot != null && snapshot.hasLink(id);
    }

    private void drawDockedAvatarWell(float x, float y, float width, float height, float radius,
                                      float uiScale, float alpha, boolean showAvatar) {
        if (!showAvatar) {
            return;
        }
        float avatarWidth = Math.min(width * 0.45F, AVATAR_TILE_WIDTH * uiScale);
        float inset = Math.max(2.0F * uiScale, radius * 0.5F);
        if (avatarWidth <= inset * 2.0F || height <= inset * 2.0F) {
            return;
        }
        RenderServices.shapes().rounded(x + inset, y + inset, x + avatarWidth - inset, y + height - inset,
                Math.max(2.0F * uiScale, radius * 0.72F),
                multiplyAlpha(NightBloomHudLayout.SURFACE_RAISED_COLOR, 0.54F * alpha));
    }

    private void drawPanel(float x, float y, float width, float height, float radius,
                           float uiScale, float alpha) {
        HUD.drawNightBloomShadow(x, y, x + width, y + height, radius, alpha);
        RenderServices.shapes().rounded(x, y, x + width, y + height, radius,
                multiplyAlpha(NightBloomHudLayout.SURFACE_COLOR, 0.86F * alpha));
    }

    private void drawFusedPanel(float x, float y, float width, float height, float radius, float uiScale,
                                float alpha, float fusionProgress, float joinGap) {
        float avatarWidth = Math.min(width * 0.45F, AVATAR_TILE_WIDTH * uiScale);
        float bodyWidth = width - avatarWidth - joinGap;
        if (avatarWidth <= 0.01F || bodyWidth <= 0.01F) {
            drawPanel(x, y, width, height, radius, uiScale, alpha);
            return;
        }

        NightBloomWatermarkLayout.TileView avatar = new NightBloomWatermarkLayout.TileView(
                NightBloomWatermarkLayout.Tile.BRAND, x, y, x, y, avatarWidth, height);
        NightBloomWatermarkLayout.TileView body = new NightBloomWatermarkLayout.TileView(
                NightBloomWatermarkLayout.Tile.VERSION, x + avatarWidth + joinGap, y,
                x + avatarWidth + joinGap, y, bodyWidth, height);
        NightBloomWatermarkLayout.LinkView link = new NightBloomWatermarkLayout.LinkView(
                NightBloomWatermarkLayout.Tile.BRAND, NightBloomWatermarkLayout.Tile.VERSION,
                NightBloomWatermarkLayout.Placement.RIGHT_OF,
                NightBloomWatermarkLayout.CrossAlignment.START, fusionProgress, false);
        NightBloomWatermarkLiquid.Bridge bridge = NightBloomWatermarkLiquid.bridge(avatar, body, link);
        List<NightBloomWatermarkLiquid.Bridge> bridges = new ArrayList<NightBloomWatermarkLiquid.Bridge>();
        if (bridge.getProgress() > 0.01F) {
            bridges.add(bridge);
        }
        List<NightBloomWatermarkLayout.TileView> tiles = new ArrayList<NightBloomWatermarkLayout.TileView>();
        tiles.add(avatar);
        tiles.add(body);
        List<NightBloomWatermarkLiquid.Composite> composites = NightBloomWatermarkLiquid.composites(tiles, bridges);

        drawFusedShadows(tiles, bridges, composites, radius, alpha);
        drawFusedSurfaces(tiles, bridges, composites, radius, alpha);
    }

    private void drawFusedShadows(List<NightBloomWatermarkLayout.TileView> tiles,
                                  List<NightBloomWatermarkLiquid.Bridge> bridges,
                                  List<NightBloomWatermarkLiquid.Composite> composites,
                                  float radius, float alpha) {
        for (NightBloomWatermarkLayout.TileView tile : tiles) {
            float opacity = 1.0F - fusedCompositeProgress(tile.getTile(), composites);
            if (opacity > 0.01F) {
                HUD.drawNightBloomShadow(tile.getX(), tile.getY(), tile.getRight(), tile.getBottom(), radius,
                        alpha * opacity);
            }
        }
        for (NightBloomWatermarkLiquid.Bridge bridge : bridges) {
            if (bridge.isVisible()) {
                HUD.drawNightBloomShadow(bridge.getX(), bridge.getY(), bridge.getRight(), bridge.getBottom(),
                        bridge.getRadius(), alpha * bridge.getOpacity() * (1.0F - bridge.getCompositeProgress()));
            }
        }
        for (NightBloomWatermarkLiquid.Composite composite : composites) {
            if (composite.getProgress() > 0.01F) {
                HUD.drawNightBloomShadow(composite.getX(), composite.getY(), composite.getRight(),
                        composite.getBottom(), radius, alpha * composite.getProgress());
            }
        }
    }

    private void drawFusedSurfaces(List<NightBloomWatermarkLayout.TileView> tiles,
                                   List<NightBloomWatermarkLiquid.Bridge> bridges,
                                   List<NightBloomWatermarkLiquid.Composite> composites,
                                   float radius, float alpha) {
        for (NightBloomWatermarkLiquid.Bridge bridge : bridges) {
            float opacity = alpha * bridge.getOpacity() * (1.0F - bridge.getCompositeProgress());
            if (bridge.isVisible() && opacity > 0.01F) {
                RenderServices.shapes().joinedRounded(bridge.getX(), bridge.getY(), bridge.getRight(),
                        bridge.getBottom(), bridge.getRadius(), bridge.getRadius(), bridge.getRadius(),
                        bridge.getRadius(), multiplyAlpha(NightBloomHudLayout.SURFACE_COLOR, 0.86F * opacity));
            }
        }
        for (NightBloomWatermarkLayout.TileView tile : tiles) {
            float opacity = 1.0F - fusedCompositeProgress(tile.getTile(), composites);
            if (opacity <= 0.01F) {
                continue;
            }
            NightBloomWatermarkLiquid.Surface surface = NightBloomWatermarkLiquid.surfaceFor(tile, bridges, radius);
            RenderServices.shapes().joinedRounded(tile.getX(), tile.getY(), tile.getRight(), tile.getBottom(),
                    surface.getTopLeft(), surface.getTopRight(), surface.getBottomRight(), surface.getBottomLeft(),
                    surface.getTopJoinStart(), surface.getTopJoinEnd(),
                    surface.getBottomJoinStart(), surface.getBottomJoinEnd(),
                    surface.getLeftJoinStart(), surface.getLeftJoinEnd(),
                    surface.getRightJoinStart(), surface.getRightJoinEnd(),
                    multiplyAlpha(NightBloomHudLayout.SURFACE_COLOR, 0.86F * alpha * opacity));
        }
        for (NightBloomWatermarkLiquid.Composite composite : composites) {
            float opacity = fusedCompositeSurfaceOpacity(alpha, composite.getProgress());
            if (opacity > 0.01F) {
                RenderServices.shapes().joinedRounded(composite.getX(), composite.getY(), composite.getRight(),
                        composite.getBottom(), radius, radius, radius, radius,
                        multiplyAlpha(NightBloomHudLayout.SURFACE_COLOR, 0.86F * alpha * opacity));
            }
        }
    }

    private static float fusedCompositeProgress(NightBloomWatermarkLayout.Tile tile,
                                                List<NightBloomWatermarkLiquid.Composite> composites) {
        float progress = 0.0F;
        for (NightBloomWatermarkLiquid.Composite composite : composites) {
            if (composite.contains(tile)) {
                progress = Math.max(progress, composite.getProgress());
            }
        }
        return progress;
    }

    static float fusedCompositeSurfaceOpacity(float alpha, float progress) {
        float baseOpacity = 0.86F;
        float desiredOpacity = baseOpacity * clamp01(alpha);
        if (desiredOpacity <= 0.0001F) {
            return 0.0F;
        }
        float individualOpacity = desiredOpacity * (1.0F - clamp01(progress));
        float compositeOpacity = (desiredOpacity - individualOpacity)
                / Math.max(0.0001F, 1.0F - individualOpacity);
        return compositeOpacity / desiredOpacity;
    }

    private void drawContent(Content content, float x, float y, float width, float uiScale,
                             float alpha, boolean showAvatar, float joinGap) {
        if (showAvatar) {
            drawPortrait(content, x + 10.0F * uiScale, y + 10.0F * uiScale,
                    28.0F * uiScale, uiScale, alpha);
        }

        CFontRenderer nameFont = FontLoaders.tenacityBold(Math.max(12, Math.round(15.0F * uiScale)));
        CFontRenderer metaFont = FontLoaders.regular(Math.max(9, Math.round(10.0F * uiScale)));
        CFontRenderer valueFont = FontLoaders.regular(Math.max(9, Math.round(10.0F * uiScale)));
        float textX = x + (showAvatar ? AVATAR_TILE_WIDTH : 10.0F) * uiScale + joinGap;
        float right = x + width - 10.0F * uiScale;
        String healthText = Math.round(content.healthRatio * 100.0F) + "%";
        float healthWidth = valueFont.getStringWidth(healthText);
        float maxNameWidth = Math.max(30.0F * uiScale,
                right - textX - healthWidth - 8.0F * uiScale);

        HUD.drawNightBloomText(nameFont, trim(content.name, nameFont, maxNameWidth),
                textX, y + 7.0F * uiScale,
                multiplyAlpha(NightBloomHudLayout.PRIMARY_COLOR, alpha),
                multiplyAlpha(NightBloomHudLayout.PRIMARY_COLOR, 0.68F * alpha), 0.48F);
        HUD.drawNightBloomText(valueFont, healthText, right - healthWidth, y + 8.0F * uiScale,
                multiplyAlpha(NightBloomHudLayout.SECONDARY_COLOR, alpha),
                multiplyAlpha(NightBloomHudLayout.PRIMARY_COLOR, 0.24F * alpha), 0.20F);
        HUD.drawNightBloomText(metaFont, trim(content.metadata, metaFont, right - textX),
                textX, y + 23.0F * uiScale,
                multiplyAlpha(NightBloomHudLayout.SECONDARY_COLOR, alpha),
                multiplyAlpha(NightBloomHudLayout.PRIMARY_COLOR, 0.20F * alpha), 0.17F);
    }

    private void drawHealth(float x, float y, float width, float uiScale,
                            TargetHudMotion motion, float alpha, boolean showAvatar, float joinGap) {
        float barX = x + (showAvatar ? AVATAR_TILE_WIDTH : 10.0F) * uiScale + joinGap;
        float barY = y + 37.0F * uiScale;
        float barWidth = width - (showAvatar ? 56.0F : 20.0F) * uiScale - joinGap;
        float barHeight = Math.max(2.5F, 3.0F * uiScale);
        float radius = barHeight * 0.5F;

        RenderServices.shapes().rounded(barX, barY, barX + barWidth, barY + barHeight, radius,
                multiplyAlpha(PALETTE.getSurfaceOverlay(), 0.78F * alpha));
        float damageWidth = barWidth * clamp01(motion.getDamageTrail());
        if (damageWidth > 0.5F) {
            RenderServices.shapes().rounded(barX, barY, barX + damageWidth, barY + barHeight, radius,
                    multiplyAlpha(PALETTE.getHealthDamageTrail(), alpha));
        }
        float healthWidth = barWidth * clamp01(motion.getHealth());
        if (healthWidth > 0.5F) {
            int color = healthColor(motion.getHealth());
            RenderServices.shapes().rounded(barX, barY, barX + healthWidth, barY + barHeight, radius,
                    multiplyAlpha(color, alpha));
        }
    }

    private void drawPortrait(Content content, float x, float y, float size, float uiScale, float alpha) {
        float radius = 4.0F * uiScale;
        RenderServices.shapes().rounded(x, y, x + size, y + size, radius,
                multiplyAlpha(NightBloomHudLayout.SURFACE_RAISED_COLOR, alpha));

        ResourceLocation skin = content.entity instanceof AbstractClientPlayer
                ? ((AbstractClientPlayer) content.entity).getLocationSkin() : null;
        if (skin != null) {
            drawPlayerHead(skin, x + 2.0F * uiScale, y + 2.0F * uiScale,
                    size - 4.0F * uiScale, 4.5F * uiScale, alpha);
        } else {
            drawEntityBadge(content.entity, x, y, size, uiScale, alpha);
        }
        if (content.hurt) {
            RenderServices.shapes().rounded(x + 1.0F * uiScale, y + 1.0F * uiScale,
                    x + size - 1.0F * uiScale, y + size - 1.0F * uiScale,
                    Math.max(1.0F, radius - 1.0F * uiScale),
                    multiplyAlpha(PALETTE.getDanger(), 0.18F * alpha));
        }
    }

    private void drawPlayerHead(ResourceLocation skin, float x, float y, float size, float radius, float alpha) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int pixelSize = Math.max(1, Math.round(size));
        GlStateManager.pushMatrix();
        try {
            RenderServices.stencil().initWrite();
            RenderServices.shapes().rounded(ix, iy, ix + pixelSize, iy + pixelSize, radius, 0xFFFFFFFF);
            RenderServices.stencil().read(1);
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
            minecraft.getTextureManager().bindTexture(skin);
            Gui.drawScaledCustomSizeModalRect(ix, iy, 8.0F, 8.0F, 8, 8,
                    pixelSize, pixelSize, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect(ix, iy, 40.0F, 8.0F, 8, 8,
                    pixelSize, pixelSize, 64.0F, 64.0F);
        } finally {
            RenderServices.stencil().end();
            GlStateManager.popMatrix();
            resetRenderState();
        }
    }

    private void drawEntityBadge(EntityLivingBase entity, float x, float y, float size,
                                 float uiScale, float alpha) {
        CFontRenderer iconFont = FontLoaders.icon(Math.max(13, Math.round(16.0F * uiScale)));
        String icon = entity instanceof EntityPlayer ? FontLoaders.ICON_USER
                : entity instanceof EntityAnimal || entity instanceof EntityWaterMob
                || entity instanceof EntityAmbientCreature ? FontLoaders.ICON_HEARTBEAT
                : entity instanceof EntityMob || entity instanceof EntitySlime || entity instanceof IMob
                ? FontLoaders.ICON_WARNING : FontLoaders.ICON_CUBE;
        HUD.drawNightBloomCenteredIcon(icon, iconFont, x + size * 0.5F, y + size * 0.5F,
                multiplyAlpha(NightBloomHudLayout.PRIMARY_COLOR, alpha),
                multiplyAlpha(NightBloomHudLayout.PRIMARY_COLOR, 0.66F * alpha), 0.58F);
    }

    private static int healthColor(float health) {
        float value = clamp01(health);
        if (value <= 0.30F) {
            return PALETTE.getHealthLow();
        }
        if (value <= 0.60F) {
            return PALETTE.getHealthMid();
        }
        return PALETTE.getHealthHigh();
    }

    private static String trim(String text, CFontRenderer font, float maxWidth) {
        if (text == null || maxWidth <= 0.0F) {
            return "";
        }
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 1 && font.getStringWidth(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result.length() <= 1 ? "..." : result + "...";
    }

    private static int multiplyAlpha(int color, float alpha) {
        int sourceAlpha = color >>> 24 & 255;
        int resolvedAlpha = Math.round(sourceAlpha * clamp01(alpha));
        return color & 0x00FFFFFF | resolvedAlpha << 24;
    }

    private static float smoothStep(float value) {
        float t = clamp01(value);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static void resetRenderState() {
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    static final class Content {
        private final EntityLivingBase entity;
        private final int entityId;
        private final String name;
        private final String metadata;
        private final float healthRatio;
        private final boolean hurt;

        Content(EntityLivingBase entity, int entityId, String name, String metadata,
                float healthRatio, boolean hurt) {
            this.entity = entity;
            this.entityId = entityId;
            this.name = name == null || name.length() == 0 ? "Unknown" : name;
            this.metadata = metadata == null ? "" : metadata;
            this.healthRatio = clamp01(healthRatio);
            this.hurt = hurt;
        }

        int getEntityId() {
            return entityId;
        }

        float getHealthRatio() {
            return healthRatio;
        }
    }
}
