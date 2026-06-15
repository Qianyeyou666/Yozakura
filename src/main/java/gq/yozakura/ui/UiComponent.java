package gq.yozakura.ui;

public abstract class UiComponent {
    protected final UiBounds bounds = new UiBounds();
    protected UiTheme theme = UiTheme.yozakura();
    protected float alpha = 1.0f;
    protected boolean visible = true;

    public UiBounds bounds() {
        return bounds;
    }

    public UiComponent setBounds(float x, float y, float width, float height) {
        bounds.set(x, y, width, height);
        return this;
    }

    public UiComponent setTheme(UiTheme theme) {
        if (theme != null) {
            this.theme = theme;
        }
        return this;
    }

    public UiComponent setAlpha(float alpha) {
        this.alpha = Math.max(0.0f, Math.min(1.0f, alpha));
        return this;
    }

    public UiComponent setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public boolean isHovered(float mouseX, float mouseY) {
        return visible && bounds.contains(mouseX, mouseY);
    }

    public void render(int mouseX, int mouseY, float partialTicks) {
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }
}
