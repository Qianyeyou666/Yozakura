package gq.yozakura.util.render;

import gq.yozakura.engine.render.ui.VisualPalette;
import gq.yozakura.value.Numbers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HudDrag {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final HudDragManager MANAGER = new HudDragManager();
    private static final VisualPalette PALETTE = VisualPalette.nightBloom();
    private static final HudDockingCoordinator DOCKING = new HudDockingCoordinator();
    private static final Map<String, DockBinding> DOCK_BINDINGS = new LinkedHashMap<String, DockBinding>();
    private static final long DOCK_STALE_NANOS = 180000000L;
    private static HudDockingCoordinator.Snapshot dockingSnapshot;
    private static long lastDockUpdateNanos;

    private HudDrag() {
    }

    public static boolean isEditMode() {
        return MC.currentScreen instanceof GuiChat;
    }

    public static float[] update(String id, Numbers<Double> xValue, Numbers<Double> yValue,
                                 float defaultX, float defaultY, float width, float height,
                                 ScaledResolution sr) {
        return update(id, xValue, yValue, null, defaultX, defaultY, width, height, sr);
    }

    public static float[] update(String id, Numbers<Double> xValue, Numbers<Double> yValue,
                                 Numbers<Double> scaleValue, float defaultX, float defaultY,
                                 float width, float height, ScaledResolution sr) {
        if (sr == null) {
            throw new IllegalArgumentException("scaled resolution must not be null");
        }
        HudDragSession.Bounds bounds = new HudDragSession.Bounds(sr.getScaledWidth(), sr.getScaledHeight(), width, height);
        HudDragManager.Frame frame = new HudDragManager.Frame(isEditMode(), logicalMouseX(sr), logicalMouseY(sr),
                Mouse.isButtonDown(0), Keyboard.isKeyDown(Keyboard.KEY_ESCAPE));
        return asArray(MANAGER.update(id, xValue, yValue, defaultX, defaultY, bounds, frame));
    }

    /**
     * Updates a Night Bloom HUD node through the shared docking graph.  Width, height and radius
     * are already in logical screen pixels, so callers must pass their scaled bounds.
     */
    public static float[] updateDocked(String id, Numbers<Double> xValue, Numbers<Double> yValue,
                                       Numbers<Double> scaleValue, float defaultX, float defaultY,
                                       float width, float height, float radius,
                                       Set<HudDockingCoordinator.Side> sides, ScaledResolution sr) {
        return updateDocked(id, xValue, yValue, scaleValue, defaultX, defaultY, width, height, radius,
                sides, true, sr);
    }

    public static float[] updateDocked(String id, Numbers<Double> xValue, Numbers<Double> yValue,
                                       Numbers<Double> scaleValue, float defaultX, float defaultY,
                                       float width, float height, float radius,
                                       Set<HudDockingCoordinator.Side> sides, boolean compositeEligible,
                                       ScaledResolution sr) {
        if (id == null || id.length() == 0 || sr == null) {
            return update(id, xValue, yValue, scaleValue, defaultX, defaultY, width, height, sr);
        }
        long now = System.nanoTime();
        DockBinding binding = DOCK_BINDINGS.get(id);
        if (binding == null) {
            binding = new DockBinding(id, xValue, yValue);
            DOCK_BINDINGS.put(id, binding);
        } else {
            binding.xValue = xValue;
            binding.yValue = yValue;
        }
        binding.inputX = resolvePosition(xValue, defaultX);
        binding.inputY = resolvePosition(yValue, defaultY);
        binding.width = Math.max(0.0F, width);
        binding.height = Math.max(0.0F, height);
        binding.radius = Math.max(0.0F, radius);
        binding.sides = sides == null || sides.isEmpty()
                ? EnumSet.noneOf(HudDockingCoordinator.Side.class) : EnumSet.copyOf(sides);
        binding.compositeEligible = compositeEligible;
        binding.lastSeenNanos = now;

        binding.movable = true;
        advanceDocking(sr, now, true);
        HudDockingCoordinator.NodeView node = dockingSnapshot.getNode(id);
        float resolvedX = node == null ? binding.inputX : node.getX();
        float resolvedY = node == null ? binding.inputY : node.getY();
        MANAGER.observe(id, xValue, yValue, defaultX, defaultY, resolvedX, resolvedY,
                binding.width, binding.height);
        return new float[]{resolvedX, resolvedY};
    }

    public static float[] updateDocked(String id, Numbers<Double> xValue, Numbers<Double> yValue,
                                       Numbers<Double> scaleValue, float defaultX, float defaultY,
                                       float width, float height, float radius, ScaledResolution sr) {
        return updateDocked(id, xValue, yValue, scaleValue, defaultX, defaultY, width, height, radius,
                HudDockingCoordinator.Side.all(), sr);
    }

    /**
     * Adds a renderer-owned node (currently the independently draggable Watermark tiles) to the
     * global graph without competing for its mouse input. Other HUD widgets can snap to it.
     */
    public static float[] registerDockedPassive(String id, float x, float y, float width, float height,
                                                float radius, Set<HudDockingCoordinator.Side> sides,
                                                ScaledResolution sr) {
        if (id == null || id.length() == 0 || sr == null) {
            return new float[]{x, y};
        }
        long now = System.nanoTime();
        DockBinding binding = DOCK_BINDINGS.get(id);
        if (binding == null) {
            binding = new DockBinding(id, null, null);
            DOCK_BINDINGS.put(id, binding);
        }
        binding.inputX = x;
        binding.inputY = y;
        binding.width = Math.max(0.0F, width);
        binding.height = Math.max(0.0F, height);
        binding.radius = Math.max(0.0F, radius);
        binding.sides = sides == null || sides.isEmpty()
                ? EnumSet.noneOf(HudDockingCoordinator.Side.class) : EnumSet.copyOf(sides);
        binding.movable = false;
        binding.compositeEligible = false;
        binding.lastSeenNanos = now;
        advanceDocking(sr, now, false);
        HudDockingCoordinator.NodeView node = dockingSnapshot == null ? null : dockingSnapshot.getNode(id);
        float resolvedX = node == null ? x : node.getX();
        float resolvedY = node == null ? y : node.getY();
        MANAGER.observe(id, null, null, x, y, resolvedX, resolvedY, width, height);
        return new float[]{resolvedX, resolvedY};
    }

    /** Completes a local renderer-owned drag by snapping its proxy into the shared HUD graph. */
    public static float[] snapDockedPassive(String id, float fallbackX, float fallbackY) {
        if (id == null || id.length() == 0 || dockingSnapshot == null) {
            return new float[]{fallbackX, fallbackY};
        }
        dockingSnapshot = DOCKING.attachNearest(id);
        persistDockedPositions(dockingSnapshot);
        HudDockingCoordinator.NodeView node = dockingSnapshot.getNode(id);
        return node == null ? new float[]{fallbackX, fallbackY} : new float[]{node.getX(), node.getY()};
    }

    /** Detaches a renderer-owned proxy before its local drag takes over its tile geometry. */
    public static void detachDocked(String id) {
        if (id == null || id.length() == 0 || dockingSnapshot == null || !dockingSnapshot.hasLink(id)) {
            return;
        }
        dockingSnapshot = DOCKING.detach(id);
        persistDockedPositions(dockingSnapshot);
    }

    public static HudDockingCoordinator.Snapshot getDockingSnapshot() {
        return dockingSnapshot;
    }

    public static void resetDocking() {
        DOCKING.reset();
        DOCK_BINDINGS.clear();
        dockingSnapshot = null;
        lastDockUpdateNanos = 0L;
    }

    /** Removes a permanently disabled widget while allowing transiently hidden widgets to retain links. */
    public static void unregisterDocked(String id) {
        if (id == null || id.length() == 0) {
            return;
        }
        DOCK_BINDINGS.remove(id);
    }

    public static void drawHint(String id, float x, float y, float width, float height, float radius) {
        if (!isEditMode()) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(MC);
        boolean hovered = isHovered(logicalMouseX(sr), logicalMouseY(sr), x, y, width, height);
        HudDragSession.DragState state = MANAGER.getState(id);
        float hoverProgress = MANAGER.updateHoverProgress(id, hovered, System.nanoTime());
        if (state == HudDragSession.DragState.SNAP_PREVIEW) {
            HudDragSession.Preview preview = MANAGER.getPreview(id);
            if (preview != null) {
                drawSnapGuides(sr, preview);
            }
        }
        int alpha = Math.round(255.0F * hoverProgress);
        if (alpha <= 0) {
            return;
        }
        RenderUtil.drawRoundedBorderedRect(x, y, x + width, y + height,
                Math.max(0.0F, radius), 0.5F, 0x00FFFFFF, withAlpha(0xFFFFFFFF, alpha));
    }

    /** Draws the stronger edit-mode state for a shared docking node without changing panel rendering. */
    public static void drawDockHint(String id, float x, float y, float width, float height, float radius) {
        if (!isEditMode() || dockingSnapshot == null) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(MC);
        boolean hovered = isHovered(logicalMouseX(sr), logicalMouseY(sr), x, y, width, height);
        boolean active = dockingSnapshot.isDragging(id);
        boolean selected = dockingSnapshot.isSelected(id);
        if (!hovered && !active && !selected) {
            return;
        }
        int color = active ? withAlpha(PALETTE.getAccentPrimary(), 0xD4)
                : selected ? withAlpha(PALETTE.getAccentAlt(), 0xB8)
                : withAlpha(PALETTE.getAccentPrimary(), 0x7A);
        RenderUtil.drawRoundedBorderedRect(x - 1.0F, y - 1.0F, x + width + 1.0F, y + height + 1.0F,
                Math.max(2.0F, radius + 1.0F), 1.0F, 0x00000000, color);
        if (active || selected) {
            RenderUtil.drawRoundedRect(x + width * 0.5F - 9.0F, y + 3.0F,
                    x + width * 0.5F + 9.0F, y + 4.5F, 0.75F, color);
        }
    }

    public static int mouseX(ScaledResolution sr) {
        return (int) logicalMouseX(sr);
    }

    public static int mouseY(ScaledResolution sr) {
        return (int) logicalMouseY(sr);
    }

    public static boolean isDragging(String id) {
        return MANAGER.isDragging(id);
    }

    public static boolean isSelected(String id) {
        return MANAGER.isSelected(id);
    }

    /**
     * 在编辑模式下，当鼠标悬停在 HUD 元素上时，通过滚轮调整缩放值。
     * 步进自动读取 scaleValue 的 increment。
     */
    public static void handleScroll(String id, Numbers<Double> scaleValue,
                                    float x, float y, float width, float height,
                                    float minScale, float maxScale) {
        if (!isEditMode() || scaleValue == null) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(MC);
        if (!isHovered(logicalMouseX(sr), logicalMouseY(sr), x, y, width, height)) {
            return;
        }
        int wheel = Mouse.getDWheel();
        if (wheel == 0) {
            return;
        }
        double current = scaleValue.getValue();
        double step = scaleValue.getIncrement().doubleValue();
        double delta = wheel > 0 ? step : -step;
        double next = Math.max(minScale, Math.min(maxScale, current + delta));
        next = Math.round(next * 100.0) / 100.0;
        if (Math.abs(next - current) > 0.0001) {
            scaleValue.setValue(next);
        }
    }

    private static float resolvePosition(Numbers<Double> value, float fallback) {
        if (value == null || value.getValue() == null || value.getValue() < 0.0D) {
            return fallback;
        }
        return value.getValue().floatValue();
    }

    private static void setNumber(Numbers<Double> value, float position) {
        if (value == null) {
            return;
        }
        double rounded = Math.round(position * 10.0f) / 10.0D;
        if (value.getValue() == null || Math.abs(value.getValue() - rounded) > 0.0001D) {
            value.setNumberValue(rounded);
        }
    }

    private static void persistDockedPositions(HudDockingCoordinator.Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        for (HudDockingCoordinator.NodeView node : snapshot.getNodes()) {
            DockBinding binding = DOCK_BINDINGS.get(node.getId());
            if (binding == null || !shouldPersistDockedPosition(snapshot, binding, node.getId())) {
                continue;
            }
            setNumber(binding.xValue, node.getTargetX());
            setNumber(binding.yValue, node.getTargetY());
        }
    }

    private static void advanceDocking(ScaledResolution sr, long now, boolean processInput) {
        List<HudDockingCoordinator.NodeInput> nodes = new ArrayList<HudDockingCoordinator.NodeInput>();
        Iterator<DockBinding> iterator = DOCK_BINDINGS.values().iterator();
        while (iterator.hasNext()) {
            DockBinding candidate = iterator.next();
            boolean visible = now - candidate.lastSeenNanos <= DOCK_STALE_NANOS;
            boolean linked = dockingSnapshot != null && dockingSnapshot.hasLink(candidate.id);
            if (!visible && !linked) {
                iterator.remove();
                continue;
            }
            nodes.add(new HudDockingCoordinator.NodeInput(candidate.id, candidate.inputX, candidate.inputY,
                    candidate.width, candidate.height, candidate.radius, candidate.sides, candidate.movable,
                    candidate.compositeEligible, visible));
        }
        float deltaSeconds = lastDockUpdateNanos == 0L ? 0.0F
                : Math.min(0.05F, Math.max(0.0F, (now - lastDockUpdateNanos) / 1000000000.0F));
        lastDockUpdateNanos = now;
        boolean editMode = isEditMode();
        dockingSnapshot = DOCKING.update(new HudDockingCoordinator.Frame(sr.getScaledWidth(), sr.getScaledHeight(),
                deltaSeconds, editMode, logicalMouseX(sr), logicalMouseY(sr), Mouse.isButtonDown(0),
                Mouse.isButtonDown(1), Keyboard.isKeyDown(Keyboard.KEY_ESCAPE), processInput, nodes));
        persistDockedPositions(dockingSnapshot);
    }

    private static boolean shouldPersistDockedPosition(HudDockingCoordinator.Snapshot snapshot,
                                                       DockBinding binding, String id) {
        boolean configured = binding.xValue != null && binding.yValue != null
                && binding.xValue.getValue() != null && binding.yValue.getValue() != null
                && binding.xValue.getValue() >= 0.0D && binding.yValue.getValue() >= 0.0D;
        return configured || snapshot.hasLink(id)
                || snapshot.isDirty() && snapshot.isSelected(id);
    }

    private static float[] asArray(HudDragSession.Position position) {
        return new float[]{position.getX(), position.getY()};
    }

    private static float logicalMouseX(ScaledResolution sr) {
        return HudDragSession.toLogicalCoordinate(Mouse.getX(), MC.displayWidth, sr.getScaledWidth());
    }

    private static float logicalMouseY(ScaledResolution sr) {
        return HudDragSession.toLogicalYFromBottom(Mouse.getY(), MC.displayHeight, sr.getScaledHeight());
    }

    private static void drawSnapGuides(ScaledResolution sr, HudDragSession.Preview preview) {
        int guideColor = withAlpha(PALETTE.getAccentAlt(), 0x9C);
        if (preview.getHorizontalSnap() != HudDragSession.SnapTarget.NONE) {
            float guideX = snapGuideX(sr.getScaledWidth(), preview.getHorizontalSnap());
            RenderUtil.drawRect(guideX - 0.5F, 2.0F, guideX + 0.5F, sr.getScaledHeight() - 2.0F, guideColor);
        }
        if (preview.getVerticalSnap() != HudDragSession.SnapTarget.NONE) {
            float guideY = snapGuideY(sr.getScaledHeight(), preview.getVerticalSnap());
            RenderUtil.drawRect(2.0F, guideY - 0.5F, sr.getScaledWidth() - 2.0F, guideY + 0.5F, guideColor);
        }
    }

    private static float snapGuideX(float screenWidth, HudDragSession.SnapTarget target) {
        if (target == HudDragSession.SnapTarget.CENTER) {
            return screenWidth * 0.5F;
        }
        return target == HudDragSession.SnapTarget.END ? screenWidth - HudDragSession.SAFE_MARGIN
                : HudDragSession.SAFE_MARGIN;
    }

    private static float snapGuideY(float screenHeight, HudDragSession.SnapTarget target) {
        if (target == HudDragSession.SnapTarget.CENTER) {
            return screenHeight * 0.5F;
        }
        return target == HudDragSession.SnapTarget.END ? screenHeight - HudDragSession.SAFE_MARGIN
                : HudDragSession.SAFE_MARGIN;
    }

    private static boolean isHovered(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static final class DockBinding {
        private final String id;
        private Numbers<Double> xValue;
        private Numbers<Double> yValue;
        private float inputX;
        private float inputY;
        private float width;
        private float height;
        private float radius;
        private Set<HudDockingCoordinator.Side> sides = HudDockingCoordinator.Side.all();
        private boolean movable = true;
        private boolean compositeEligible = true;
        private long lastSeenNanos;

        private DockBinding(String id, Numbers<Double> xValue, Numbers<Double> yValue) {
            this.id = id;
            this.xValue = xValue;
            this.yValue = yValue;
        }
    }
}
