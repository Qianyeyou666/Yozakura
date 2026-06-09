package gq.vapulite.ui;

public final class UiBounds {
    public float x;
    public float y;
    public float width;
    public float height;

    public UiBounds() {
    }

    public UiBounds(float x, float y, float width, float height) {
        set(x, y, width, height);
    }

    public UiBounds set(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0.0f, width);
        this.height = Math.max(0.0f, height);
        return this;
    }

    public float right() {
        return x + width;
    }

    public float bottom() {
        return y + height;
    }

    public boolean contains(float mouseX, float mouseY) {
        return mouseX >= x && mouseX <= right() && mouseY >= y && mouseY <= bottom();
    }
}
