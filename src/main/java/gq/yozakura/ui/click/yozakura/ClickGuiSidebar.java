package gq.yozakura.ui.click.yozakura;

import gq.yozakura.core.Client;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.animation.AnimationState;
import gq.yozakura.util.animation.AnimationUtil;

import java.util.List;

/**
 * Sidebar of the Nether v2.1 ClickGUI.
 *
 * <p>Contains:
 * <ul>
 *   <li>A "CATEGORIES" label</li>
 *   <li>Capsule category buttons with module-count badges</li>
 *   <li>A profile card pinned to the bottom (avatar + username + premium tag + UID)</li>
 * </ul>
 *
 * <p>The sidebar background uses {@link ClickGuiTheme#SIDEBAR}, and active category
 * is highlighted with the accent color and a left accent bar (animated in).
 */
public final class ClickGuiSidebar {
    private static final float LABEL_PAD_X = 10f;
    private static final float LABEL_PAD_TOP = 4f;
    private static final float CARD_PAD_X = 13f;
    private static final float CARD_PAD_Y = 9f;
    private static final float CARD_GAP = 6f;
    private static final float CARD_H = 38f;
    private static final float ICON_SIZE = 18f;
    private static final float BADGE_PAD_X = 7f;
    private static final float BADGE_PAD_Y = 2f;
    private static final float PROFILE_H = 56f;
    private static final float PROFILE_PAD_X = 12f;
    private static final float AVATAR_SIZE = 34f;

    /** Categories shown in the sidebar, in display order. */
    private static final ModuleType[] CATEGORY_ORDER = {
            ModuleType.Combat,
            ModuleType.Movement,
            ModuleType.Render,
            ModuleType.Player,
            ModuleType.World,
            ModuleType.Other
    };

    private final AnimationState anim;
    private ModuleType selected = ModuleType.Combat;
    private ProfileActionListener profileListener;

    public interface ProfileActionListener {
        void onProfileClicked();
    }

    public ClickGuiSidebar(AnimationState anim) {
        this.anim = anim;
    }

    public void setProfileActionListener(ProfileActionListener listener) {
        this.profileListener = listener;
    }

    public ModuleType selected() {
        return selected;
    }

    public void select(ModuleType type) {
        this.selected = type;
    }

    /** Draws the sidebar inside the supplied bounds. */
    public void draw(float x, float y, float w, float h, int mouseX, int mouseY, float frameScale) {
        // Background
        RenderServices.shapes().rect(x, y, x + w, y + h, ClickGuiTheme.SIDEBAR);
        // Top inner highlight (inset 0 1px 0 rgba(255,255,255,.02))
        RenderServices.shapes().horizontalGradient(x + 2f, y + 1f, x + w - 2f, y + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x05));
        RenderServices.shapes().horizontalGradient(x + w / 2f, y + 1f, x + w - 2f, y + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x05),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00));
        // Right edge divider
        RenderServices.shapes().rect(x + w - 1, y, x + w, y + h, ClickGuiTheme.BORDER);

        // "CATEGORIES" label
        float labelY = y + 14f;
        FontLoaders.MONO10.drawString("CATEGORIES", x + LABEL_PAD_X, labelY, ClickGuiTheme.FG_4);

        // Category cards
        float cardX = x + 10f;
        float cardW = w - 20f;
        float cursorY = labelY + 16f;

        for (ModuleType type : CATEGORY_ORDER) {
            boolean active = type == selected;
            boolean hover = mouseX >= cardX && mouseX <= cardX + cardW
                    && mouseY >= cursorY && mouseY <= cursorY + CARD_H;
            drawCategoryCard(cardX, cursorY, cardW, type, active, hover, frameScale, key(type));
            cursorY += CARD_H + CARD_GAP;
        }

        // Profile card pinned to bottom
        float profileY = y + h - PROFILE_H - 10f;
        drawProfileCard(x + 10f, profileY, w - 20f, mouseX, mouseY, frameScale);
    }

    private String key(ModuleType type) {
        return "side-cat:" + type.name();
    }

    private void drawCategoryCard(float x, float y, float w, ModuleType type,
                                  boolean active, boolean hover, float frameScale, String key) {
        // Hover/active animation
        float target = active ? 1f : (hover ? 0.5f : 0f);
        float t = anim.eased(key, target, ClickGuiTheme.SPRING_SPEED, frameScale, 0f,
                AnimationUtil.Ease.OUT_CUBIC);
        float liftT = anim.eased(key + ":lift", hover && !active ? 1f : 0f,
                ClickGuiTheme.SPRING_SPEED, frameScale, 0f, AnimationUtil.Ease.OUT_CUBIC);
        float lift = liftT * 1f;
        float cardY = y - lift;

        // Background — base card color
        int bgBase;
        if (hover && !active) {
            bgBase = ClickGuiTheme.blend(ClickGuiTheme.CARD, ClickGuiTheme.CARD_HOVER, 0.5f);
        } else {
            bgBase = ClickGuiTheme.CARD;
        }
        int border = active
                ? ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x40)
                : ClickGuiTheme.blend(ClickGuiTheme.BORDER, ClickGuiTheme.BORDER_2, t);
        RenderServices.shapes().roundedBorderWH(x, cardY, w, CARD_H, ClickGuiTheme.R_MD,
                1f, bgBase, border);

        // Active: 135deg accent gradient overlay (accent.10 → accent.04)
        if (active) {
            ClickGuiRenderContext.pushScissor(x, cardY, w, CARD_H);
            try {
                RenderServices.shapes().roundedGradient(x, cardY, x + w, cardY + CARD_H,
                        ClickGuiTheme.R_MD,
                        ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x1A),
                        ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x0A),
                        ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x0A),
                        ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x00));
            } finally {
                ClickGuiRenderContext.popScissor();
            }
            // Accent glow shadow (matches design: 0 4px 16px rgba(accent,.10))
            RenderServices.shapes().shadow(x, cardY, x + w, cardY + CARD_H, ClickGuiTheme.R_MD,
                    ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x1A), 3, 4f);
        }

        // Inner top highlight (inset 0 1px 0 rgba(255,255,255,.04))
        RenderServices.shapes().horizontalGradient(x + 2f, cardY + 1f, x + w - 2f, cardY + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x0A));
        RenderServices.shapes().horizontalGradient(x + w / 2f, cardY + 1f, x + w - 2f, cardY + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x0A),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00));

        // Left accent bar (active only) — animated in with spring bounce
        if (active) {
            float barT = anim.eased(key + ":bar", 1f, ClickGuiTheme.SPRING_BOUNCE_SPEED,
                    frameScale, 0f, AnimationUtil.Ease.OUT_BACK);
            float barH = CARD_H * 0.6f * barT;
            float barY = cardY + (CARD_H - barH) / 2f;
            RenderServices.shapes().roundedWH(x - 1f, barY, 3f, barH, 1.5f, ClickGuiTheme.accent());
        }

        // Icon
        float iconX = x + CARD_PAD_X;
        float iconY = cardY + (CARD_H - ICON_SIZE) / 2f;
        int iconColor = active ? ClickGuiTheme.accentHover()
                : (hover ? ClickGuiTheme.FG : ClickGuiTheme.FG_3);
        // Icon hover scale (matches design: transform .3s spring, scale 1.1 on hover)
        float iconScaleT = anim.eased(key + ":icon-scale", hover ? 1f : 0f,
                ClickGuiTheme.SPRING_SPEED, frameScale, 0f, AnimationUtil.Ease.OUT_CUBIC);
        float iconScale = 1f + iconScaleT * 0.1f;
        float iconDrawW = ICON_SIZE * iconScale;
        float iconDrawH = ICON_SIZE * iconScale;
        float iconDrawX = iconX + (ICON_SIZE - iconDrawW) / 2f;
        float iconDrawY = iconY + (ICON_SIZE - iconDrawH) / 2f;
        ClickGuiIconShapes.drawCategory(type, iconDrawX + iconDrawW / 2f,
                iconDrawY + iconDrawH / 2f, iconDrawW, 1.5f, iconColor);

        // Name
        float textX = iconX + ICON_SIZE + 10f;
        int nameColor = active ? ClickGuiTheme.FG : (hover ? ClickGuiTheme.FG : ClickGuiTheme.FG_3);
        FontLoaders.INTER14.drawString(displayName(type), textX, cardY + (CARD_H - 12f) / 2f, nameColor);

        // Module count badge
        int count = countModules(type);
        String countStr = String.valueOf(count);
        float badgeW = FontLoaders.MONO10.getStringWidth(countStr) + BADGE_PAD_X * 2;
        float badgeH = 14f;
        float badgeX = x + w - badgeW - CARD_PAD_X;
        float badgeY = cardY + (CARD_H - badgeH) / 2f;
        int badgeBg = active ? ClickGuiTheme.accentDim() : ClickGuiTheme.withAlpha(0xFFFFFF, 0x0A);
        int badgeText = active ? ClickGuiTheme.accentHover() : ClickGuiTheme.FG_4;
        RenderServices.shapes().roundedWH(badgeX, badgeY, badgeW, badgeH, 5f, badgeBg);
        FontLoaders.MONO10.drawString(countStr,
                badgeX + (badgeW - FontLoaders.MONO10.getStringWidth(countStr)) / 2f,
                badgeY + (badgeH - 10f) / 2f, badgeText);
    }

    private void drawProfileCard(float x, float y, float w, int mouseX, int mouseY, float frameScale) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + PROFILE_H;
        float hoverT = anim.eased("side-profile:hover", hover ? 1f : 0f,
                ClickGuiTheme.SPRING_SPEED, frameScale, 0f, AnimationUtil.Ease.OUT_CUBIC);
        float lift = hoverT * 1f;
        float cardY = y - lift;

        int bg = ClickGuiTheme.CARD;
        int border = ClickGuiTheme.blend(ClickGuiTheme.BORDER, ClickGuiTheme.BORDER_2, hoverT);
        RenderServices.shapes().roundedBorderWH(x, cardY, w, PROFILE_H, ClickGuiTheme.R_MD,
                1f, bg, border);
        // Inner top highlight
        RenderServices.shapes().horizontalGradient(x + 2f, cardY + 1f, x + w - 2f, cardY + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x0A));
        RenderServices.shapes().horizontalGradient(x + w / 2f, cardY + 1f, x + w - 2f, cardY + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x0A),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00));

        // Avatar — accent gradient square with shadow
        float avatarX = x + PROFILE_PAD_X;
        float avatarY = cardY + (PROFILE_H - AVATAR_SIZE) / 2f;
        // Avatar shadow (matches design: 0 2px 8px rgba(accent,.35))
        RenderServices.shapes().shadow(avatarX, avatarY, avatarX + AVATAR_SIZE, avatarY + AVATAR_SIZE,
                ClickGuiTheme.R_SM, ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x59), 3, 3f);
        // Base accent
        RenderServices.shapes().roundedWH(avatarX, avatarY, AVATAR_SIZE, AVATAR_SIZE,
                ClickGuiTheme.R_SM, ClickGuiTheme.accent());
        // 135deg gradient overlay (accentHover top-left → transparent bottom-right)
        ClickGuiRenderContext.pushScissor(avatarX, avatarY, AVATAR_SIZE, AVATAR_SIZE);
        try {
            RenderServices.shapes().roundedGradient(avatarX, avatarY, avatarX + AVATAR_SIZE, avatarY + AVATAR_SIZE,
                    ClickGuiTheme.R_SM,
                    ClickGuiTheme.withAlpha(ClickGuiTheme.accentHover(), 0x66),
                    ClickGuiTheme.withAlpha(ClickGuiTheme.accentHover(), 0x19),
                    ClickGuiTheme.withAlpha(ClickGuiTheme.accentHover(), 0x19),
                    ClickGuiTheme.withAlpha(ClickGuiTheme.accentHover(), 0x00));
        } finally {
            ClickGuiRenderContext.popScissor();
        }
        // Inner top highlight (inset 0 1px 0 rgba(255,255,255,.25))
        RenderServices.shapes().horizontalGradient(avatarX + 2f, avatarY + 1f,
                avatarX + AVATAR_SIZE - 2f, avatarY + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x40));
        RenderServices.shapes().horizontalGradient(avatarX + AVATAR_SIZE / 2f, avatarY + 1f,
                avatarX + AVATAR_SIZE - 2f, avatarY + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x40),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00));

        // Initial letter
        String initial = initialOf(Client.username);
        float textW = FontLoaders.BRICOLAGE16.getStringWidth(initial);
        FontLoaders.BRICOLAGE16.drawString(initial,
                avatarX + (AVATAR_SIZE - textW) / 2f,
                avatarY + (AVATAR_SIZE - 12f) / 2f,
                0xFFFFFFFF);

        // Profile info
        float infoX = avatarX + AVATAR_SIZE + 10f;
        float infoW = x + w - infoX - PROFILE_PAD_X;
        String name = truncate(Client.username, infoW, FontLoaders.INTER12);
        FontLoaders.INTER12.drawString(name, infoX, cardY + 10, ClickGuiTheme.FG);

        // Premium/Online tag — green dot (with glow) + text
        float tagY = cardY + 26f;
        RenderServices.shapes().circle(infoX + 2.5f, tagY + 4.5f, 0, 360, 2.5f, ClickGuiTheme.GREEN);
        // Glow halo around the dot
        RenderServices.shapes().circle(infoX + 2.5f, tagY + 4.5f, 0, 360, 4.5f,
                ClickGuiTheme.withAlpha(ClickGuiTheme.GREEN, 0x33));
        FontLoaders.MONO10.drawString("PREMIUM", infoX + 8f, tagY, ClickGuiTheme.GREEN);

        // UID — separate line below the tag (matches design)
        String uid = "UID: " + formatUid(Client.username);
        FontLoaders.MONO10.drawString(truncate(uid, infoW, FontLoaders.MONO10), infoX, cardY + 41f, ClickGuiTheme.FG_4);
    }

    /** Formats a stable 7-digit UID from the username hash. */
    private static String formatUid(String username) {
        long hash = username == null ? 0L : (long) username.hashCode() & 0xFFFFFFFFL;
        long uid = 1000000L + (hash % 9000000L);
        return String.valueOf(uid);
    }

    /** Handles mouse clicks inside the sidebar. Returns true if consumed. */
    public boolean mouseClicked(float x, float y, float w, float h, int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        if (mouseX < x || mouseX > x + w || mouseY < y || mouseY > y + h) return false;

        float labelY = y + 14f;
        float cardX = x + 10f;
        float cardW = w - 20f;
        float cursorY = labelY + 16f;
        for (ModuleType type : CATEGORY_ORDER) {
            if (mouseX >= cardX && mouseX <= cardX + cardW
                    && mouseY >= cursorY && mouseY <= cursorY + CARD_H) {
                if (selected != type) {
                    selected = type;
                }
                return true;
            }
            cursorY += CARD_H + CARD_GAP;
        }

        // Profile card
        float profileY = y + h - PROFILE_H - 10f;
        if (mouseX >= x + 10f && mouseX <= x + 10f + cardW
                && mouseY >= profileY && mouseY <= profileY + PROFILE_H) {
            if (profileListener != null) {
                profileListener.onProfileClicked();
            }
            return true;
        }
        return false;
    }

    private static int countModules(ModuleType type) {
        List<Module> modules = ModuleManager.getModulesInType(type);
        return modules == null ? 0 : modules.size();
    }

    private static String displayName(ModuleType type) {
        return type == ModuleType.Other ? "Misc" : type.getName();
    }

    private static String initialOf(String name) {
        if (name == null || name.isEmpty()) return "?";
        return String.valueOf(Character.toUpperCase(name.charAt(0)));
    }

    private static String truncate(String text, float maxWidth, gq.yozakura.engine.font.CFontRenderer font) {
        if (text == null) return "";
        if (font.getStringWidth(text) <= maxWidth) return text;
        String ellipsis = "…";
        float ellipsisW = font.getStringWidth(ellipsis);
        int lo = 0, hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (font.getStringWidth(text.substring(0, mid)) + ellipsisW <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return text.substring(0, lo) + ellipsis;
    }
}
