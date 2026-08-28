package gq.yozakura.ui.click.qml;

import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import gq.yozakura.util.minecraft.Helper;
import io.github.timer_err.qml4j.render.QmlView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Mouse;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.nio.IntBuffer;

public final class QmlClickGuiScreen extends GuiScreen {
    private QmlClickGuiRuntime runtime;
    private float contentX;
    private float contentY;
    private float contentScale = 1.0F;
    private int guiScaleFactor = 1;
    private final QmlWindowGeometry geometry = new QmlWindowGeometry(
            QmlClickGuiRuntime.WIDTH, QmlClickGuiRuntime.HEIGHT);
    private Cursor hiddenCursor;

    public static void open(Minecraft minecraft) {
        minecraft.displayGuiScreen(new QmlClickGuiScreen());
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        updateTransform();
        try {
            runtime = QmlClickGuiRuntime.open();
            installCustomCursor();
        } catch (Throwable throwable) {
            Helper.sendMessage("QML ClickGUI failed: " + rootMessage(throwable));
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (runtime == null) {
            return;
        }
        updateTransform();
        if (geometry.isInteracting()) {
            geometry.updatePointer(mouseX, mouseY);
            copyGeometry();
        }
        float qmlX = toQmlX(mouseX);
        float qmlY = toQmlY(mouseY);
        if (!geometry.isInteracting()) {
            runtime.dispatchPointerMove(qmlX, qmlY);
        }
        try {
            runtime.renderIfNeeded();
            if (runtime.consumeCloseRequest()) {
                mc.displayGuiScreen(null);
                return;
            }
            drawQmlTexture(runtime.textureId());
        } catch (Throwable throwable) {
            Helper.sendMessage("QML ClickGUI render failed: " + rootMessage(throwable));
            mc.displayGuiScreen(null);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawClientCursor(mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (runtime != null && isInside(mouseX, mouseY)) {
            float qmlX = toQmlX(mouseX);
            float qmlY = toQmlY(mouseY);
            if (mouseButton == 0 && isResizeHandle(qmlX, qmlY)) {
                geometry.beginResize(mouseX, mouseY);
                return;
            }
            if (mouseButton == 0 && isTitleDragArea(qmlX, qmlY)) {
                geometry.beginMove(mouseX, mouseY);
                return;
            }
            runtime.dispatchPointerDown(qmlX, qmlY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (geometry.isInteracting()) {
            geometry.endInteraction();
            return;
        }
        if (runtime != null) {
            runtime.dispatchPointerUp(toQmlX(mouseX), toQmlY(mouseY), state);
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (runtime == null || wheel == 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (isInside(mouseX, mouseY)) {
            runtime.dispatchWheel(toQmlX(mouseX), toQmlY(mouseY), wheel > 0 ? 1.0F : -1.0F);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        Module clickGui = ModuleManager.getModule("ClickGUI");
        int closeKey = clickGui == null ? Keyboard.KEY_NONE : clickGui.getKey();
        if (keyCode == Keyboard.KEY_ESCAPE || closeKey != Keyboard.KEY_NONE && keyCode == closeKey) {
            mc.displayGuiScreen(null);
            return;
        }
        if (runtime != null) {
            int qmlKey = qmlKey(keyCode);
            String text = typedChar >= 32 && typedChar != 127 ? String.valueOf(typedChar) : "";
            boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                    || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            runtime.dispatchKey(qmlKey, text, true, shift);
            runtime.dispatchKey(qmlKey, text, false, shift);
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        restoreSystemCursor();
        if (runtime != null) {
            runtime.dispose();
            runtime = null;
        }
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void updateTransform() {
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        guiScaleFactor = Math.max(1, scaledResolution.getScaleFactor());
        geometry.updateViewport(width, height, guiScaleFactor);
        copyGeometry();
    }

    private void copyGeometry() {
        contentX = Math.round(geometry.x() * guiScaleFactor) / (float) guiScaleFactor;
        contentY = Math.round(geometry.y() * guiScaleFactor) / (float) guiScaleFactor;
        contentScale = geometry.scale();
    }

    private boolean isInside(float x, float y) {
        return x >= contentX && y >= contentY
                && x <= contentX + QmlClickGuiRuntime.WIDTH * contentScale
                && y <= contentY + QmlClickGuiRuntime.HEIGHT * contentScale;
    }

    private float toQmlX(float x) {
        return (x - contentX) / contentScale;
    }

    private float toQmlY(float y) {
        return (y - contentY) / contentScale;
    }

    private static boolean isTitleDragArea(float x, float y) {
        return x >= 8.0F && x < 808.0F && y >= 8.0F && y <= 66.0F;
    }

    private static boolean isResizeHandle(float x, float y) {
        return x >= QmlClickGuiRuntime.WIDTH - 20.0F
                && y >= QmlClickGuiRuntime.HEIGHT - 20.0F;
    }

    private void drawQmlTexture(int textureId) {
        float right = contentX + QmlClickGuiRuntime.WIDTH * contentScale;
        float bottom = contentY + QmlClickGuiRuntime.HEIGHT * contentScale;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.bindTexture(textureId);
            int filter = QmlTextureSampling.isPixelPerfect(contentScale, guiScaleFactor)
                    ? GL11.GL_NEAREST : GL11.GL_LINEAR;
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer renderer = tessellator.getWorldRenderer();
            renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            renderer.pos(contentX, bottom, 0.0D).tex(0.0D, 1.0D).endVertex();
            renderer.pos(right, bottom, 0.0D).tex(1.0D, 1.0D).endVertex();
            renderer.pos(right, contentY, 0.0D).tex(1.0D, 0.0D).endVertex();
            renderer.pos(contentX, contentY, 0.0D).tex(0.0D, 0.0D).endVertex();
            tessellator.draw();
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
            gq.yozakura.engine.render.GLStateManager.syncToCurrent();
        }
    }

    private void installCustomCursor() {
        if (!Mouse.isCreated()) {
            return;
        }
        try {
            IntBuffer pixels = BufferUtils.createIntBuffer(1);
            pixels.put(0x00000000);
            pixels.flip();
            hiddenCursor = new Cursor(1, 1, 0, 0, 1, pixels, null);
            Mouse.setNativeCursor(hiddenCursor);
        } catch (LWJGLException error) {
            hiddenCursor = null;
            Helper.sendMessage("QML ClickGUI cursor failed: " + rootMessage(error));
        }
    }

    private void restoreSystemCursor() {
        try {
            if (Mouse.isCreated()) {
                Mouse.setNativeCursor(null);
            }
        } catch (LWJGLException ignored) {
        }
        if (hiddenCursor != null) {
            hiddenCursor.destroy();
            hiddenCursor = null;
        }
    }

    private static void drawClientCursor(float mouseX, float mouseY) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.72F);
            GL11.glLineWidth(1.25F);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(mouseX, mouseY);
            GL11.glVertex2f(mouseX + 3.5F, mouseY + 13.0F);
            GL11.glVertex2f(mouseX + 6.5F, mouseY + 8.5F);
            GL11.glVertex2f(mouseX + 11.5F, mouseY + 8.0F);
            GL11.glEnd();

            GL11.glColor4f(0.025F, 0.025F, 0.03F, 1.0F);
            GL11.glBegin(GL11.GL_POLYGON);
            GL11.glVertex2f(mouseX + 0.8F, mouseY + 1.0F);
            GL11.glVertex2f(mouseX + 3.8F, mouseY + 11.5F);
            GL11.glVertex2f(mouseX + 6.0F, mouseY + 7.6F);
            GL11.glVertex2f(mouseX + 10.2F, mouseY + 7.4F);
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
            gq.yozakura.engine.render.GLStateManager.syncToCurrent();
        }
    }

    private static int qmlKey(int keyCode) {
        switch (keyCode) {
            case Keyboard.KEY_BACK:
                return QmlView.KEY_BACKSPACE;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER:
                return QmlView.KEY_ENTER;
            case Keyboard.KEY_LEFT:
                return QmlView.KEY_LEFT;
            case Keyboard.KEY_RIGHT:
                return QmlView.KEY_RIGHT;
            case Keyboard.KEY_UP:
                return QmlView.KEY_UP;
            case Keyboard.KEY_DOWN:
                return QmlView.KEY_DOWN;
            case Keyboard.KEY_HOME:
                return QmlView.KEY_HOME;
            case Keyboard.KEY_END:
                return QmlView.KEY_END;
            case Keyboard.KEY_TAB:
                return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)
                        ? QmlView.KEY_BACKTAB : QmlView.KEY_TAB;
            default:
                return keyCode;
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.trim().isEmpty()
                ? current.getClass().getSimpleName() : message;
    }
}
