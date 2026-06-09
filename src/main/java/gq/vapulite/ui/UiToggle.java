package gq.vapulite.ui;

import gq.vapulite.Vapu.utils.RenderUtil;

import java.awt.Color;

public class UiToggle extends UiComponent {
    private boolean enabled;
    private float progress;
    private boolean explicitProgress;

    public UiToggle enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public UiToggle progress(float progress) {
        this.progress = Math.max(0.0f, Math.min(1.0f, progress));
        this.explicitProgress = true;
        return this;
    }

    @Override
    public UiToggle setBounds(float x, float y, float width, float height) {
        super.setBounds(x, y, width, height);
        return this;
    }

    @Override
    public UiToggle setAlpha(float alpha) {
        super.setAlpha(alpha);
        return this;
    }

    @Override
    public UiToggle setTheme(UiTheme theme) {
        super.setTheme(theme);
        return this;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible || alpha <= 0.0f) {
            return;
        }
        if (!explicitProgress) {
            progress += ((enabled ? 1.0f : 0.0f) - progress) * 0.25f;
        }
        explicitProgress = false;
        int offTrack = new Color(37, 39, 42, 235).getRGB();
        int onTrack = new Color(82, 79, 190, 225).getRGB();
        int track = blend(offTrack, onTrack, progress);
        if (progress > 0.04f) {
            RenderUtil.drawSoftShadow(bounds.x, bounds.y, bounds.right(), bounds.bottom(), bounds.height / 2.0f,
                    theme.withAlpha(theme.accent, 45.0f * progress * alpha), 4, 2.2f);
        }
        RenderUtil.drawRoundedRect(bounds.x, bounds.y, bounds.right(), bounds.bottom(), bounds.height / 2.0f,
                theme.withAlpha(track, ((track >>> 24) & 255) * alpha));
        float knobSize = Math.max(4.0f, bounds.height - 4.0f);
        float knobX = bounds.x + 2.0f + (bounds.width - knobSize - 4.0f) * progress;
        int knob = blend(new Color(112, 118, 123).getRGB(), new Color(226, 241, 246).getRGB(), progress);
        RenderUtil.drawRoundedRect(knobX, bounds.y + 2.0f, knobX + knobSize, bounds.y + bounds.height - 2.0f,
                knobSize / 2.0f, theme.withAlpha(knob, 245.0f * alpha));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            enabled = !enabled;
            return true;
        }
        return false;
    }

    private static int blend(int from, int to, float progress) {
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        int a = (int) (((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * progress);
        int r = (int) (((from >>> 16) & 255) + (((to >>> 16) & 255) - ((from >>> 16) & 255)) * progress);
        int g = (int) (((from >>> 8) & 255) + (((to >>> 8) & 255) - ((from >>> 8) & 255)) * progress);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * progress);
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }
}
