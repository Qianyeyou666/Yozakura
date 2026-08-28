package gq.yozakura.ui.engine.api;

import gq.yozakura.ui.engine.animation.AnimationClock;
import gq.yozakura.ui.engine.animation.TransitionListener;
import gq.yozakura.ui.engine.animation.TransitionRunner;
import gq.yozakura.ui.engine.animation.TransitionStore;
import gq.yozakura.ui.engine.css.ComputedStyle;
import gq.yozakura.ui.engine.css.StyleResolver;
import gq.yozakura.ui.engine.css.Stylesheet;
import gq.yozakura.ui.engine.dom.ElementNode;
import gq.yozakura.ui.engine.input.HitTester;
import gq.yozakura.ui.engine.input.InputDispatcher;
import gq.yozakura.ui.engine.input.InteractionState;
import gq.yozakura.ui.engine.input.PointerEvent;
import gq.yozakura.ui.engine.input.PointerEventType;
import gq.yozakura.ui.engine.layout.LayoutBox;
import gq.yozakura.ui.engine.layout.LayoutEngine;
import gq.yozakura.ui.engine.layout.MeasureContext;
import gq.yozakura.ui.engine.paint.PaintCommandList;
import gq.yozakura.ui.engine.paint.PaintTreeBuilder;
import gq.yozakura.ui.engine.paint.PaintVisualState;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 文档上下文：YozakuraUI 引擎的组合根（retained pipeline 协调器）。
 *
 * <p>AGENTS.md 契约（retained pipeline + Dirty-State Model）：
 * <pre>
 *   HTML -> DOM
 *   CSS -> Stylesheet
 *   DOM + Stylesheet -> ComputedStyle
 *   ComputedStyle -> LayoutBox tree
 *   LayoutBox tree -> PaintCommandList
 *   PaintCommandList -> batched OpenGL rendering
 * </pre>
 *
 * <p>组合以下组件：
 * <ul>
 *   <li>{@link ElementNode} DOM 根</li>
 *   <li>{@link Stylesheet} CSS 样式表</li>
 *   <li>{@link StyleResolver} 计算 ComputedStyle</li>
 *   <li>{@link LayoutEngine} 计算 LayoutBox 树</li>
 *   <li>{@link PaintTreeBuilder} 生成 PaintCommandList</li>
 *   <li>{@link InputDispatcher} + {@link HitTester} 输入路由</li>
 *   <li>{@link TransitionRunner} + {@link AnimationClock} 动画</li>
 * </ul>
 *
 * <p>dirty 协调（AGENTS.md "Dirty-State Model"）：
 * <ul>
 *   <li>STYLE_DIRTY → 重算 style + layout + commands</li>
 *   <li>LAYOUT_DRITY → 重算 layout + commands</li>
 *   <li>PAINT_DIRTY → 重算 commands</li>
 *   <li>COMMANDS_DIRTY → 仅重算 commands（paint tree builder 重新生成）</li>
 * </ul>
 *
 * <p>线程模型：单线程（UI 线程 / 渲染线程）。非线程安全。
 *
 * <p>阶段 6 实现：基础组合 + dirty 协调 + 输入集成 + 动画 tick；
 * transition 与 binding 的深度集成记录为后续切片。
 */
public final class DocumentContext implements gq.yozakura.ui.engine.binding.DirtyFlagSink {

    private static final String[] LAYOUT_PROPERTIES = {
            "display", "position", "width", "height", "min-width", "max-width",
            "min-height", "max-height", "margin", "margin-top", "margin-right",
            "margin-bottom", "margin-left", "padding", "padding-top", "padding-right",
            "padding-bottom", "padding-left", "border", "border-width", "border-top-width",
            "border-right-width", "border-bottom-width", "border-left-width", "box-sizing",
            "flex-direction", "flex-grow", "flex-shrink", "flex-basis", "justify-content",
            "align-items", "align-self", "gap", "left", "right", "top", "bottom",
            "overflow", "font-family", "font-size", "font-weight", "line-height",
            "white-space"
    };

    private final ElementNode rootElement;
    private final Stylesheet stylesheet;
    private final StyleResolver styleResolver;
    private final LayoutEngine layoutEngine;
    private final PaintTreeBuilder paintTreeBuilder;

    private MeasureContext measureContext;
    private int viewportWidth;
    private int viewportHeight;

    private Map<ElementNode, ComputedStyle> styles;
    private LayoutBox layoutRoot;
    private PaintCommandList paintCommands;
    private final Map<ElementNode, PaintVisualState> visualStates =
            new IdentityHashMap<ElementNode, PaintVisualState>();

    private final InteractionState interactionState;
    private final InputDispatcher inputDispatcher;
    private final AnimationClock animationClock;
    private final TransitionStore transitionStore;
    private final TransitionRunner transitionRunner;

    // dirty 标志
    private boolean styleDirty;
    private boolean layoutDirty;
    private boolean paintDirty;
    private boolean commandsDirty;
    private boolean interactionStyleDirty;
    private int styleRecomputeCount;
    private int layoutRecomputeCount;
    private int paintRecomputeCount;
    private int hitTestCount;
    private float cachedPointerX;
    private float cachedPointerY;
    private float cachedPointerOriginX;
    private float cachedPointerOriginY;
    private int cachedPointerLayoutCount = -1;
    private ElementNode cachedPointerHit;

    public DocumentContext(ElementNode root, Stylesheet stylesheet,
                            MeasureContext measureContext) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        if (stylesheet == null) {
            throw new IllegalArgumentException("stylesheet must not be null");
        }
        if (measureContext == null) {
            throw new IllegalArgumentException("measureContext must not be null");
        }
        this.rootElement = root;
        this.stylesheet = stylesheet;
        this.measureContext = measureContext;
        this.viewportWidth = measureContext.viewportWidth();
        this.viewportHeight = measureContext.viewportHeight();

        this.styleResolver = new StyleResolver();
        this.layoutEngine = new LayoutEngine();
        this.paintTreeBuilder = new PaintTreeBuilder();

        this.interactionState = new InteractionState();
        this.inputDispatcher = new InputDispatcher(interactionState);
        this.animationClock = new AnimationClock();
        this.transitionStore = new TransitionStore();
        // TransitionListener：将 progress/completed 转为 dirty 标记
        // MVP 简化：completed 不携带 element；progress 标 PAINT 或 LAYOUT dirty
        this.transitionRunner = new TransitionRunner(animationClock, transitionStore,
                new TransitionListener() {
                    @Override
                    public void onTransitionProgress(gq.yozakura.ui.engine.dom.ElementNode element,
                                                      String property, float value,
                                                      boolean layoutAffecting) {
                        if (layoutAffecting) {
                            markLayoutDirty();
                        } else {
                            markPaintDirty();
                        }
                    }
                    @Override
                    public void onTransitionCompleted(gq.yozakura.ui.engine.dom.ElementNode element,
                                                       String property, float finalValue,
                                                       boolean layoutAffecting) {
                        if (layoutAffecting) {
                            markLayoutDirty();
                        } else {
                            markPaintDirty();
                        }
                    }
                });

        // 初始全量计算
        this.styleDirty = true;
        this.layoutDirty = true;
        this.paintDirty = true;
        this.commandsDirty = true;
        recompute();
    }

    // ---- 查询 ----

    public ElementNode rootElement() { return rootElement; }
    public Stylesheet stylesheet() { return stylesheet; }
    public Map<ElementNode, ComputedStyle> styles() { return styles; }
    public LayoutBox layoutRoot() { return layoutRoot; }
    public PaintCommandList paintCommands() { return paintCommands; }
    public InputDispatcher inputDispatcher() { return inputDispatcher; }
    public InteractionState interactionState() { return interactionState; }
    public AnimationClock animationClock() { return animationClock; }
    public TransitionRunner transitionRunner() { return transitionRunner; }
    public TransitionStore transitionStore() { return transitionStore; }

    public int viewportWidth() { return viewportWidth; }
    public int viewportHeight() { return viewportHeight; }

    // ---- dirty 查询 ----

    public boolean isStyleDirty() { return styleDirty; }
    public boolean isLayoutDirty() { return layoutDirty; }
    public boolean isPaintDirty() { return paintDirty; }
    public boolean isCommandsDirty() { return commandsDirty; }
    public int styleRecomputeCount() { return styleRecomputeCount; }
    public int layoutRecomputeCount() { return layoutRecomputeCount; }
    public int paintRecomputeCount() { return paintRecomputeCount; }
    public int hitTestCount() { return hitTestCount; }

    public void setPaintVisualState(ElementNode element, PaintVisualState state) {
        if (element == null || state == null) return;
        visualStates.put(element, state);
        markPaintDirty();
    }

    public void clearPaintVisualState(ElementNode element) {
        if (element != null && visualStates.remove(element) != null) markPaintDirty();
    }

    // ---- dirty 标记（实现 DirtyFlagSink）----

    @Override
    public void markStyleDirty() {
        styleDirty = true;
        interactionStyleDirty = false;
        commandsDirty = true;
    }

    @Override
    public void markLayoutDirty() {
        layoutDirty = true;
        commandsDirty = true;
    }

    @Override
    public void markPaintDirty() {
        paintDirty = true;
        commandsDirty = true;
    }

    /** 清除所有 dirty 标志（用于测试或外部强制 clean 状态）。 */
    public void clearAllDirty() {
        styleDirty = false;
        layoutDirty = false;
        paintDirty = false;
        commandsDirty = false;
    }

    // ---- 重算 pipeline ----

    /**
     * 按需重算 pipeline。
     *
     * <p>顺序：style → layout → commands；任一前置 dirty 则后续也重算。
     */
    public void recompute() {
        if (styleDirty) {
            boolean interactionOnly = interactionStyleDirty;
            Map<ElementNode, ComputedStyle> resolved = styleResolver.resolve(stylesheet, rootElement);
            styleRecomputeCount++;
            boolean affectsLayout = layoutDirty || layoutStylesChanged(styles, resolved);
            boolean stylesChanged = computedStylesChanged(styles, resolved);
            styles = resolved;
            styleDirty = false;
            interactionStyleDirty = false;
            if (affectsLayout) {
                layoutDirty = true;
            } else if (stylesChanged || !interactionOnly) {
                paintDirty = true;
            }
        }
        if (layoutDirty) {
            layoutRoot = layoutEngine.layout(rootElement, styles, measureContext);
            layoutRecomputeCount++;
            layoutDirty = false;
            commandsDirty = true;  // layout 变化触发 commands
        }
        if (commandsDirty || paintDirty) {
            if (layoutRoot != null) {
                paintCommands = paintTreeBuilder.build(layoutRoot, styles, visualStates);
            } else {
                paintCommands = new PaintCommandList();
            }
            paintRecomputeCount++;
            commandsDirty = false;
            paintDirty = false;
        }
    }

    private static boolean layoutStylesChanged(Map<ElementNode, ComputedStyle> before,
                                               Map<ElementNode, ComputedStyle> after) {
        if (before == null || after == null || before.size() != after.size()) return true;
        for (Map.Entry<ElementNode, ComputedStyle> entry : after.entrySet()) {
            ComputedStyle previous = before.get(entry.getKey());
            if (previous == null) return true;
            ComputedStyle current = entry.getValue();
            for (int i = 0; i < LAYOUT_PROPERTIES.length; i++) {
                if (!same(previous.get(LAYOUT_PROPERTIES[i]), current.get(LAYOUT_PROPERTIES[i]))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean computedStylesChanged(Map<ElementNode, ComputedStyle> before,
                                                 Map<ElementNode, ComputedStyle> after) {
        if (before == null || after == null || before.size() != after.size()) return true;
        for (Map.Entry<ElementNode, ComputedStyle> entry : after.entrySet()) {
            ComputedStyle previous = before.get(entry.getKey());
            ComputedStyle current = entry.getValue();
            if (previous == null || !previous.propertyNames().equals(current.propertyNames())
                    || !previous.customPropertyNames().equals(current.customPropertyNames())) {
                return true;
            }
            for (String property : current.propertyNames()) {
                if (!same(previous.get(property), current.get(property))) return true;
            }
            for (String property : current.customPropertyNames()) {
                if (!same(previous.customProperty(property), current.customProperty(property))) return true;
            }
        }
        return false;
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    // ---- 输入 ----

    /**
     * 分发指针事件：先做命中测试，再路由到 InputDispatcher。
     *
     * <p>坐标 (x, y) 为逻辑像素，相对视口左上角；
     * (originX, originY) 为视口原点在 Minecraft 屏幕中的偏移（用于多窗口/嵌入场景）。
     *
     * <p>命中测试使用当前 layoutRoot（若为 null 则 hit=null）。
     *
     * @param event   指针事件
     * @param originX 视口原点 X（逻辑像素）
     * @param originY 视口原点 Y（逻辑像素）
     */
    public void dispatchPointer(PointerEvent event, float originX, float originY) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        // 确保 layout 是最新的（hit test 依赖最新几何）
        recompute();

        ElementNode hit;
        boolean reusableMove = event.type() == PointerEventType.MOVE
                && cachedPointerLayoutCount == layoutRecomputeCount
                && Float.floatToIntBits(cachedPointerX) == Float.floatToIntBits(event.x())
                && Float.floatToIntBits(cachedPointerY) == Float.floatToIntBits(event.y())
                && Float.floatToIntBits(cachedPointerOriginX) == Float.floatToIntBits(originX)
                && Float.floatToIntBits(cachedPointerOriginY) == Float.floatToIntBits(originY);
        if (reusableMove) {
            hit = cachedPointerHit;
        } else {
            hit = resolveHit(event.x() - originX, event.y() - originY);
            if (event.type() == PointerEventType.MOVE) {
                cachedPointerX = event.x();
                cachedPointerY = event.y();
                cachedPointerOriginX = originX;
                cachedPointerOriginY = originY;
                cachedPointerLayoutCount = layoutRecomputeCount;
                cachedPointerHit = hit;
            }
        }

        inputDispatcher.dispatch(event, hit);

        // 输入状态变化（hover/active/focus）标 STYLE_DIRTY
        if (interactionState.isStyleDirty()) {
            if (!styleDirty) interactionStyleDirty = true;
            styleDirty = true;
            interactionState.clearStyleDirty();
        }
    }

    /** Returns the element at a logical pointer coordinate using the current layout. */
    public ElementNode hitTest(float x, float y) {
        recompute();
        return resolveHit(x, y);
    }

    private ElementNode resolveHit(float x, float y) {
        if (layoutRoot == null) return null;
        hitTestCount++;
        LayoutBox box = HitTester.hit(layoutRoot, styles, x, y);
        return box == null ? null : box.element();
    }

    /** Returns {x,y,width,height} for an element's border box in document logical coordinates. */
    public float[] logicalBounds(ElementNode element) {
        if (element == null) return null;
        recompute();
        return layoutRoot == null ? null : findBounds(layoutRoot, element, 0.0F, 0.0F);
    }

    private static float[] findBounds(LayoutBox box, ElementNode target,
                                      float parentContentX, float parentContentY) {
        float x = parentContentX + box.borderBoxX();
        float y = parentContentY + box.borderBoxY();
        if (box.element() == target) {
            return new float[]{x, y, box.borderBoxWidth(), box.borderBoxHeight()};
        }
        float contentX = x + box.border().left() + box.padding().left();
        float contentY = y + box.border().top() + box.padding().top();
        for (int i = 0; i < box.childCount(); i++) {
            float[] result = findBounds(box.child(i), target, contentX, contentY);
            if (result != null) return result;
        }
        return null;
    }

    // ---- 动画 tick ----

    /**
     * 推进一帧。
     *
     * <p>步骤：
     * <ol>
     *   <li>推进动画时钟到 nowMs</li>
     *   <li>若有活跃过渡，调用 transitionRunner.tick 触发 progress/completed 回调</li>
     *   <li>回调会标记 PAINT/LAYOUT dirty</li>
     *   <li>调用 recompute 应用 dirty</li>
     * </ol>
     *
     * <p>无活跃动画时仍推进时钟（保证时间一致性），但 recompute 为 no-op。
     *
     * @param nowMs 当前时间（毫秒，单调）
     */
    public void tick(long nowMs) {
        animationClock.advanceTo(nowMs);
        if (animationClock.hasActiveAnimations()) {
            transitionRunner.tick(nowMs);
            recompute();
        }
    }

    // ---- 视口变化（GUI Scale）----

    /**
     * 设置视口尺寸（GUI Scale 变化时由 host 调用）。
     *
     * <p>标记 LAYOUT_DIRTY，下次 recompute 重新布局。
     */
    public void setViewport(int width, int height) {
        if (width == viewportWidth && height == viewportHeight) {
            return;
        }
        this.viewportWidth = width;
        this.viewportHeight = height;
        // 重建 MeasureContext（简单实现：内部匿名类）
        final int w = width;
        final int h = height;
        final float rootFont = measureContext.rootFontSizePx();
        this.measureContext = new MeasureContext() {
            @Override public int viewportWidth() { return w; }
            @Override public int viewportHeight() { return h; }
            @Override public float rootFontSizePx() { return rootFont; }
        };
        markLayoutDirty();
    }

    // ---- 关闭 UI 清理 ----

    /**
     * 关闭 UI 时清理：释放输入状态、停止动画、清除过渡。
     *
     * <p>AGENTS.md："Closing the UI restores cursor, repeat-key and focus state."
     */
    public void clearAll() {
        inputDispatcher.clearAll();
        transitionRunner.cancelAll();
        animationClock.reset();
        visualStates.clear();
    }
}
