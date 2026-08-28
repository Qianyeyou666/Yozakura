package gq.yozakura.ui.click.timewarp;

/** Geometry for the independent Timewarp window, including drag and resize handles. */
public final class TimewarpClickGuiGeometry {
    public static final float WINDOW_WIDTH = 500.0f;
    public static final float WINDOW_HEIGHT = 382.0f;
    public static final float MIN_WINDOW_WIDTH = 460.0f;
    public static final float MIN_WINDOW_HEIGHT = 330.0f;
    public static final float VIEWPORT_MARGIN = 8.0f;
    public static final float SIDEBAR_WIDTH = 122.0f;
    public static final float MIN_SIDEBAR_WIDTH = 104.0f;
    public static final float NAV_TOP = 48.0f;
    public static final float NAV_HEIGHT = 29.0f;
    public static final float NAV_GAP = 1.0f;
    public static final float CONTENT_PADDING = 14.0f;
    public static final float CONTENT_HEADER_HEIGHT = 48.0f;
    public static final float MODULE_ROW_HEIGHT = 42.0f;
    public static final float MODULE_ROW_GAP = 5.0f;
    public static final float RESIZE_HANDLE_SIZE = 14.0f;

    private TimewarpClickGuiGeometry() {
    }

    public static Layout compute(float screenWidth, float screenHeight) {
        float width = Math.min(WINDOW_WIDTH, Math.max(1.0f, screenWidth - VIEWPORT_MARGIN * 2.0f));
        float height = Math.min(WINDOW_HEIGHT, Math.max(1.0f, screenHeight - VIEWPORT_MARGIN * 2.0f));
        return compute(screenWidth, screenHeight, width, height,
                (screenWidth - width) * 0.5f, (screenHeight - height) * 0.5f);
    }

    public static Layout compute(float screenWidth, float screenHeight,
                                 float requestedWidth, float requestedHeight,
                                 float requestedX, float requestedY) {
        float maxWidth = Math.max(1.0f, screenWidth - VIEWPORT_MARGIN * 2.0f);
        float maxHeight = Math.max(1.0f, screenHeight - VIEWPORT_MARGIN * 2.0f);
        float width = clamp(requestedWidth, Math.min(MIN_WINDOW_WIDTH, maxWidth), maxWidth);
        float height = clamp(requestedHeight, Math.min(MIN_WINDOW_HEIGHT, maxHeight), maxHeight);
        float x = clamp(requestedX, VIEWPORT_MARGIN,
                Math.max(VIEWPORT_MARGIN, screenWidth - VIEWPORT_MARGIN - width));
        float y = clamp(requestedY, VIEWPORT_MARGIN,
                Math.max(VIEWPORT_MARGIN, screenHeight - VIEWPORT_MARGIN - height));
        float sidebarWidth = Math.min(SIDEBAR_WIDTH,
                Math.max(MIN_SIDEBAR_WIDTH, width * 0.245f));
        Rect window = new Rect(x, y, width, height);
        Rect sidebar = new Rect(x, y, sidebarWidth, height);
        Rect content = new Rect(sidebar.right(), y, width - sidebarWidth, height);
        return new Layout(window, sidebar, content);
    }

    public static Rect dragHandle(Layout layout) {
        return new Rect(layout.window().x(), layout.window().y(), layout.window().width(), 28.0f);
    }

    public static Rect resizeHandle(Layout layout) {
        Rect window = layout.window();
        return new Rect(window.right() - RESIZE_HANDLE_SIZE,
                window.bottom() - RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
    }

    public static Rect navigationItem(Layout layout, int index) {
        Rect sidebar = layout.sidebar();
        return new Rect(sidebar.x() + 12.0f,
                sidebar.y() + NAV_TOP + index * (NAV_HEIGHT + NAV_GAP),
                sidebar.width() - 24.0f, NAV_HEIGHT);
    }

    public static Rect moduleViewport(Layout layout) {
        Rect content = layout.content();
        float top = content.y() + CONTENT_HEADER_HEIGHT;
        return new Rect(content.x() + CONTENT_PADDING, top,
                content.width() - CONTENT_PADDING * 2.0f,
                Math.max(1.0f, content.bottom() - CONTENT_PADDING - top));
    }

    public static Rect moduleRow(Layout layout, int index, float scroll) {
        Rect viewport = moduleViewport(layout);
        return new Rect(viewport.x(),
                viewport.y() + index * (MODULE_ROW_HEIGHT + MODULE_ROW_GAP) - scroll,
                viewport.width(), MODULE_ROW_HEIGHT);
    }

    public static Rect detailViewport(Layout layout) {
        Rect content = layout.content();
        float top = content.y() + CONTENT_HEADER_HEIGHT;
        return new Rect(content.x() + CONTENT_PADDING, top,
                content.width() - CONTENT_PADDING * 2.0f,
                Math.max(1.0f, content.bottom() - CONTENT_PADDING - top));
    }

    public static Rect detailKeybindButton(Layout layout) {
        Rect content = layout.content();
        return new Rect(content.right() - 126.0f, content.y() + 14.0f, 72.0f, 24.0f);
    }

    public static Rect closeButton(Layout layout) {
        Rect content = layout.content();
        return new Rect(content.right() - 42.0f, content.y() + 13.0f, 26.0f, 26.0f);
    }

    public static Rect configTab(Layout layout, boolean cloud) {
        Rect content = layout.content();
        return new Rect(content.x() + 16.0f + (cloud ? 94.0f : 0.0f),
                content.y() + 48.0f, 86.0f, 24.0f);
    }

    public static final class Layout {
        private final Rect window;
        private final Rect sidebar;
        private final Rect content;

        private Layout(Rect window, Rect sidebar, Rect content) {
            this.window = window;
            this.sidebar = sidebar;
            this.content = content;
        }

        public Rect window() { return window; }
        public Rect sidebar() { return sidebar; }
        public Rect content() { return content; }
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

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
