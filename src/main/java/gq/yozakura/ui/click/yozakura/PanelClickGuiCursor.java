package gq.yozakura.ui.click.yozakura;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Mouse;

import java.nio.IntBuffer;

/** Owns the Panel ClickGUI hardware cursor and restores the cursor it replaced. */
public final class PanelClickGuiCursor {
    private Cursor panelCursor;
    private Cursor previousCursor;
    private boolean installed;

    public void install() {
        if (installed || !Mouse.isCreated()) {
            return;
        }
        previousCursor = Mouse.getNativeCursor();
        try {
            int minimumSize = Cursor.getMinCursorSize();
            int maximumSize = Cursor.getMaxCursorSize();
            int cursorSize = Math.max(32, minimumSize);
            if (cursorSize > maximumSize) {
                previousCursor = null;
                return;
            }
            PanelClickGuiCursorImage image = PanelClickGuiCursorImage.outlinedArrow(cursorSize);
            if (PanelClickGuiWindowsAlphaCursor.install(image)) {
                installed = true;
                return;
            }
            int[] pixels = image.nativeArgbPixels();
            IntBuffer buffer = BufferUtils.createIntBuffer(pixels.length);
            buffer.put(pixels);
            buffer.flip();
            panelCursor = new Cursor(image.width(), image.height(),
                    image.hotspotX(), image.lwjglHotspotY(), 1, buffer, null);
            Mouse.setNativeCursor(panelCursor);
            installed = true;
        } catch (LWJGLException exception) {
            destroyPanelCursor();
            previousCursor = null;
        } catch (RuntimeException exception) {
            destroyPanelCursor();
            previousCursor = null;
        } catch (LinkageError error) {
            destroyPanelCursor();
            previousCursor = null;
        }
    }

    public void restore() {
        if (!installed && panelCursor == null) {
            return;
        }
        try {
            PanelClickGuiWindowsAlphaCursor.restore();
            if (Mouse.isCreated()) {
                Mouse.setNativeCursor(previousCursor);
            }
        } catch (LWJGLException ignored) {
        } finally {
            installed = false;
            previousCursor = null;
            destroyPanelCursor();
        }
    }

    private void destroyPanelCursor() {
        if (panelCursor != null) {
            panelCursor.destroy();
            panelCursor = null;
        }
    }
}
