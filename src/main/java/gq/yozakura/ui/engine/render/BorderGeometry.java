package gq.yozakura.ui.engine.render;

import gq.yozakura.ui.engine.paint.RectBorderCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Border 几何分解：将 {@link RectBorderCommand} 分解为三角形列表，供 renderer 上传顶点缓冲。
 *
 * <p>分解策略：
 * <ul>
 *   <li>非圆角 (radius=0)：4 个边矩形 (top/right/bottom/left)，每个 2 三角形，共 8 个。
 *       边范围避开角：top/bottom 占全宽，left/right 占中间（不与 top/bottom 重叠）。</li>
 *   <li>圆角 (radius>0)：4 个边矩形（缩短以避开角）+ 4 个角的圆弧分段。
 *       每角分 N 段（N ∝ radius），每段为 1 quad (2 三角形)；
 *       内半径退化为 0 时段变为单三角形（fan 到角中心）。</li>
 * </ul>
 *
 * <p>三角形顺序：top(0,1), right(2,3), bottom(4,5), left(6,7)；
 * 圆角时追加 4 角段（TL, TR, BR, BL 顺时针）。
 *
 * <p>坐标为逻辑像素，与 RectBorderCommand 一致。所有顶点位于 border 盒 (x,y)-(x+w,y+h) 内。
 * 三角形顶点按相同绕序构造，保证非退化（面积非零）。
 *
 * <p>radius 会被钳制到 min(w,h)/2（CSS 行为）；内半径 = max(0, radius - maxBorder)。
 *
 * <p>本类不持有 GL 资源；纯几何计算，可在任意线程调用。
 */
public final class BorderGeometry {

    private BorderGeometry() {
    }

    /** 分解命令为三角形列表。 */
    public static TriangleList decompose(RectBorderCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("cmd must not be null");
        }
        float x = cmd.x();
        float y = cmd.y();
        float w = cmd.width();
        float h = cmd.height();
        float t = cmd.borderTop();
        float r = cmd.borderRight();
        float b = cmd.borderBottom();
        float l = cmd.borderLeft();
        float radius = cmd.radius();

        // 全 0 宽度 border → 无几何
        if (t <= 0f && r <= 0f && b <= 0f && l <= 0f) {
            return new TriangleList(Collections.<float[]>emptyList());
        }

        List<float[]> tris = new ArrayList<float[]>();

        if (radius <= 0f) {
            decomposeNonRounded(x, y, w, h, t, r, b, l, tris);
        } else {
            decomposeRounded(x, y, w, h, t, r, b, l, radius, tris);
        }

        return new TriangleList(tris);
    }

    /** 非圆角分解：4 边各 2 三角形。边的范围避开角（top/bottom 占全宽，left/right 占中间）。 */
    private static void decomposeNonRounded(float x, float y, float w, float h,
                                             float t, float r, float b, float l,
                                             List<float[]> out) {
        // Top: (x, y) - (x+w, y+t)
        addQuad(out, x, y, x + w, y + t);
        // Right: (x+w-r, y+t) - (x+w, y+h-b)
        addQuad(out, x + w - r, y + t, x + w, y + h - b);
        // Bottom: (x, y+h-b) - (x+w, y+h)
        addQuad(out, x, y + h - b, x + w, y + h);
        // Left: (x, y+t) - (x+l, y+h-b)
        addQuad(out, x, y + t, x + l, y + h - b);
    }

    /** 圆角分解：4 边（缩短以避开角）+ 4 角弧分段。 */
    private static void decomposeRounded(float x, float y, float w, float h,
                                          float t, float r, float b, float l,
                                          float radius, List<float[]> out) {
        // 钳制 radius 到 min(w,h)/2（CSS 行为）
        float maxRadius = Math.min(w, h) / 2f;
        float rad = Math.min(radius, maxRadius);
        if (rad <= 0f) {
            decomposeNonRounded(x, y, w, h, t, r, b, l, out);
            return;
        }

        // 边矩形（缩短以避开角）
        // Top: (x+rad, y) - (x+w-rad, y+t)
        if (w - 2f * rad > 0f) {
            addQuad(out, x + rad, y, x + w - rad, y + t);
        }
        // Right: (x+w-r, y+rad) - (x+w, y+h-rad)
        if (h - 2f * rad > 0f) {
            addQuad(out, x + w - r, y + rad, x + w, y + h - rad);
        }
        // Bottom: (x+rad, y+h-b) - (x+w-rad, y+h)
        if (w - 2f * rad > 0f) {
            addQuad(out, x + rad, y + h - b, x + w - rad, y + h);
        }
        // Left: (x, y+rad) - (x+l, y+h-rad)
        if (h - 2f * rad > 0f) {
            addQuad(out, x, y + rad, x + l, y + h - rad);
        }

        // 角弧分段：段数 ∝ radius，至少 1 段
        int segments = Math.max(1, (int) (rad / 2f));
        // 内半径 = max(0, radius - maxBorder)；同圆心
        float maxBorder = Math.max(Math.max(t, r), Math.max(b, l));
        float innerRad = Math.max(0f, rad - maxBorder);

        // 4 角顺时针：TL(π → 3π/2), TR(3π/2 → 2π), BR(0 → π/2), BL(π/2 → π)
        // 角中心 = (x+rad, y+rad), (x+w-rad, y+rad), (x+w-rad, y+h-rad), (x+rad, y+h-rad)
        addCornerArc(out, x + rad, y + rad, rad, innerRad,
                (float) Math.PI, 1.5f * (float) Math.PI, segments);
        addCornerArc(out, x + w - rad, y + rad, rad, innerRad,
                1.5f * (float) Math.PI, 2f * (float) Math.PI, segments);
        addCornerArc(out, x + w - rad, y + h - rad, rad, innerRad,
                0f, 0.5f * (float) Math.PI, segments);
        addCornerArc(out, x + rad, y + h - rad, rad, innerRad,
                0.5f * (float) Math.PI, (float) Math.PI, segments);
    }

    /**
     * 添加一个矩形 quad（2 三角形）。坐标 (x0,y0) 左上，(x1,y1) 右下。
     * 退化（零宽或零高）quad 跳过——边宽度为 0 时不应产生几何。
     */
    private static void addQuad(List<float[]> out, float x0, float y0, float x1, float y1) {
        if (x0 == x1 || y0 == y1) {
            return;
        }
        // 三角形 1: (x0,y0), (x1,y0), (x0,y1)
        out.add(new float[]{x0, y0, x1, y0, x0, y1});
        // 三角形 2: (x1,y0), (x1,y1), (x0,y1)
        out.add(new float[]{x1, y0, x1, y1, x0, y1});
    }

    /**
     * 添加一个角的弧分段。angle 从 startAngle 到 endAngle（弧度）。
     * innerR > 0 时每段为 ring quad（2 三角形）；innerR = 0 时退化为 fan（1 三角形到中心）。
     */
    private static void addCornerArc(List<float[]> out, float cx, float cy,
                                      float outerR, float innerR,
                                      float startAngle, float endAngle,
                                      int segments) {
        float angleStep = (endAngle - startAngle) / segments;
        for (int i = 0; i < segments; i++) {
            float a0 = startAngle + i * angleStep;
            float a1 = startAngle + (i + 1f) * angleStep;
            float ox0 = cx + outerR * (float) Math.cos(a0);
            float oy0 = cy + outerR * (float) Math.sin(a0);
            float ox1 = cx + outerR * (float) Math.cos(a1);
            float oy1 = cy + outerR * (float) Math.sin(a1);
            if (innerR <= 0f) {
                // 退化为 fan：单三角形到中心
                out.add(new float[]{ox0, oy0, ox1, oy1, cx, cy});
            } else {
                float ix0 = cx + innerR * (float) Math.cos(a0);
                float iy0 = cy + innerR * (float) Math.sin(a0);
                float ix1 = cx + innerR * (float) Math.cos(a1);
                float iy1 = cy + innerR * (float) Math.sin(a1);
                // Quad: (ox0,oy0), (ox1,oy1), (ix1,iy1), (ix0,iy0) → 2 三角形
                out.add(new float[]{ox0, oy0, ox1, oy1, ix1, iy1});
                out.add(new float[]{ox0, oy0, ix1, iy1, ix0, iy0});
            }
        }
    }

    /** 不可变三角形列表。每个三角形为 6 float（3 顶点 × 2 坐标：x0,y0,x1,y1,x2,y2）。 */
    public static final class TriangleList {
        private final List<float[]> triangles;

        TriangleList(List<float[]> triangles) {
            this.triangles = Collections.unmodifiableList(
                    new ArrayList<float[]>(triangles));
        }

        public int triangleCount() {
            return triangles.size();
        }

        /** 第 index 个三角形，返回 6 float 数组 [x0,y0,x1,y1,x2,y2]。 */
        public float[] triangle(int index) {
            return triangles.get(index);
        }
    }
}
