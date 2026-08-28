package gq.yozakura.ui.click.yozakura;

/**
 * Direct-OpenGL Panel layout matching Epsilon's centered panel geometry.
 */
public final class PanelClickGuiLayout {
    public static final float PANEL_MAX_WIDTH = 584.0f;
    public static final float PANEL_MAX_HEIGHT = 420.0f;
    public static final float PANEL_MIN_WIDTH = 528.0f;
    public static final float PANEL_MIN_HEIGHT = 360.0f;
    public static final float OUTER_PADDING = 5.0f;
    public static final float SECTION_GAP = 3.0f;
    public static final float NAV_MIN_WIDTH = 120.0f;
    public static final float NAV_MAX_WIDTH = 132.0f;
    public static final float VIEWPORT_MARGIN = 5.0f;
    public static final float RESIZE_HANDLE_SIZE = 16.0f;

    private PanelClickGuiLayout() {
    }

    public static Layout compute(float screenWidth, float screenHeight, float railWidth) {
        float desiredWidth = Math.min(screenWidth * 0.56f, PANEL_MAX_WIDTH);
        float desiredHeight = Math.min(screenHeight * 0.62f, PANEL_MAX_HEIGHT);
        return compute(screenWidth, screenHeight, railWidth,
                Math.max(PANEL_MIN_WIDTH, desiredWidth),
                Math.max(PANEL_MIN_HEIGHT, desiredHeight));
    }

    public static Layout compute(float screenWidth, float screenHeight, float railWidth,
                                 float requestedWidth, float requestedHeight) {
        float maxWidth = Math.max(1.0f, screenWidth - VIEWPORT_MARGIN * 2.0f);
        float maxHeight = Math.max(1.0f, screenHeight - VIEWPORT_MARGIN * 2.0f);
        float minWidth = Math.min(PANEL_MIN_WIDTH, maxWidth);
        float minHeight = Math.min(PANEL_MIN_HEIGHT, maxHeight);
        float panelWidth = Math.max(minWidth, Math.min(maxWidth, requestedWidth));
        float panelHeight = Math.max(minHeight, Math.min(maxHeight, requestedHeight));
        return computeResolved(screenWidth, screenHeight, railWidth, panelWidth, panelHeight,
                (screenWidth - panelWidth) * 0.5f,
                (screenHeight - panelHeight) * 0.5f);
    }

    public static Layout compute(float screenWidth, float screenHeight, float railWidth,
                                 float requestedWidth, float requestedHeight,
                                 float requestedX, float requestedY) {
        float maxWidth = Math.max(1.0f, screenWidth - VIEWPORT_MARGIN * 2.0f);
        float maxHeight = Math.max(1.0f, screenHeight - VIEWPORT_MARGIN * 2.0f);
        float minWidth = Math.min(PANEL_MIN_WIDTH, maxWidth);
        float minHeight = Math.min(PANEL_MIN_HEIGHT, maxHeight);
        float panelWidth = Math.max(minWidth, Math.min(maxWidth, requestedWidth));
        float panelHeight = Math.max(minHeight, Math.min(maxHeight, requestedHeight));
        float maxX = Math.max(VIEWPORT_MARGIN, screenWidth - VIEWPORT_MARGIN - panelWidth);
        float maxY = Math.max(VIEWPORT_MARGIN, screenHeight - VIEWPORT_MARGIN - panelHeight);
        float x = Math.max(VIEWPORT_MARGIN, Math.min(maxX, requestedX));
        float y = Math.max(VIEWPORT_MARGIN, Math.min(maxY, requestedY));
        return computeResolved(screenWidth, screenHeight, railWidth, panelWidth, panelHeight, x, y);
    }

    private static Layout computeResolved(float screenWidth, float screenHeight, float railWidth,
                                          float panelWidth, float panelHeight, float x, float y) {
        float safeRailWidth = NAV_MAX_WIDTH;
        float columnHeight = panelHeight - OUTER_PADDING * 2.0f;
        Rect panel = new Rect(x, y, panelWidth, panelHeight);
        Rect rail = new Rect(x + OUTER_PADDING, y + OUTER_PADDING,
                safeRailWidth, columnHeight);
        float contentX = rail.right() + SECTION_GAP;
        float contentWidth = Math.max(1.0f,
                panel.right() - OUTER_PADDING - contentX);
        Rect modules = new Rect(contentX, rail.y(), contentWidth, columnHeight);
        Rect detail = new Rect(contentX, rail.y(), contentWidth, columnHeight);
        return new Layout(panel, rail, modules, detail);
    }

    public static Rect dragHandle(Layout layout) {
        return new Rect(layout.panel().x(), layout.panel().y(),
                layout.panel().width(), 34.0f);
    }

    public static Rect resizeHandle(Rect panel) {
        return new Rect(panel.right() - RESIZE_HANDLE_SIZE,
                panel.bottom() - RESIZE_HANDLE_SIZE,
                RESIZE_HANDLE_SIZE,
                RESIZE_HANDLE_SIZE);
    }

    public static Layout translated(Layout source, float offsetX, float offsetY) {
        return new Layout(
                translate(source.panel(), offsetX, offsetY),
                translate(source.rail(), offsetX, offsetY),
                translate(source.modules(), offsetX, offsetY),
                translate(source.detail(), offsetX, offsetY));
    }

    public static Layout resized(Layout source, float width, float height) {
        float safeWidth = Math.max(1.0f, width);
        float safeHeight = Math.max(1.0f, height);
        float x = source.panel().x();
        float y = source.panel().y();
        float columnHeight = Math.max(1.0f, safeHeight - OUTER_PADDING * 2.0f);
        Rect panel = new Rect(x, y, safeWidth, safeHeight);
        Rect rail = new Rect(x + OUTER_PADDING, y + OUTER_PADDING,
                NAV_MAX_WIDTH, columnHeight);
        float contentX = rail.right() + SECTION_GAP;
        float contentWidth = Math.max(1.0f,
                panel.right() - OUTER_PADDING - contentX);
        Rect content = new Rect(contentX, rail.y(), contentWidth, columnHeight);
        return new Layout(panel, rail, content, content);
    }

    private static Rect translate(Rect rect, float offsetX, float offsetY) {
        return new Rect(rect.x() + offsetX, rect.y() + offsetY, rect.width(), rect.height());
    }

    public static final class Layout {
        private final Rect panel;
        private final Rect rail;
        private final Rect modules;
        private final Rect detail;

        private Layout(Rect panel, Rect rail, Rect modules, Rect detail) {
            this.panel = panel;
            this.rail = rail;
            this.modules = modules;
            this.detail = detail;
        }

        public Rect panel() { return panel; }
        public Rect rail() { return rail; }
        public Rect modules() { return modules; }
        public Rect detail() { return detail; }
    }

    public static final class Rect {
        private final float x;
        private final float y;
        private final float width;
        private final float height;

        public Rect(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = Math.max(0.0f, width);
            this.height = Math.max(0.0f, height);
        }

        public float x() { return x; }
        public float y() { return y; }
        public float width() { return width; }
        public float height() { return height; }
        public float right() { return x + width; }
        public float bottom() { return y + height; }

        public boolean contains(float px, float py) {
            return px >= x && px <= right() && py >= y && py <= bottom();
        }
    }
}
