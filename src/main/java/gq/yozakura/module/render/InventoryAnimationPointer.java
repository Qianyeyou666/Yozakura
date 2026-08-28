package gq.yozakura.module.render;

final class InventoryAnimationPointer {
    private InventoryAnimationPointer() {
    }

    static float toLogicalCoordinate(int pointer, float center, float scale) {
        if (pointer < 0) {
            return pointer;
        }
        return center + (pointer - center) / Math.max(0.001F, scale);
    }
}
