package gq.yozakura.ui.engine.text;

/** Render-thread texture operations used by {@link GlyphAtlas}. */
public interface GlyphTextureBackend {
    int createPage(int width, int height);
    void uploadAlpha(int textureId, int x, int y, int width, int height, byte[] alpha);
    void deletePage(int textureId);
}
