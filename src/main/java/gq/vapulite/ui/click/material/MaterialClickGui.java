package gq.vapulite.ui.click.material;

import gq.vapulite.core.Client;
import gq.vapulite.engine.font.FontLoaders;
import gq.vapulite.engine.render.GLStateManager;
import gq.vapulite.engine.render.ShaderRenderer;
import gq.vapulite.engine.render.ui.RenderServices;
import gq.vapulite.manager.ModuleManager;
import gq.vapulite.module.Module;
import gq.vapulite.module.ModuleType;
import gq.vapulite.module.render.ClickGUI;
import gq.vapulite.value.Value;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 全新的 MD3 x Liquid Glass ClickGUI。
 *
 * <p>该类只负责窗口生命周期、全局状态和事件分发；侧栏、模块网格、卡片和值控件
 * 都拆到独立类中，避免继续堆到一个巨大 GuiScreen 里。</p>
 */
public final class MaterialClickGui extends GuiScreen {
    private static ModuleType currentType = ModuleType.Combat;

    private final MaterialClickTheme theme = new MaterialClickTheme();
    private final MaterialClickSidebar sidebar = new MaterialClickSidebar(this);
    private final MaterialModuleGrid grid = new MaterialModuleGrid(this);

    private MaterialClickLayout layout;
    private Module bindingModule;
    private boolean draggingWindow;
    private boolean savedOnClose;
    private float dragOffsetX;
    private float dragOffsetY;
    private float openProgress;
    private float frameScale = 1.0f;
    private long lastFrameNanos = System.nanoTime();

    MaterialClickTheme theme() {
        return theme;
    }

    MaterialClickLayout layout() {
        return layout;
    }

    ModuleType currentType() {
        return currentType;
    }

    float frameScale() {
        return frameScale;
    }

    void setCurrentType(ModuleType type) {
        if (type == null || currentType == type) {
            return;
        }
        currentType = type;
        grid.resetScroll();
    }

    @Override
    public void initGui() {
        layout = MaterialClickLayout.calculate(new ScaledResolution(mc));
        openProgress = 0.0f;
        savedOnClose = false;
        grid.resetScroll();
        super.initGui();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateFrameScale();
        ScaledResolution sr = new ScaledResolution(mc);
        layout = MaterialClickLayout.calculate(sr);
        updateWindowDrag(mouseX, mouseY, sr);

        openProgress = animate(openProgress, 1.0f, 0.18f);
        theme.setAlpha(openProgress * ClickGUI.clickGuiAlpha.getValue().floatValue());

        ShaderRenderer.invalidateFrostedGlass();
        drawWindowShell();
        sidebar.render(mouseX, mouseY);
        grid.render(mouseX, mouseY);

        super.drawScreen(mouseX, mouseY, partialTicks);
        drawBindingOverlay(sr);
    }

    private void drawWindowShell() {
        RenderServices.liquidGlass().roundedBorder(layout.x, layout.y, layout.x + layout.w, layout.y + layout.h,
                layout.radius, 0.0f, theme.windowFill(), 0);
        RenderServices.shapes().line(layout.x + layout.sidebarW, layout.y + 18.0f * layout.scale,
                layout.x + layout.sidebarW, layout.y + layout.h - 18.0f * layout.scale,
                0.8f * layout.scale, theme.withAlpha(MaterialClickTheme.OUTLINE, 18.0f * theme.alpha()));
    }

    private void updateWindowDrag(int mouseX, int mouseY, ScaledResolution sr) {
        if (!draggingWindow) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            draggingWindow = false;
            return;
        }
        float x = MaterialClickLayout.clamp(mouseX - dragOffsetX, 8.0f,
                Math.max(8.0f, sr.getScaledWidth() - layout.w - 8.0f));
        float y = MaterialClickLayout.clamp(mouseY - dragOffsetY, 8.0f,
                Math.max(8.0f, sr.getScaledHeight() - layout.h - 8.0f));
        ClickGUI.windowX.setValue((double) x);
        ClickGUI.windowY.setValue((double) y);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (bindingModule != null) {
            return;
        }
        if (sidebar.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (grid.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (mouseButton == 0 && layout.inDragHeader(mouseX, mouseY)) {
            draggingWindow = true;
            dragOffsetX = mouseX - layout.x;
            dragOffsetY = mouseY - layout.y;
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingWindow = false;
        grid.mouseReleased();
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && layout != null) {
            ScaledResolution sr = new ScaledResolution(mc);
            int mouseX = Mouse.getEventX() * sr.getScaledWidth() / mc.displayWidth;
            int mouseY = sr.getScaledHeight() - Mouse.getEventY() * sr.getScaledHeight() / mc.displayHeight - 1;
            grid.mouseWheel(mouseX, mouseY, wheel);
        }
        super.handleMouseInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (bindingModule != null) {
            finishBinding(keyCode);
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RSHIFT) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        saveConfigOnClose();
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    void beginScissor(float x, float y, float w, float h) {
        GLStateManager.pushScissor(x, y, w, h);
    }

    void endScissor() {
        GLStateManager.popScissor();
    }

    void startBinding(Module module) {
        bindingModule = module;
    }

    boolean isBinding(Module module) {
        return bindingModule == module;
    }

    private void finishBinding(int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            bindingModule = null;
            return;
        }
        bindingModule.setKey(keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK ? 0 : keyCode);
        bindingModule = null;
    }

    private void drawBindingOverlay(ScaledResolution sr) {
        if (bindingModule == null) {
            return;
        }
        RenderServices.shapes().rect(0.0f, 0.0f, sr.getScaledWidth(), sr.getScaledHeight(),
                theme.withAlpha(0xFF000000, 96.0f * theme.alpha()));
        float boxW = 260.0f * layout.scale;
        float boxH = 86.0f * layout.scale;
        float x = (sr.getScaledWidth() - boxW) / 2.0f;
        float y = (sr.getScaledHeight() - boxH) / 2.0f;
        RenderServices.liquidGlass().roundedBorder(x, y, x + boxW, y + boxH, 18.0f * layout.scale,
                1.0f, theme.withAlpha(MaterialClickTheme.SURFACE, 220.0f * theme.alpha()),
                theme.withAlpha(MaterialClickTheme.PRIMARY, 78.0f * theme.alpha()));
        FontLoaders.F18.drawCenteredString("Press a key", x + boxW / 2.0f, y + 21.0f * layout.scale, theme.text());
        FontLoaders.F14.drawCenteredString(displayName(bindingModule) + "  |  DEL 清除",
                x + boxW / 2.0f, y + 51.0f * layout.scale, theme.muted());
    }

    private void saveConfigOnClose() {
        if (savedOnClose || Client.instance == null) {
            return;
        }
        savedOnClose = true;
        try {
            Client.SaveConfig();
        } catch (IOException ignored) {
        }
    }

    int moduleCount(ModuleType type) {
        int count = 0;
        List<Module> modules = ModuleManager.getModulesInType(type);
        for (Module module : modules) {
            if (module != null) {
                count++;
            }
        }
        return count;
    }

    int enabledCount(ModuleType type) {
        int count = 0;
        List<Module> modules = ModuleManager.getModulesInType(type);
        for (Module module : modules) {
            if (module != null && module.getState()) {
                count++;
            }
        }
        return count;
    }

    boolean isSelfModule(Module module) {
        return module != null && "clickgui".equals(normalize(module.getName()));
    }

    String displayName(Module module) {
        if (module == null) {
            return "";
        }
        return Client.CHINESE ? module.getChinese() : module.getName();
    }

    String description(Module module) {
        String desc = module == null ? "" : module.getDescription();
        return desc == null ? "" : desc;
    }

    String displayName(Value value) {
        if (value == null) {
            return "";
        }
        String display = value.getDisplayName();
        if (display == null || display.trim().length() == 0) {
            display = value.getName();
        }
        return display == null ? "" : display;
    }

    String keyName(int key) {
        if (key == 0) {
            return "NONE";
        }
        String name = Keyboard.getKeyName(key);
        return name == null ? "KEY " + key : "KEY " + name;
    }

    float animate(float current, float target, float speed) {
        return current + (target - current) * MaterialClickTheme.clamp(speed * frameScale, 0.0f, 1.0f);
    }

    float ease(float value) {
        float v = MaterialClickTheme.clamp(value, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - v, 3.0D);
    }

    private void updateFrameScale() {
        long now = System.nanoTime();
        long elapsed = now - lastFrameNanos;
        lastFrameNanos = now;
        if (elapsed <= 0L) {
            frameScale = 1.0f;
            return;
        }
        float measured = MaterialClickLayout.clamp(elapsed / 16666666.0f, 0.55f, 1.75f);
        frameScale += (measured - frameScale) * 0.18f;
    }

    static String normalize(String text) {
        return text == null ? "" : text.replace(" ", "").replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }
}
