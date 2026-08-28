package gq.yozakura.ui.click.yozakura;

/** Scrollbar geometry shared by painting and pointer hit-testing. */
public final class PanelClickGuiScroll {
    public static final float WIDTH = 2.0f;
    public static final float RIGHT_INSET = 2.5f;
    public static final float MIN_THUMB_HEIGHT = 10.0f;
    public static final float HIT_WIDTH = 10.0f;
    public static final float HOVER_WIDTH = 2.5f;
    public static final float TOTAL_WIDTH = HIT_WIDTH;

    private PanelClickGuiScroll() {
    }

    public static Geometry geometry(PanelClickGuiLayout.Rect viewport, float scroll,
                                    float maxScroll, float contentHeight) {
        if (viewport == null || maxScroll <= 0.0f || contentHeight <= viewport.height()) {
            return null;
        }
        float thumbHeight = Math.max(MIN_THUMB_HEIGHT,
                viewport.height() * viewport.height() / Math.max(viewport.height(), contentHeight));
        float travel = Math.max(0.0f, viewport.height() - thumbHeight);
        float progress = clamp(scroll / maxScroll, 0.0f, 1.0f);
        float thumbY = viewport.y() + travel * progress;
        float thumbX = viewport.right() - RIGHT_INSET;
        float trackX = viewport.right() - HIT_WIDTH;
        return new Geometry(thumbX, thumbY, WIDTH, thumbHeight,
                trackX, viewport.y(), HIT_WIDTH, viewport.height());
    }

    public static float scrollFromThumbTop(float thumbTop, PanelClickGuiLayout.Rect viewport,
                                           float maxScroll, float contentHeight) {
        Geometry geometry = geometry(viewport, 0.0f, maxScroll, contentHeight);
        if (geometry == null) {
            return 0.0f;
        }
        float travel = Math.max(1.0f, viewport.height() - geometry.height());
        float progress = clamp((thumbTop - viewport.y()) / travel, 0.0f, 1.0f);
        return progress * Math.max(0.0f, maxScroll);
    }

    public static float visualWidth(Geometry geometry, float hoverProgress) {
        if (geometry == null) {
            return 0.0f;
        }
        float progress = clamp(hoverProgress, 0.0f, 1.0f);
        return geometry.width() + (HOVER_WIDTH - geometry.width()) * progress;
    }

    public static float visualX(Geometry geometry, float hoverProgress) {
        if (geometry == null) {
            return 0.0f;
        }
        float width = visualWidth(geometry, hoverProgress);
        return geometry.x() - (width - geometry.width()) * 0.5f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Geometry {
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final float trackX;
        private final float trackY;
        private final float trackWidth;
        private final float trackHeight;

        private Geometry(float x, float y, float width, float height,
                         float trackX, float trackY, float trackWidth, float trackHeight) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.trackX = trackX;
            this.trackY = trackY;
            this.trackWidth = trackWidth;
            this.trackHeight = trackHeight;
        }

        public float x() { return x; }
        public float y() { return y; }
        public float width() { return width; }
        public float height() { return height; }
        public float trackX() { return trackX; }
        public float trackY() { return trackY; }
        public float trackWidth() { return trackWidth; }
        public float trackHeight() { return trackHeight; }

        public boolean thumbContains(float px, float py) {
            return px >= trackX && px <= trackX + trackWidth && py >= y && py <= y + height;
        }

        public boolean trackContains(float px, float py) {
            return px >= trackX && px <= trackX + trackWidth
                    && py >= trackY && py <= trackY + trackHeight;
        }
    }
}
