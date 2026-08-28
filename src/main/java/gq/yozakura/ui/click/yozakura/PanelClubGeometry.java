package gq.yozakura.ui.click.yozakura;

public final class PanelClubGeometry {
    private static final float INSET = 14.0f;
    private static final float GAP = 6.0f;
    private static final float TAB_HEIGHT = 22.0f;
    private static final float CONTROL_HEIGHT = 22.0f;

    private PanelClubGeometry() {
    }

    public static PanelClickGuiLayout.Rect localTab(PanelClickGuiLayout.Rect bounds) {
        float width = (bounds.width() - INSET * 2.0f - GAP) * 0.5f;
        return new PanelClickGuiLayout.Rect(bounds.x() + INSET, bounds.y() + 43.0f,
                width, TAB_HEIGHT);
    }

    public static PanelClickGuiLayout.Rect cloudTab(PanelClickGuiLayout.Rect bounds) {
        PanelClickGuiLayout.Rect local = localTab(bounds);
        return new PanelClickGuiLayout.Rect(local.right() + GAP, local.y(), local.width(), local.height());
    }

    public static PanelClickGuiLayout.Rect identity(PanelClickGuiLayout.Rect bounds) {
        return new PanelClickGuiLayout.Rect(bounds.x() + INSET, bounds.y() + 69.0f,
                bounds.width() - INSET * 2.0f, 14.0f);
    }

    public static PanelClickGuiLayout.Rect searchField(PanelClickGuiLayout.Rect bounds) {
        return new PanelClickGuiLayout.Rect(bounds.x() + INSET, bounds.y() + 87.0f,
                bounds.width() - INSET * 2.0f, CONTROL_HEIGHT);
    }

    public static PanelClickGuiLayout.Rect cloudList(PanelClickGuiLayout.Rect bounds) {
        float top = bounds.y() + 115.0f;
        return new PanelClickGuiLayout.Rect(bounds.x() + INSET, top,
                bounds.width() - INSET * 2.0f,
                Math.max(72.0f, bounds.bottom() - 82.0f - top));
    }

    public static PanelClickGuiLayout.Rect uploadButton(PanelClickGuiLayout.Rect bounds) {
        return action(bounds, 0, 5);
    }

    public static PanelClickGuiLayout.Rect downloadButton(PanelClickGuiLayout.Rect bounds) {
        return action(bounds, 1, 5);
    }

    public static PanelClickGuiLayout.Rect useButton(PanelClickGuiLayout.Rect bounds) {
        return action(bounds, 2, 5);
    }

    public static PanelClickGuiLayout.Rect deleteButton(PanelClickGuiLayout.Rect bounds) {
        return action(bounds, 3, 5);
    }

    public static PanelClickGuiLayout.Rect refreshButton(PanelClickGuiLayout.Rect bounds) {
        return action(bounds, 4, 5);
    }

    public static PanelClickGuiLayout.Rect status(PanelClickGuiLayout.Rect bounds) {
        return new PanelClickGuiLayout.Rect(bounds.x() + INSET, bounds.bottom() - 20.0f,
                bounds.width() - INSET * 2.0f, 12.0f);
    }

    public static float statusY(PanelClickGuiLayout.Rect bounds) {
        return status(bounds).y();
    }

    private static PanelClickGuiLayout.Rect action(PanelClickGuiLayout.Rect bounds, int index, int count) {
        float available = bounds.width() - INSET * 2.0f - GAP * (count - 1);
        float width = available / count;
        return new PanelClickGuiLayout.Rect(bounds.x() + INSET + index * (width + GAP),
                bounds.bottom() - 50.0f, width, CONTROL_HEIGHT);
    }
}
