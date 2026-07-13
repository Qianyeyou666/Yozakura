package gq.yozakura.ui.click.sakura;

final class SakuraWindowGeometry {
    private SakuraWindowGeometry() {
    }

    static boolean containsScreen(float x1, float y1, float x2, float y2, float mouseX, float mouseY) {
        return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
    }

    static float resizeScale(float startScale, float startMouseX, float startMouseY,
                             float mouseX, float mouseY, float responsiveScale,
                             float baseWidth, float baseHeight, float minimum, float maximum) {
        float width = Math.max(1.0f, baseWidth * responsiveScale);
        float height = Math.max(1.0f, baseHeight * responsiveScale);
        float deltaX = mouseX - startMouseX;
        float deltaY = mouseY - startMouseY;
        float scaleDelta = (deltaX * width + deltaY * height) / (width * width + height * height);
        return clamp(startScale + scaleDelta, minimum, maximum);
    }

    static float windowYFromHeader(float mouseY, float dragOffsetY, float introOffset) {
        return mouseY - dragOffsetY - introOffset;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
