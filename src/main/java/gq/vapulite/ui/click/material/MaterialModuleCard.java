package gq.vapulite.ui.click.material;

import gq.vapulite.engine.font.FontLoaders;
import gq.vapulite.engine.render.ui.RenderServices;
import gq.vapulite.module.Module;

/**
 * 单个模块卡片。
 *
 * <p>对应参考 HTML 的 module-card，内部包含模块标题、按键、MD3 开关和设置项。</p>
 */
final class MaterialModuleCard {
    private static final float COLLAPSED_H = 86.0f;
    private static final float INACTIVE_BORDER_W = 0.65f;
    private static final float ACTIVE_BORDER_W = 1.25f;

    private final MaterialClickGui gui;
    private final MaterialValueRenderer values;
    private final Module module;
    private final float x;
    private final float y;
    private final float w;
    private final float h;

    MaterialModuleCard(MaterialClickGui gui, MaterialValueRenderer values, Module module,
                       float x, float y, float w, float h) {
        this.gui = gui;
        this.values = values;
        this.module = module;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    static float measure(MaterialClickGui gui, MaterialValueRenderer values, Module module, float w) {
        float s = gui.layout().scale;
        float valueH = values.measure(module, w - 40.0f * s);
        if (!gui.isModuleExpanded(module) || valueH <= 0.0f) {
            return COLLAPSED_H * s;
        }
        float min = 120.0f * s;
        return Math.max(min, 70.0f * s + valueH + (valueH > 0.0f ? 18.0f * s : 20.0f * s));
    }

    void render(int mouseX, int mouseY) {
        MaterialClickTheme theme = gui.theme();
        MaterialClickLayout layout = gui.layout();
        float s = layout.scale;
        boolean active = module.getState();
        boolean hovered = MaterialClickLayout.contains(x, y, x + w, y + h, mouseX, mouseY);
        float hover = hovered ? 1.0f : 0.0f;

        float borderW = (active ? ACTIVE_BORDER_W : INACTIVE_BORDER_W) * s;
        RenderServices.shapes().roundedBorder(x, y, x + w, y + h, 20.0f * s, borderW,
                theme.cardFill(active, hover), theme.cardBorder(active, hover));

        drawHeader(mouseX, mouseY);
        if (gui.isModuleExpanded(module) && hasSettings()) {
            float valueY = y + 70.0f * s;
            values.render(module, x + 20.0f * s, valueY, w - 40.0f * s, mouseX, mouseY);
        }
    }

    private void drawHeader(int mouseX, int mouseY) {
        MaterialClickTheme theme = gui.theme();
        MaterialClickLayout layout = gui.layout();
        float s = layout.scale;
        FontLoaders.TB16.drawString(gui.displayName(module), x + 20.0f * s, y + 18.0f * s, theme.text());
        drawKeyPill(mouseX, mouseY);
        drawSwitch(x + w - 64.0f * s, y + 22.0f * s, module.getState());
    }

    private void drawKeyPill(int mouseX, int mouseY) {
        MaterialClickTheme theme = gui.theme();
        MaterialClickLayout layout = gui.layout();
        float s = layout.scale;
        String key = gui.isBinding(module) ? "..." : gui.keyName(module.getKey());
        float pillX = x + 20.0f * s;
        float pillY = y + 43.0f * s;
        float pillW = Math.max(42.0f * s, FontLoaders.C14.getStringWidth(key) + 12.0f * s);
        RenderServices.shapes().rounded(pillX, pillY, pillX + pillW, pillY + 18.0f * s, 6.0f * s,
                theme.keybindFill());
        FontLoaders.C14.drawCenteredString(key, pillX + pillW / 2.0f, pillY + 4.0f * s, theme.muted());
    }

    private void drawSwitch(float x, float y, boolean active) {
        MaterialClickTheme theme = gui.theme();
        MaterialClickLayout layout = gui.layout();
        float s = layout.scale;
        float w = 44.0f * s;
        float h = 24.0f * s;
        int track = active ? theme.withAlpha(MaterialClickTheme.PRIMARY, 255.0f * theme.alpha())
                : theme.withAlpha(0xFFFFFFFF, 26.0f * theme.alpha());
        int thumb = active ? theme.withAlpha(MaterialClickTheme.ON_PRIMARY, 255.0f * theme.alpha())
                : theme.withAlpha(MaterialClickTheme.MUTED, 230.0f * theme.alpha());
        int border = active ? 0 : theme.withAlpha(0xFFFFFFFF, 13.0f * theme.alpha());
        RenderServices.shapes().roundedBorder(x, y, x + w, y + h, h / 2.0f, 1.0f, track, border);
        float knob = 16.0f * s;
        float knobX = x + (active ? 22.0f : 4.0f) * s;
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
                && values.mouseClicked(module, x + 20.0f * s, y + 70.0f * s,
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
        float s = gui.layout().scale;
        return values.measure(module, w - 40.0f * s) > 0.0f;
    }

}
