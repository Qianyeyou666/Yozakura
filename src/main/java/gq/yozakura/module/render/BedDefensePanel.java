package gq.yozakura.module.render;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class BedDefensePanel {
    static final int MAX_COLUMNS = 6;
    private static final float MIN_SCALE = 0.60F;
    private static final float MIN_SCALE_DISTANCE = 64.0F;

    private BedDefensePanel() {
    }

    static <T> List<T> uniqueMaterials(Iterable<T> materials) {
        if (materials == null) {
            throw new IllegalArgumentException("materials must not be null");
        }
        Set<T> unique = new LinkedHashSet<T>();
        for (T material : materials) {
            if (material != null) {
                unique.add(material);
            }
        }
        return new ArrayList<T>(unique);
    }

    static int columns(int iconCount) {
        if (iconCount < 0) {
            throw new IllegalArgumentException("iconCount must not be negative");
        }
        return Math.min(MAX_COLUMNS, iconCount);
    }

    static int rows(int iconCount) {
        int columns = columns(iconCount);
        return columns == 0 ? 0 : (iconCount + columns - 1) / columns;
    }

    static float scaleForDistance(float distance) {
        if (distance < 0.0F) {
            throw new IllegalArgumentException("distance must not be negative");
        }
        float range = 1.0F - MIN_SCALE;
        return Math.max(MIN_SCALE, 1.0F - distance * range / MIN_SCALE_DISTANCE);
    }
}
