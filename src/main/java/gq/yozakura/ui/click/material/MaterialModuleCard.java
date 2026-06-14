package gq.yozakura.ui.click.material;

import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.module.Module;
import gq.yozakura.util.animation.AnimationUtil;

/**
 * 单个模块卡片。
 *
 * <p>对应参考 HTML 的 module-card，内部包含模块标题、按键、MD3 开关和设置项。</p>
 */
final class MaterialModuleCard {
    private static final float COLLAPSED_H = 86.0f;
    private static final float INACTIVE_BORDER_W = 0.65f;
    private static final float ACTIVE_BORDER_W = 1.25f;
    private static final float VALUE_TOP = 80.0f;
    private static final float VALUE_CLIP_TOP = 72.0f;
    private static final float VALUE_BOTTOM_PADDING = 18.0f;

    private final MaterialClickGui gui;
    private final MaterialValueRenderer values;
    private final Module module;
    private float x;
    private float y;
    private float w;
    private float h;
    private float reveal;
    private float valueHeight;

    MaterialModuleCard(MaterialClickGui gui, MaterialValueRenderer values, Module module,
                       float x, float y, float w, float h) {
        this(gui, values, module, x, y, w, h, 1.0f, -1.0f);
    }

    MaterialModuleCard(MaterialClickGui gui, MaterialValueRenderer values, Module module,
                       float x, float y, float w, float h, float reveal) {
        this(gui, values, module, x, y, w, h, reveal, -1.0f);
    }

    MaterialModuleCard(MaterialClickGui gui, MaterialValueRenderer values, Module module,
                       float x, float y, float w, float h, float reveal, float valueHeight) {
        this.gui = gui;
        this.values = values;
        this.module = module;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.reveal = MaterialClickTheme.clamp(reveal, 0.0f, 1.0f);
        this.valueHeight = valueHeight;
    }

    void setLayout(float x, float y, float w, float h, float reveal, float valueHeight) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.reveal = MaterialClickTheme.clamp(reveal, 0.0f, 1.0f);
        this.valueHeight = valueHeight;
    }

    static float measure(MaterialClickGui gui, MaterialValueRenderer values, Module module, float w) {
        float s = gui.layout().scale;
        return measure(gui, module, w, values.measure(module, w - 40.0f * s));
    }

    static float measure(MaterialClickGui gui, Module module, float w, float valueH) {
        float s = gui.layout().scale;
        float collapsed = COLLAPSED_H * s;
        if (!gui.isModuleExpanded(module) || valueH <= 0.0f) {
            float expand = AnimationUtil.ease(gui.moduleExpandProgress(module), AnimationUtil.Ease.IN_OUT_CUBIC);
            if (valueH <= 0.0f || expand <= 0.01f) {
                return collapsed;
            }
        }
        float min = 120.0f * s;
        float expanded = Math.max(min, VALUE_TOP * s + valueH
                + (valueH > 0.0f ? VALUE_BOTTOM_PADDING * s : 20.0f * s));
        float expand = AnimationUtil.ease(gui.moduleExpandProgress(module), AnimationUtil.Ease.IN_OUT_CUBIC);
        return AnimationUtil.lerp(collapsed, expanded, expand);
    }

    void render(int mouseX, int mouseY) {
        if (reveal <= 0.01f) {
            return;
        }
        MaterialClickTheme theme = gui.theme();
        MaterialClickLayout layout = gui.layout();
        float s = layout.scale;
        boolean active = module.getState();
        boolean hovered = MaterialClickLayout.contains(x, y, x + w, y + h, mouseX, mouseY);
        String key = gui.animationKey(module);
        float hover = gui.easedAnimation("module.hover." + key, hovered ? 1.0f : 0.0f,
                0.26f, 0.0f, AnimationUtil.Ease.OUT_CUBIC);
        float activeProgress = gui.easedAnimation("module.active." + key, active ? 1.0f : 0.0f,
                0.24f, active ? 1.0f : 0.0f, AnimationUtil.Ease.IN_OUT_CUBIC);
        float expand = AnimationUtil.ease(gui.moduleExpandProgress(module), AnimationUtil.Ease.IN_OUT_CUBIC);

        float borderW = AnimationUtil.lerp(INACTIVE_BORDER_W, ACTIVE_BORDER_W, activeProgress) * s;
        RenderServices.shapes().roundedBorder(x, y, x + w, y + h, 20.0f * s, borderW,
                alpha(theme.cardFill(activeProgress, hover), reveal), alpha(theme.cardBorder(activeProgress, hover), reveal));

        drawHeader(mouseX, mouseY, activeProgress);
        if (hasSettings() && expand > 0.01f) {
            float valueY = y + VALUE_TOP * s;
            gui.beginScissor(x, y + VALUE_CLIP_TOP * s, w, Math.max(0.0f, h - VALUE_CLIP_TOP * s));
            try {
                values.render(module, x + 20.0f * s, valueY, w - 40.0f * s, mouseX, mouseY);
            } finally {
                gui.endScissor();
            }
        }
    }

    private void drawHeader(int mouseX, int mouseY, float activeProgress) {
        MaterialClickTheme theme = gui.theme();
        MaterialClickLayout layout = gui.layout();
        float s = layout.scale;
        FontLoaders.TB16.drawString(gui.displayName(module), x + 20.0f * s, y + 18.0f * s,
                alpha(theme.text(), reveal));
        drawKeyPill(mouseX, mouseY);
        drawSwitch(x + w - 64.0f * s, y + 22.0f * s, activeProgress);
    }

    private void drawKeyPill(int mouseX, int mouseY) {
        MaterialClickTheme theme = gui.theme();
        MaterialClickLayout layout = gui.layout();
        float s = layout.scale;
        String key = gui.isBinding(module) ? "..." : gui.keyName(module.getKey());
        float pillX = x + 20.0f * s;
        float pillY = y + 43.0f * s;
        float pillH = 18.0f * s;
        float pillW = Math.max(42.0f * s, FontLoaders.C14.getStringWidth(key) + 12.0f * s);
        boolean hovered = MaterialClickLayout.contains(pillX, pillY, pillX + pillW, pillY + pillH, mouseX, mouseY);
        float focus = gui.easedAnimation("module.key." + gui.animationKey(module),
                hovered || gui.isBinding(module) ? 1.0f : 0.0f, 0.22f, 0.0f, AnimationUtil.Ease.IN_OUT_CUBIC);
        int fill = theme.blend(theme.withAlpha(0xFFFFFFFF, 26.0f * theme.alpha()),
                theme.withAlpha(MaterialClickTheme.PRIMARY_CONTAINER, 168.0f * theme.alpha()), focus);
        RenderServices.shapes().rounded(pillX, pillY, pillX + pillW, pillY + pillH, 6.0f * s,
                alpha(fill, reveal));
        float textY = pillY + Math.max(0.0f, pillH - FontLoaders.C14.getStringHeight(key)) / 2.0f + 0.5f * s;
        FontLoaders.C14.drawCenteredString(key, pillX + pillW / 2.0f, textY,
                alpha(theme.withAlpha(theme.blend(MaterialClickTheme.MUTED, MaterialClickTheme.ON_PRIMARY_CONTAINER, focus),
                        255.0f * theme.alpha()), reveal));
    }

    private void drawSwitch(float x, float y, float activeProgress) {
        MaterialClickTheme theme = gui.theme();
        MaterialClickLayout layout = gui.layout();
        float s = layout.scale;
        float w = 44.0f * s;
        float h = 24.0f * s;
        int track = theme.blend(theme.withAlpha(0xFFFFFFFF, 26.0f * theme.alpha() * reveal),
                theme.withAlpha(MaterialClickTheme.PRIMARY, 255.0f * theme.alpha() * reveal), activeProgress);
        int thumb = theme.blend(theme.withAlpha(MaterialClickTheme.MUTED, 230.0f * theme.alpha() * reveal),
                theme.withAlpha(MaterialClickTheme.ON_PRIMARY, 255.0f * theme.alpha() * reveal), activeProgress);
        int border = theme.withAlpha(0xFFFFFFFF, 13.0f * theme.alpha() * reveal * (1.0f - activeProgress));
        RenderServices.shapes().roundedBorder(x, y, x + w, y + h, h / 2.0f, 1.0f, track, border);
        float knob = 16.0f * s;
        float knobX = x + AnimationUtil.lerp(4.0f, 22.0f, activeProgress) * s;
        RenderServices.shapes().rounded(knobX, y + 4.0f * s, knobX + knob, y + 4.0f * s + knob, knob / 2.0f, thumb);
    }

    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!MaterialClickLayout.contains(x, y, x + w, y + h, mouseX, mouseY)) {
            return false;
        }
        if (button == 1) {
            values.closeDropdown();
            if (hasSettings()) {
                gui.toggleModuleExpanded(module);
            }
            return true;
        }
        float s = gui.layout().scale;
        float keyX = x + 20.0f * s;
        float keyY = y + 43.0f * s;
        float keyW = Math.max(42.0f * s, FontLoaders.C14.getStringWidth(gui.keyName(module.getKey())) + 12.0f * s);
        if ((button == 0 || button == 2) && MaterialClickLayout.contains(keyX, keyY, keyX + keyW, keyY + 18.0f * s, mouseX, mouseY)) {
            gui.startBinding(module);
            return true;
        }
        if (gui.isModuleExpanded(module) && hasSettings()
                && values.mouseClicked(module, x + 20.0f * s, y + VALUE_TOP * s,
                w - 40.0f * s, mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && mouseY <= y + 68.0f * s) {
            module.setState(!module.getState());
            return true;
        }
        if (button == 2) {
            gui.startBinding(module);
            return true;
        }
        return true;
    }

    private boolean hasSettings() {
        return measuredValueHeight() > 0.0f;
    }

    private float measuredValueHeight() {
        if (valueHeight >= 0.0f) {
            return valueHeight;
        }
        float s = gui.layout().scale;
        return values.measure(module, w - 40.0f * s);
    }

    private int alpha(int color, float alpha) {
        return gui.theme().withAlpha(color, ((color >>> 24) & 255) * MaterialClickTheme.clamp(alpha, 0.0f, 1.0f));
    }
}
