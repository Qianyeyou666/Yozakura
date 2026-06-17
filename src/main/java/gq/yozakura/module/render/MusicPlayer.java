package gq.yozakura.module.render;

import gq.yozakura.bridge.YozakuraEventBridge;
import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.event.bridge.Render2DEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.render.music.CoverCache;
import gq.yozakura.module.render.music.Lyrics;
import gq.yozakura.module.render.music.Mp3MusicPlayer;
import gq.yozakura.module.render.music.NeteaseMusicApi;
import gq.yozakura.module.render.music.QrImageFactory;
import gq.yozakura.util.render.HudDrag;
import gq.yozakura.util.render.RenderUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MusicPlayer extends Module {
    private static final int TEXT = 0xFFF8ECF7;
    private static final int MUTED = 0xB9D8C8D8;
    private static final int FAINT = 0x72EBD8EA;
    private static final int PINK = 0xFFFF8EC5;
    private static final int PINK_SOFT = 0x88FF8EC5;
    private static final int GLASS = 0xB5121022;
    private static final int BORDER = 0x58FFB7DB;
    private static final int DARK = 0xDD0B0A16;

    public enum DisplayMode {
        EXPANDED,
        LYRICS,
        MINI
    }

    private enum LibraryTab {
        SEARCH,
        PLAYLISTS,
        QR_LOGIN
    }

    private final Mode<DisplayMode> displayMode = new Mode<DisplayMode>("Mode", "Mode", DisplayMode.values(), DisplayMode.EXPANDED);
    private final Option<Boolean> autoPlay = new Option<Boolean>("Auto Play", "AutoPlay", false);
    private final Option<Boolean> petals = new Option<Boolean>("Petals", "Petals", true);
    private final Numbers<Double> xPosition = new Numbers<Double>("X", "X", 30.0, 0.0, 2000.0, 1.0);
    private final Numbers<Double> yPosition = new Numbers<Double>("Y", "Y", 86.0, 0.0, 1200.0, 1.0);
    private final Numbers<Double> scale = new Numbers<Double>("Scale", "Scale", 1.0, 0.65, 1.6, 0.05);

    private final NeteaseMusicApi api = new NeteaseMusicApi();
    private final Mp3MusicPlayer player = new Mp3MusicPlayer();
    private final CoverCache covers = new CoverCache(api);
    private final List<NeteaseMusicApi.Song> searchResults = new ArrayList<NeteaseMusicApi.Song>();
    private final List<NeteaseMusicApi.Playlist> playlists = new ArrayList<NeteaseMusicApi.Playlist>();
    private final List<NeteaseMusicApi.Song> playlistSongs = new ArrayList<NeteaseMusicApi.Song>();

    private NeteaseMusicApi.Song currentSong;
    private NeteaseMusicApi.Song previousSong;
    private Lyrics lyrics = Lyrics.EMPTY;
    private NeteaseMusicApi.UserProfile profile = new NeteaseMusicApi.UserProfile(0L, "", "");
    private String status = "Ready";
    private String currentUrl = "";
    private String qrKey = "";
    private String qrImage = "";
    private String qrUrl = "";
    private String lastKeyword = "sakura";
    private int lastQrCode;
    private long qrLastPoll;
    private long lastClick;
    private float openAnimation;
    private float lyricAnimation;
    private float modeAnimation = 1.0f;
    private float songSwapAnimation = 1.0f;
    private DisplayMode renderedMode = DisplayMode.EXPANDED;
    private DisplayMode previousMode = DisplayMode.EXPANDED;

    public MusicPlayer() {
        super("MusicPlayer", Keyboard.KEY_M, ModuleType.Render, "NetEase QR music player with sakura lyric HUD");
        Chinese = "音乐播放器";
        this.addValues(displayMode, autoPlay, petals, xPosition, yPosition, scale);
    }

    @Override
    public void toggle() {
        if (mc.currentScreen != null && !(mc.currentScreen instanceof MusicPlayerScreen)) {
            super.toggle();
            return;
        }
        if (!getState()) {
            setState(true);
        }
        if (isInGame()) {
            openLibraryScreen();
        }
    }

    @Override
    public void enable() {
        loadCookie();
        if (currentSong == null) {
            search(lastKeyword, false);
        }
        if (profile.id == 0L) {
            startQrLogin();
        } else {
            loadPlaylists();
        }
        openAnimation = 0.0f;
    }

    @Override
    public void disable() {
        player.stop();
        openAnimation = 0.0f;
        modeAnimation = 1.0f;
        if (mc.currentScreen instanceof MusicPlayerScreen) {
            mc.displayGuiScreen(null);
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        renderOverlay();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRender(RenderGameOverlayEvent.Text event) {
        if (!YozakuraEventBridge.hasRenderedOverlayThisFrame()) {
            renderOverlay();
        }
    }

    private void renderOverlay() {
        if (!isInGame() || mc.currentScreen instanceof GuiMainMenu) {
            return;
        }
        if (mc.currentScreen instanceof MusicPlayerScreen) {
            pollQrLogin();
            return;
        }
        pollQrLogin();
        updateHudAnimations();

        ScaledResolution sr = new ScaledResolution(mc);
        float uiScale = Math.max(0.65f, scale.getValue().floatValue());
        Layout layout = layout(renderedMode);
        float defaultX = Math.min(xPosition.getValue().floatValue(), sr.getScaledWidth() - layout.width * uiScale - 4.0f);
        float defaultY = Math.min(yPosition.getValue().floatValue(), sr.getScaledHeight() - layout.height * uiScale - 4.0f);
        float[] pos = HudDrag.update("music_player", xPosition, yPosition, scale,
                defaultX, defaultY, layout.width * uiScale, layout.height * uiScale, sr);

        GlStateManager.pushMatrix();
        GlStateManager.translate(pos[0], pos[1], 0.0f);
        GlStateManager.scale(uiScale, uiScale, 1.0f);
        try {
            if (modeAnimation < 0.985f && previousMode != renderedMode) {
                float oldAlpha = openAnimation * (1.0f - ease(modeAnimation));
                GlStateManager.pushMatrix();
                GlStateManager.translate(-10.0f * modeAnimation, 0.0f, 0.0f);
                drawMode(previousMode, layout(previousMode), oldAlpha);
                GlStateManager.popMatrix();
            }
            float newAlpha = openAnimation * ease(modeAnimation);
            GlStateManager.pushMatrix();
            GlStateManager.translate(12.0f * (1.0f - modeAnimation), 0.0f, 0.0f);
            drawMode(renderedMode, layout, newAlpha);
            GlStateManager.popMatrix();
        } finally {
            GlStateManager.popMatrix();
        }
        HudDrag.drawHint("music_player", pos[0], pos[1], layout.width * uiScale, layout.height * uiScale, 9.0f * uiScale);
        HudDrag.handleScroll("music_player", scale, pos[0], pos[1], layout.width * uiScale, layout.height * uiScale, 0.65f, 1.6f);
        handleHudClicks(pos[0], pos[1], layout.width * uiScale, layout.height * uiScale, sr);
    }

    private void updateHudAnimations() {
        DisplayMode mode = displayMode.getValue();
        if (mode != renderedMode) {
            previousMode = renderedMode;
            renderedMode = mode;
            modeAnimation = 0.0f;
        }
        openAnimation = approach(openAnimation, 1.0f, 0.16f);
        modeAnimation = approach(modeAnimation, 1.0f, 0.18f);
        songSwapAnimation = approach(songSwapAnimation, 1.0f, 0.17f);
        lyricAnimation += 0.08f;
    }

    private void drawMode(DisplayMode mode, Layout layout, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        if (mode == DisplayMode.MINI) {
            drawMini(layout.width, layout.height, alpha);
        } else if (mode == DisplayMode.LYRICS) {
            drawLyrics(layout.width, layout.height, alpha);
        } else {
            drawExpanded(layout.width, layout.height, alpha);
        }
    }

    private void drawExpanded(float w, float h, float alpha) {
        drawPanel(0.0f, 0.0f, w, h, 11.0f, alpha);
        if (Boolean.TRUE.equals(petals.getValue())) {
            drawPetals(0.0f, 0.0f, w, h, 0.7f * alpha);
        }
        drawCoverAnimated(24.0f, 24.0f, 112.0f, true, alpha);
        drawVinyl(141.0f, 82.0f, 61.0f, alpha);
        String name = currentSong == null ? "Scan QR or search" : currentSong.name;
        String artist = currentSong == null ? "NetEase Cloud Music" : currentSong.artist;
        float textShift = 12.0f * (1.0f - ease(songSwapAnimation));
        FontLoaders.C22.drawString(trim(name, FontLoaders.C22, 168.0f), 189.0f + textShift, 34.0f, withAlpha(TEXT, alpha));
        FontLoaders.C16.drawString(trim(artist, FontLoaders.C16, 164.0f), 190.0f + textShift, 58.0f, withAlpha(MUTED, alpha));
        FontLoaders.C12.drawString(trim(currentSong == null ? status : currentSong.album, FontLoaders.C12, 152.0f),
                190.0f + textShift, 76.0f, withAlpha(FAINT, alpha));
        chip(player.isPlaying() ? "playing" : player.isPaused() ? "paused" : "ready",
                191.0f, 99.0f, player.isPlaying() ? PINK : 0x77FFFFFF, alpha);
        drawEqualizer(271.0f, 128.0f, 82.0f, player.isPlaying(), alpha);
        drawProgress(24.0f, 165.0f, w - 48.0f, alpha);
        drawControls(w, 196.0f, alpha);
        drawExpandedFooter(w, h, alpha);
    }

    private void drawLyrics(float w, float h, float alpha) {
        drawPanel(0.0f, 0.0f, w, h, 11.0f, alpha);
        if (Boolean.TRUE.equals(petals.getValue())) {
            drawPetals(0.0f, 0.0f, w, h, alpha);
        }
        drawCoverSong(currentSong, 20.0f, 18.0f, 42.0f, false, alpha, 0.0f);
        FontLoaders.C16.drawString(trim(currentSong == null ? "Music Player" : currentSong.name, FontLoaders.C16, 146.0f),
                72.0f, 23.0f, withAlpha(TEXT, alpha));
        FontLoaders.C12.drawString(currentSong == null ? status : currentSong.artist, 72.0f, 41.0f, withAlpha(MUTED, alpha));
        drawEqualizer(w - 63.0f, 37.0f, 38.0f, player.isPlaying(), alpha);
        List<Lyrics.Line> lines = lyrics.around(player.positionMs(), 3);
        float centerY = h * 0.52f;
        for (int i = 0; i < lines.size(); i++) {
            Lyrics.Line line = lines.get(i);
            boolean active = line == lyrics.current(player.positionMs());
            CFontRenderer font = active ? FontLoaders.C22 : FontLoaders.regular(15);
            int color = active ? TEXT : 0x88FFFFFF;
            float y = centerY + (i - lines.size() / 2.0f) * 25.0f;
            float activePulse = active ? 1.0f + (float) Math.sin(lyricAnimation) * 0.012f : 1.0f;
            String text = trim(line.text, font, w - 42.0f);
            GlStateManager.pushMatrix();
            GlStateManager.translate(w / 2.0f, y + 8.0f, 0.0f);
            GlStateManager.scale(activePulse, activePulse, 1.0f);
            GlStateManager.translate(-w / 2.0f, -y - 8.0f, 0.0f);
            font.drawString(text, (w - font.getStringWidth(text)) / 2.0f, y, withAlpha(color, alpha));
            GlStateManager.popMatrix();
            if (active && line.translation.length() > 0) {
                CFontRenderer translationFont = FontLoaders.regular(13);
                String translation = trim(line.translation, translationFont, w - 50.0f);
                translationFont.drawString(translation, (w - translationFont.getStringWidth(translation)) / 2.0f,
                        y + 19.0f, withAlpha(0xA8FFD1E5, alpha));
            }
        }
        drawProgress(22.0f, h - 31.0f, w - 44.0f, alpha);
    }

    private void drawMini(float w, float h, float alpha) {
        drawPanel(0.0f, 0.0f, w, h, 9.0f, alpha);
        drawCoverAnimated(15.0f, 12.0f, 42.0f, false, alpha);
        String title = currentSong == null ? "Music Player" : currentSong.name;
        CFontRenderer titleFont = FontLoaders.regular(15);
        titleFont.drawString(trim(title, titleFont, 112.0f), 70.0f, 15.0f, withAlpha(TEXT, alpha));
        FontLoaders.C12.drawString(currentSong == null ? status : currentSong.artist, 70.0f, 33.0f, withAlpha(MUTED, alpha));
        drawEqualizer(176.0f, 30.0f, 54.0f, player.isPlaying(), alpha);
        FontLoaders.C12.drawString(timeText(player.positionMs()) + " / " + durationText(), 237.0f, 28.0f, withAlpha(0xB8FFFFFF, alpha));
        drawRoundButton(w - 67.0f, 31.0f, 20.0f, player.isPlaying() ? "II" : ">", 0.82f * alpha);
        drawRoundButton(w - 31.0f, 31.0f, 18.0f, "≡", 0.62f * alpha);
    }

    private void drawExpandedFooter(float w, float h, float alpha) {
        drawPill("Lyrics", 24.0f, h - 39.0f, 72.0f, displayMode.getValue() == DisplayMode.LYRICS, alpha);
        drawPill("Mini", 106.0f, h - 39.0f, 60.0f, displayMode.getValue() == DisplayMode.MINI, alpha);
        FontLoaders.C14.drawString("Vol", 196.0f, h - 31.0f, withAlpha(MUTED, alpha));
        RenderUtil.drawRoundedRect(230.0f, h - 25.0f, 306.0f, h - 22.0f, 1.5f, withAlpha(0x33FFFFFF, alpha));
        RenderUtil.drawRoundedRect(230.0f, h - 25.0f, 285.0f, h - 22.0f, 1.5f, withAlpha(PINK_SOFT, alpha));
        drawPill(profile.id == 0L ? "QR" : "User", w - 116.0f, h - 39.0f, 58.0f, profile.id != 0L, alpha);
        drawPill("Library", w - 52.0f, h - 39.0f, 52.0f, false, alpha);
        if (qrImage.length() > 0 && profile.id == 0L) {
            ResourceLocation qr = covers.texture(qrImage);
            RenderUtil.drawRoundedRect(w - 88.0f, 22.0f, w - 18.0f, 92.0f, 7.0f, withAlpha(0x33000000, alpha));
            if (qr != null) {
                RenderUtil.drawImage(qr, (int) (w - 84.0f), 26, 62.0f, 62.0f, alpha);
            }
            FontLoaders.C12.drawString("QR Login", w - 84.0f, 96.0f, withAlpha(0xCFFFFFFF, alpha));
        } else if (profile.id != 0L) {
            FontLoaders.C12.drawString(trim(profile.nickname, FontLoaders.C12, 82.0f), w - 112.0f, 24.0f, withAlpha(0xDFFFFFFF, alpha));
        }
    }

    private void drawPanel(float x, float y, float w, float h, float radius, float alpha) {
        RenderUtil.drawGlowAround(x + 6.0f, y + 6.0f, x + w - 6.0f, y + h - 4.0f, radius,
                withAlpha(0x70FF8EC5, alpha), 1.0f);
        RenderServices.panels().panel(x, y, x + w, y + h, radius, 1.0f, withAlpha(GLASS, alpha), withAlpha(BORDER, alpha));
        RenderUtil.drawRoundedGradientRect(x + 1.0f, y + 1.0f, x + w - 1.0f, y + h - 1.0f, radius,
                withAlpha(0x28FFB7DB, alpha), withAlpha(0x06000000, alpha),
                withAlpha(0x18181533, alpha), withAlpha(0x1024162F, alpha));
    }

    private void drawCoverAnimated(float x, float y, float size, boolean shadow, float alpha) {
        float swap = ease(songSwapAnimation);
        if (previousSong != null && songSwapAnimation < 0.98f) {
            drawCoverSong(previousSong, x - 12.0f * swap, y, size, shadow, alpha * (1.0f - swap), 0.0f);
        }
        drawCoverSong(currentSong, x + 14.0f * (1.0f - swap), y, size, shadow, alpha * swap, 0.0f);
    }

    private void drawCoverSong(NeteaseMusicApi.Song song, float x, float y, float size, boolean shadow, float alpha, float rotate) {
        ResourceLocation cover = song == null ? null : covers.texture(song.coverUrl);
        if (shadow) {
            RenderUtil.drawGlowAround(x + 5.0f, y + 5.0f, x + size - 5.0f, y + size - 3.0f, 7.0f,
                    withAlpha(0x66FF8EC5, alpha), 0.9f);
        }
        RenderUtil.drawRoundedRect(x, y, x + size, y + size, 7.0f, withAlpha(0x4428183A, alpha));
        if (cover != null) {
            if (rotate != 0.0f) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(x + size / 2.0f, y + size / 2.0f, 0.0f);
                GlStateManager.rotate(rotate, 0.0f, 0.0f, 1.0f);
                GlStateManager.translate(-x - size / 2.0f, -y - size / 2.0f, 0.0f);
                RenderUtil.drawImage(cover, (int) x, (int) y, size, size, alpha);
                GlStateManager.popMatrix();
            } else {
                RenderUtil.drawImage(cover, (int) x, (int) y, size, size, alpha);
            }
        } else {
            RenderUtil.drawRoundedGradientRect(x, y, x + size, y + size, 7.0f,
                    withAlpha(0xFF6D4CA0, alpha), withAlpha(0xFF21112F, alpha),
                    withAlpha(0xFFFF8EC5, alpha), withAlpha(0xFF302255, alpha));
            FontLoaders.C30.drawString("✿", x + size * 0.35f, y + size * 0.33f, withAlpha(0xDDFFD1E5, alpha));
        }
    }

    private void drawVinyl(float x, float y, float radius, float alpha) {
        RenderServices.shapes().circle(x, y, 0, 360, radius, withAlpha(DARK, alpha));
        RenderServices.shapes().circle(x, y, 0, 360, radius * 0.36f, withAlpha(0xFF2A1838, alpha));
        ResourceLocation cover = currentSong == null ? null : covers.texture(currentSong.coverUrl);
        if (cover != null) {
            float rotation = player.isPlaying() ? (player.positionMs() % 8000L) / 8000.0f * 360.0f : 0.0f;
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0.0f);
            GlStateManager.rotate(rotation, 0.0f, 0.0f, 1.0f);
            GlStateManager.translate(-x, -y, 0.0f);
            RenderUtil.drawCircleWithTexture(x, y, 0, 360, radius * 0.34f, cover, withAlpha(0xFFFFFFFF, alpha));
            GlStateManager.popMatrix();
        }
    }

    private void drawProgress(float x, float y, float width, float alpha) {
        float progress = progress();
        RenderUtil.drawRoundedRect(x, y, x + width, y + 4.0f, 2.0f, withAlpha(0x33FFFFFF, alpha));
        RenderUtil.drawRoundedRect(x, y, x + width * progress, y + 4.0f, 2.0f, withAlpha(PINK, alpha));
        RenderServices.shapes().circle(x + width * progress, y + 2.0f, 0, 360, 4.2f, withAlpha(0xFFFFB7DB, alpha));
        FontLoaders.C12.drawString(timeText(player.positionMs()), x, y + 11.0f, withAlpha(MUTED, alpha));
        String duration = durationText();
        FontLoaders.C12.drawString(duration, x + width - FontLoaders.C12.getStringWidth(duration), y + 11.0f, withAlpha(MUTED, alpha));
    }

    private void drawControls(float w, float y, float alpha) {
        FontLoaders.C22.drawString("R", 45.0f, y, withAlpha(MUTED, alpha));
        FontLoaders.C30.drawString("<", 112.0f, y - 7.0f, withAlpha(TEXT, alpha));
        drawRoundButton(w * 0.5f, y + 9.0f, 28.0f, player.isPlaying() ? "II" : ">", alpha);
        FontLoaders.C30.drawString(">", w - 122.0f, y - 7.0f, withAlpha(TEXT, alpha));
        FontLoaders.C22.drawString("≡", w - 60.0f, y, withAlpha(MUTED, alpha));
    }

    private void drawRoundButton(float cx, float cy, float radius, String text, float alpha) {
        RenderUtil.drawGlowAround(cx - radius, cy - radius, cx + radius, cy + radius, radius,
                withAlpha(0x80FF8EC5, alpha), 0.85f);
        RenderServices.shapes().circle(cx, cy, 0, 360, radius, withAlpha(0x55241535, alpha));
        RenderServices.shapes().circle(cx, cy, 0, 360, radius - 1.0f, withAlpha(0x2EFFB7DB, alpha));
        CFontRenderer font = text.length() > 1 ? FontLoaders.C18 : FontLoaders.C22;
        font.drawString(text, cx - font.getStringWidth(text) / 2.0f,
                cy - font.getHeight() / 2.0f + 2.0f, withAlpha(TEXT, alpha));
    }

    private void drawEqualizer(float x, float y, float width, boolean active, float alpha) {
        int bars = 18;
        for (int i = 0; i < bars; i++) {
            float phase = System.currentTimeMillis() * 0.004f + i * 0.72f;
            float h = active ? 4.0f + (float) Math.sin(phase) * 5.0f + 7.0f : 4.0f + (i % 5);
            float bx = x + i * width / bars;
            RenderUtil.drawRoundedRect(bx, y - Math.abs(h), bx + 1.5f, y, 1.0f, withAlpha(0xD6FF8EC5, alpha));
        }
    }

    private void drawPetals(float x, float y, float w, float h, float intensity) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 16; i++) {
            float px = x + ((now * 0.018f + i * 37.0f) % (w + 34.0f)) - 18.0f;
            float py = y + ((now * 0.010f + i * 29.0f) % (h + 28.0f)) - 14.0f;
            float s = 1.8f + (i % 4) * 0.45f;
            FontLoaders.C14.drawString("✿", px, py, withAlpha(0x99FFB7DB, intensity * (0.45f + (i % 3) * 0.12f)));
            RenderServices.shapes().circle(px + s, py + s, 0, 360, s * 0.35f, withAlpha(0x66FF8EC5, intensity));
        }
    }

    private void chip(String text, float x, float y, int color, float alpha) {
        float w = FontLoaders.C12.getStringWidth(text) + 18.0f;
        RenderUtil.drawRoundedRect(x, y, x + w, y + 18.0f, 5.0f, withAlpha(0x4429183A, alpha));
        FontLoaders.C12.drawString(text, x + 8.0f, y + 4.5f, withAlpha(color, alpha));
    }

    private void drawPill(String text, float x, float y, float w, boolean active, float alpha) {
        int fill = active ? 0x55301546 : 0x2AFFFFFF;
        int border = active ? 0x88FF8EC5 : 0x22FFFFFF;
        RenderServices.shapes().roundedBorder(x, y, x + w, y + 24.0f, 7.0f, 0.8f,
                withAlpha(fill, alpha), withAlpha(border, alpha));
        FontLoaders.C14.drawString(text, x + (w - FontLoaders.C14.getStringWidth(text)) / 2.0f,
                y + 6.0f, withAlpha(active ? PINK : MUTED, alpha));
    }

    private void handleHudClicks(float x, float y, float w, float h, ScaledResolution sr) {
        if (!HudDrag.isEditMode() || !Mouse.isButtonDown(0)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastClick < 220L) {
            return;
        }
        int mx = HudDrag.mouseX(sr);
        int my = HudDrag.mouseY(sr);
        if (mx < x || mx > x + w || my < y || my > y + h) {
            return;
        }
        lastClick = now;
        float s = scale.getValue().floatValue();
        if (displayMode.getValue() == DisplayMode.MINI) {
            if (mx > x + w - 78.0f * s) {
                openLibraryScreen();
            } else {
                displayMode.setValue(DisplayMode.EXPANDED);
            }
            return;
        }
        if (my > y + h - 50.0f * s) {
            if (mx < x + 96.0f * s) {
                displayMode.setValue(DisplayMode.LYRICS);
            } else if (mx < x + 174.0f * s) {
                displayMode.setValue(DisplayMode.MINI);
            } else if (mx > x + w - 128.0f * s) {
                openLibraryScreen();
            } else {
                togglePlayback();
            }
        } else if (mx > x + w * 0.42f && mx < x + w * 0.58f && my > y + h * 0.56f && my < y + h * 0.82f) {
            togglePlayback();
        }
    }

    private void openLibraryScreen() {
        if (isInGame()) {
            mc.displayGuiScreen(new MusicPlayerScreen(this));
        }
    }

    private void search(final String keyword, final boolean play) {
        final String query = keyword == null || keyword.trim().length() == 0 ? "sakura" : keyword.trim();
        lastKeyword = query;
        status = "Searching " + query;
        async(new Runnable() {
            @Override
            public void run() {
                try {
                    List<NeteaseMusicApi.Song> songs = api.searchSongs(query);
                    synchronized (searchResults) {
                        searchResults.clear();
                        searchResults.addAll(songs);
                    }
                    if (!songs.isEmpty() && currentSong == null) {
                        selectSong(songs.get(0), play || Boolean.TRUE.equals(autoPlay.getValue()));
                    }
                    status = songs.isEmpty() ? "No results" : "Found " + songs.size() + " songs";
                } catch (Throwable throwable) {
                    status = "Search failed";
                }
            }
        }, "Yozakura Music Search");
    }

    private void selectSong(final NeteaseMusicApi.Song song, final boolean play) {
        if (song == null) {
            return;
        }
        if (currentSong == null || currentSong.id != song.id) {
            previousSong = currentSong;
            songSwapAnimation = 0.0f;
        }
        currentSong = song;
        lyrics = Lyrics.EMPTY;
        status = "Loading " + song.name;
        async(new Runnable() {
            @Override
            public void run() {
                try {
                    currentUrl = api.songUrl(song.id);
                    lyrics = api.lyrics(song.id);
                    if (play && currentUrl != null && currentUrl.length() > 0) {
                        player.play(currentUrl, 0L);
                    }
                    status = currentUrl == null || currentUrl.length() == 0 ? "Song url unavailable" : "Ready";
                } catch (Throwable throwable) {
                    status = "Load failed";
                }
            }
        }, "Yozakura Music Load");
    }

    private void togglePlayback() {
        if (currentSong == null) {
            search(lastKeyword, true);
            return;
        }
        if (player.isPlaying()) {
            player.pause();
        } else if (currentUrl != null && currentUrl.length() > 0) {
            player.resume(currentUrl);
        } else {
            selectSong(currentSong, true);
        }
    }

    private void playNext() {
        List<NeteaseMusicApi.Song> source = preferredSongList();
        if (source.isEmpty()) {
            return;
        }
        int index = indexOf(source, currentSong);
        selectSong(source.get((index + 1 + source.size()) % source.size()), true);
    }

    private void playPrevious() {
        List<NeteaseMusicApi.Song> source = preferredSongList();
        if (source.isEmpty()) {
            return;
        }
        int index = indexOf(source, currentSong);
        selectSong(source.get((index - 1 + source.size()) % source.size()), true);
    }

    private List<NeteaseMusicApi.Song> preferredSongList() {
        List<NeteaseMusicApi.Song> songs = playlistSongSnapshot();
        return songs.isEmpty() ? searchSnapshot() : songs;
    }

    private int indexOf(List<NeteaseMusicApi.Song> songs, NeteaseMusicApi.Song song) {
        if (song == null) {
            return 0;
        }
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).id == song.id) {
                return i;
            }
        }
        return 0;
    }

    private void startQrLogin() {
        status = "Loading QR";
        async(new Runnable() {
            @Override
            public void run() {
                try {
                    qrKey = api.qrKey();
                    qrUrl = "https://music.163.com/login?codekey=" + qrKey;
                    qrImage = QrImageFactory.dataUri(qrUrl, 220);
                    status = "Scan QR to login";
                    qrLastPoll = 0L;
                } catch (Throwable throwable) {
                    qrKey = "";
                    qrImage = "";
                    qrUrl = "";
                    status = "Retry QR: " + throwable.getClass().getSimpleName();
                    logMusicFailure("QR login failed", throwable);
                }
            }
        }, "Yozakura Music QR");
    }

    private void pollQrLogin() {
        if (profile.id != 0L || qrKey.length() == 0 || System.currentTimeMillis() - qrLastPoll < 2500L) {
            return;
        }
        qrLastPoll = System.currentTimeMillis();
        async(new Runnable() {
            @Override
            public void run() {
                try {
                    NeteaseMusicApi.QrStatus state = api.qrStatus(qrKey);
                    lastQrCode = state.code;
                    status = qrStatusText(state);
                    if (state.code == 803) {
                        status = "Login success";
                        saveCookie(api.getCookie());
                        qrKey = "";
                        qrImage = "";
                        qrUrl = "";
                        try {
                            profile = api.account();
                            saveCookie(api.getCookie());
                            status = profile.nickname.length() == 0 ? "Login success" : "Logged in as " + profile.nickname;
                            loadPlaylists();
                        } catch (Throwable throwable) {
                            status = "Login success, account load failed";
                            logMusicFailure("NetEase account load failed after QR login", throwable);
                        }
                    } else if (state.code == 800) {
                        status = "QR expired";
                        startQrLogin();
                    }
                } catch (Throwable throwable) {
                    status = "QR check failed: " + throwable.getClass().getSimpleName();
                    logMusicFailure("QR poll failed", throwable);
                }
            }
        }, "Yozakura Music QR Poll");
    }

    private String qrStatusText(NeteaseMusicApi.QrStatus state) {
        if (state.code == 800) {
            return "QR expired";
        }
        if (state.code == 801) {
            return "Waiting for scan";
        }
        if (state.code == 802) {
            return "Confirm on phone";
        }
        if (state.code == 803) {
            return "Login success";
        }
        if (state.message.length() > 0) {
            return state.message;
        }
        return "QR status " + state.code;
    }

    private void loadPlaylists() {
        if (profile.id == 0L) {
            status = "Please scan QR first";
            startQrLogin();
            return;
        }
        async(new Runnable() {
            @Override
            public void run() {
                try {
                    List<NeteaseMusicApi.Playlist> list = api.userPlaylists(profile.id);
                    synchronized (playlists) {
                        playlists.clear();
                        playlists.addAll(list);
                    }
                    status = list.isEmpty() ? "No playlists" : "Loaded playlists";
                } catch (Throwable throwable) {
                    status = "Playlist failed";
                }
            }
        }, "Yozakura Music Playlists");
    }

    private void loadPlaylist(final long id, final boolean playFirst) {
        async(new Runnable() {
            @Override
            public void run() {
                try {
                    List<NeteaseMusicApi.Song> songs = api.playlistSongs(id);
                    synchronized (playlistSongs) {
                        playlistSongs.clear();
                        playlistSongs.addAll(songs);
                    }
                    if (playFirst && !songs.isEmpty()) {
                        selectSong(songs.get(0), Boolean.TRUE.equals(autoPlay.getValue()));
                    }
                    status = songs.isEmpty() ? "Playlist is empty" : "Loaded " + songs.size() + " songs";
                } catch (Throwable throwable) {
                    status = "Playlist songs failed";
                }
            }
        }, "Yozakura Music Playlist Songs");
    }

    private void loadCookie() {
        File file = cookieFile();
        if (!file.exists()) {
            return;
        }
        try {
            FileInputStream input = new FileInputStream(file);
            try {
                byte[] data = new byte[(int) file.length()];
                int read = input.read(data);
                if (read > 0) {
                    api.setCookie(new String(data, 0, read, StandardCharsets.UTF_8));
                    profile = api.account();
                }
            } finally {
                input.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private void saveCookie(String cookie) {
        if (cookie == null || cookie.length() == 0) {
            return;
        }
        try {
            File file = cookieFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            FileOutputStream output = new FileOutputStream(file);
            try {
                output.write(cookie.getBytes(StandardCharsets.UTF_8));
            } finally {
                output.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private File cookieFile() {
        return new File(mc.mcDataDir, "yozakura/musicplayer.cookie");
    }

    private List<NeteaseMusicApi.Song> searchSnapshot() {
        synchronized (searchResults) {
            return new ArrayList<NeteaseMusicApi.Song>(searchResults);
        }
    }

    private List<NeteaseMusicApi.Playlist> playlistSnapshot() {
        synchronized (playlists) {
            return new ArrayList<NeteaseMusicApi.Playlist>(playlists);
        }
    }

    private List<NeteaseMusicApi.Song> playlistSongSnapshot() {
        synchronized (playlistSongs) {
            return new ArrayList<NeteaseMusicApi.Song>(playlistSongs);
        }
    }

    private float progress() {
        long duration = durationMs();
        return duration <= 0L ? 0.0f : Math.max(0.0f, Math.min(1.0f, player.positionMs() / (float) duration));
    }

    private long durationMs() {
        return currentSong == null ? 240000L : Math.max(30000L, currentSong.durationMs);
    }

    private String durationText() {
        return timeText(durationMs());
    }

    private String timeText(long ms) {
        long seconds = Math.max(0L, ms / 1000L);
        return seconds / 60L + ":" + (seconds % 60L < 10L ? "0" : "") + seconds % 60L;
    }

    private Layout layout(DisplayMode mode) {
        if (mode == DisplayMode.MINI) {
            return new Layout(392.0f, 67.0f);
        }
        if (mode == DisplayMode.LYRICS) {
            return new Layout(244.0f, 334.0f);
        }
        return new Layout(440.0f, 326.0f);
    }

    private String trim(String text, CFontRenderer font, float width) {
        if (text == null) {
            return "";
        }
        if (font.getStringWidth(text) <= width) {
            return text;
        }
        String ellipsis = "...";
        String next = text;
        while (next.length() > 0 && font.getStringWidth(next + ellipsis) > width) {
            next = next.substring(0, next.length() - 1);
        }
        return next + ellipsis;
    }

    private static float approach(float current, float target, float speed) {
        return current + (target - current) * Math.max(0.0f, Math.min(1.0f, speed));
    }

    private static float ease(float value) {
        float t = Math.max(0.0f, Math.min(1.0f, value));
        return 1.0f - (float) Math.pow(1.0f - t, 3.0);
    }

    private int withAlpha(int color, float alpha) {
        return withAlpha(color, alpha, 1.0f);
    }

    private int withAlpha(int color, float alpha, float multiplier) {
        return (color & 0x00FFFFFF)
                | (Math.max(0, Math.min(255, Math.round(((color >>> 24) & 255) * alpha * multiplier))) << 24);
    }

    private void async(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        thread.start();
    }

    private void logMusicFailure(String message, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraMusicPlayer.log");
            PrintWriter writer = new PrintWriter(new FileWriter(log, true));
            try {
                writer.println(message);
                throwable.printStackTrace(writer);
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private static final class Layout {
        final float width;
        final float height;

        Layout(float width, float height) {
            this.width = width;
            this.height = height;
        }
    }

    private static final class MusicPlayerScreen extends GuiScreen {
        private final MusicPlayer module;
        private LibraryTab tab = LibraryTab.SEARCH;
        private LibraryTab previousTab = LibraryTab.SEARCH;
        private String searchText;
        private boolean searchFocused = true;
        private boolean closing;
        private float openAnimation;
        private float tabAnimation = 1.0f;
        private float listAnimation = 1.0f;
        private int searchScroll;
        private int playlistScroll;
        private int songScroll;
        private long selectedPlaylist;
        private long lastAction;

        MusicPlayerScreen(MusicPlayer module) {
            this.module = module;
            this.searchText = module.lastKeyword == null ? "sakura" : module.lastKeyword;
        }

        @Override
        public void initGui() {
            Keyboard.enableRepeatEvents(true);
            openAnimation = 0.0f;
            closing = false;
            tabAnimation = 1.0f;
            listAnimation = 1.0f;
            if (module.profile.id == 0L && module.qrImage.length() == 0) {
                module.startQrLogin();
            }
            super.initGui();
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            module.pollQrLogin();
            updateAnimations();
            ScaledResolution sr = new ScaledResolution(mc);
            float alpha = ease(openAnimation);
            float scale = 0.94f + alpha * 0.06f;
            float w = Math.min(700.0f, sr.getScaledWidth() - 36.0f);
            float h = Math.min(380.0f, sr.getScaledHeight() - 36.0f);
            float x = (sr.getScaledWidth() - w) / 2.0f;
            float y = (sr.getScaledHeight() - h) / 2.0f;

            drawBackdrop(sr, alpha);
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + w / 2.0f, y + h / 2.0f, 0.0f);
            GlStateManager.scale(scale, scale, 1.0f);
            GlStateManager.translate(-x - w / 2.0f, -y - h / 2.0f, 0.0f);
            drawShell(x, y, w, h, alpha);
            drawNowPlaying(x, y, 220.0f, h, alpha);
            drawContent(x + 238.0f, y + 20.0f, w - 260.0f, h - 40.0f, mouseX, mouseY, alpha);
            GlStateManager.popMatrix();
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        private void updateAnimations() {
            openAnimation = approach(openAnimation, closing ? 0.0f : 1.0f, closing ? 0.22f : 0.18f);
            tabAnimation = approach(tabAnimation, 1.0f, 0.20f);
            listAnimation = approach(listAnimation, 1.0f, 0.18f);
            if (closing && openAnimation < 0.035f) {
                mc.displayGuiScreen(null);
            }
        }

        private void drawBackdrop(ScaledResolution sr, float alpha) {
            RenderUtil.drawRoundedRect(0.0f, 0.0f, sr.getScaledWidth(), sr.getScaledHeight(), 0.0f,
                    module.withAlpha(0x86070A16, alpha));
            if (Boolean.TRUE.equals(module.petals.getValue())) {
                module.drawPetals(0.0f, 0.0f, sr.getScaledWidth(), sr.getScaledHeight(), 0.42f * alpha);
            }
        }

        private void drawShell(float x, float y, float w, float h, float alpha) {
            RenderUtil.drawGlowAround(x + 8.0f, y + 8.0f, x + w - 8.0f, y + h - 6.0f, 16.0f,
                    module.withAlpha(0x82FF8EC5, alpha), 1.0f);
            RenderServices.panels().panel(x, y, x + w, y + h, 16.0f, 1.0f,
                    module.withAlpha(0xD30D0B1D, alpha), module.withAlpha(0x68FFC1DF, alpha));
            RenderUtil.drawRoundedGradientRect(x + 1.0f, y + 1.0f, x + w - 1.0f, y + h - 1.0f, 16.0f,
                    module.withAlpha(0x20FFB7DB, alpha), module.withAlpha(0x06000000, alpha),
                    module.withAlpha(0x1F261646, alpha), module.withAlpha(0x12211333, alpha));
            RenderUtil.drawRoundedRect(x + 220.0f, y + 18.0f, x + 221.0f, y + h - 18.0f, 0.5f,
                    module.withAlpha(0x26FFFFFF, alpha));
            drawCloseButton(x + w - 34.0f, y + 24.0f, alpha);
        }

        private void drawCloseButton(float cx, float cy, float alpha) {
            RenderServices.shapes().circle(cx, cy, 0, 360, 10.0f, module.withAlpha(0x32FFFFFF, alpha));
            FontLoaders.C16.drawString("x", cx - FontLoaders.C16.getStringWidth("x") / 2.0f,
                    cy - 6.0f, module.withAlpha(MUTED, alpha));
        }

        private void drawNowPlaying(float x, float y, float w, float h, float alpha) {
            FontLoaders.C22.drawString("yozakura music", x + 26.0f, y + 25.0f, module.withAlpha(TEXT, alpha));
            FontLoaders.C12.drawString(module.profile.id == 0L ? "QR login only" : module.profile.nickname,
                    x + 28.0f, y + 49.0f, module.withAlpha(MUTED, alpha));
            module.drawCoverAnimated(x + 28.0f, y + 74.0f, 142.0f, true, alpha);
            if (module.currentSong != null) {
                FontLoaders.C20.drawString(module.trim(module.currentSong.name, FontLoaders.C20, 190.0f),
                        x + 27.0f, y + 236.0f, module.withAlpha(TEXT, alpha));
                FontLoaders.C14.drawString(module.trim(module.currentSong.artist, FontLoaders.C14, 190.0f),
                        x + 27.0f, y + 259.0f, module.withAlpha(MUTED, alpha));
                FontLoaders.C12.drawString(module.trim(module.currentSong.album, FontLoaders.C12, 190.0f),
                        x + 27.0f, y + 278.0f, module.withAlpha(FAINT, alpha));
            } else {
                FontLoaders.C20.drawString("No song selected", x + 27.0f, y + 236.0f, module.withAlpha(TEXT, alpha));
                FontLoaders.C14.drawString(module.status, x + 27.0f, y + 259.0f, module.withAlpha(MUTED, alpha));
            }
            module.drawProgress(x + 27.0f, y + h - 78.0f, 164.0f, alpha);
            drawPlayerControls(x + 30.0f, y + h - 40.0f, alpha);
        }

        private void drawPlayerControls(float x, float y, float alpha) {
            drawActionButton(x, y, 38.0f, 30.0f, "Prev", alpha);
            drawActionButton(x + 47.0f, y - 4.0f, 46.0f, 38.0f, module.player.isPlaying() ? "Pause" : "Play", alpha);
            drawActionButton(x + 102.0f, y, 38.0f, 30.0f, "Next", alpha);
            drawActionButton(x + 149.0f, y, 38.0f, 30.0f, module.displayMode.getValue() == DisplayMode.MINI ? "Max" : "Min", alpha);
        }

        private void drawActionButton(float x, float y, float w, float h, String label, float alpha) {
            RenderUtil.drawGlowAround(x, y, x + w, y + h, h / 2.0f, module.withAlpha(0x60FF8EC5, alpha), 0.72f);
            RenderServices.shapes().roundedBorder(x, y, x + w, y + h, h / 2.0f, 0.8f,
                    module.withAlpha(0x38251436, alpha), module.withAlpha(0x60FF8EC5, alpha));
            CFontRenderer font = FontLoaders.C12;
            font.drawString(label, x + (w - font.getStringWidth(label)) / 2.0f,
                    y + (h - font.getHeight()) / 2.0f + 2.0f, module.withAlpha(TEXT, alpha));
        }

        private void drawContent(float x, float y, float w, float h, int mouseX, int mouseY, float alpha) {
            drawTabs(x, y, w, alpha);
            float contentY = y + 47.0f;
            float eased = ease(tabAnimation);
            GlStateManager.pushMatrix();
            GlStateManager.translate(18.0f * (1.0f - eased), 0.0f, 0.0f);
            if (tab == LibraryTab.SEARCH) {
                drawSearchTab(x, contentY, w, h - 47.0f, mouseX, mouseY, alpha * eased);
            } else if (tab == LibraryTab.PLAYLISTS) {
                drawPlaylistTab(x, contentY, w, h - 47.0f, mouseX, mouseY, alpha * eased);
            } else {
                drawQrTab(x, contentY, w, h - 47.0f, alpha * eased);
            }
            GlStateManager.popMatrix();
            if (previousTab != tab && tabAnimation > 0.96f) {
                previousTab = tab;
            }
        }

        private void drawTabs(float x, float y, float w, float alpha) {
            drawTab("Search", LibraryTab.SEARCH, x, y, 74.0f, alpha);
            drawTab("Lists", LibraryTab.PLAYLISTS, x + 84.0f, y, 74.0f, alpha);
            drawTab("QR", LibraryTab.QR_LOGIN, x + 168.0f, y, 54.0f, alpha);
            drawPillMode("Full", DisplayMode.EXPANDED, x + w - 170.0f, y, 48.0f, alpha);
            drawPillMode("Lyrics", DisplayMode.LYRICS, x + w - 114.0f, y, 58.0f, alpha);
            drawPillMode("Mini", DisplayMode.MINI, x + w - 48.0f, y, 48.0f, alpha);
        }

        private void drawTab(String text, LibraryTab value, float x, float y, float w, float alpha) {
            boolean active = tab == value;
            RenderServices.shapes().roundedBorder(x, y, x + w, y + 28.0f, 8.0f, 0.8f,
                    module.withAlpha(active ? 0x58301546 : 0x24FFFFFF, alpha),
                    module.withAlpha(active ? 0x88FF8EC5 : 0x20FFFFFF, alpha));
            FontLoaders.C14.drawString(text, x + (w - FontLoaders.C14.getStringWidth(text)) / 2.0f,
                    y + 7.0f, module.withAlpha(active ? PINK : MUTED, alpha));
        }

        private void drawPillMode(String text, DisplayMode value, float x, float y, float w, float alpha) {
            boolean active = module.displayMode.getValue() == value;
            RenderServices.shapes().roundedBorder(x, y, x + w, y + 28.0f, 8.0f, 0.8f,
                    module.withAlpha(active ? 0x44301546 : 0x1DFFFFFF, alpha),
                    module.withAlpha(active ? 0x7EFF8EC5 : 0x18FFFFFF, alpha));
            FontLoaders.C14.drawString(text, x + (w - FontLoaders.C14.getStringWidth(text)) / 2.0f,
                    y + 7.0f, module.withAlpha(active ? PINK : MUTED, alpha));
        }

        private void drawSearchTab(float x, float y, float w, float h, int mouseX, int mouseY, float alpha) {
            drawSearchField(x, y, w - 88.0f, alpha);
            drawCommandButton("Search", x + w - 78.0f, y, 78.0f, 32.0f, alpha);
            FontLoaders.C12.drawString(module.status, x + 4.0f, y + 42.0f, module.withAlpha(MUTED, alpha));
            List<NeteaseMusicApi.Song> songs = module.searchSnapshot();
            drawSongRows(songs, x, y + 62.0f, w, h - 62.0f, searchScroll, alpha);
        }

        private void drawSearchField(float x, float y, float w, float alpha) {
            RenderServices.shapes().roundedBorder(x, y, x + w, y + 32.0f, 10.0f, 0.8f,
                    module.withAlpha(0x37110F20, alpha), module.withAlpha(searchFocused ? 0x92FF8EC5 : 0x34FFFFFF, alpha));
            String shown = searchText.length() == 0 ? "Search songs / artists / albums" : searchText;
            FontLoaders.C14.drawString(module.trim(shown, FontLoaders.C14, w - 38.0f), x + 16.0f, y + 9.0f,
                    module.withAlpha(searchText.length() == 0 ? FAINT : TEXT, alpha));
            if (searchFocused && (System.currentTimeMillis() / 420L) % 2L == 0L) {
                float cursorX = x + 18.0f + FontLoaders.C14.getStringWidth(module.trim(searchText, FontLoaders.C14, w - 42.0f));
                RenderUtil.drawRoundedRect(cursorX, y + 8.0f, cursorX + 1.0f, y + 24.0f, 0.5f,
                        module.withAlpha(PINK, alpha));
            }
        }

        private void drawPlaylistTab(float x, float y, float w, float h, int mouseX, int mouseY, float alpha) {
            if (module.profile.id == 0L) {
                FontLoaders.C20.drawString("QR login required", x + 6.0f, y + 8.0f, module.withAlpha(TEXT, alpha));
                FontLoaders.C14.drawString("NetEase QR login only", x + 6.0f, y + 34.0f, module.withAlpha(MUTED, alpha));
                drawQrCard(x + 6.0f, y + 70.0f, 150.0f, alpha);
                return;
            }
            List<NeteaseMusicApi.Playlist> list = module.playlistSnapshot();
            float leftW = Math.min(190.0f, w * 0.42f);
            FontLoaders.C16.drawString("Playlists", x + 2.0f, y + 4.0f, module.withAlpha(TEXT, alpha));
            FontLoaders.C16.drawString("Songs", x + leftW + 18.0f, y + 4.0f, module.withAlpha(TEXT, alpha));
            drawPlaylistRows(list, x, y + 28.0f, leftW, h - 28.0f, alpha);
            RenderUtil.drawRoundedRect(x + leftW + 8.0f, y + 2.0f, x + leftW + 9.0f, y + h, 0.5f,
                    module.withAlpha(0x22FFFFFF, alpha));
            drawSongRows(module.playlistSongSnapshot(), x + leftW + 18.0f, y + 28.0f,
                    w - leftW - 18.0f, h - 28.0f, songScroll, alpha);
        }

        private void drawQrTab(float x, float y, float w, float h, float alpha) {
            FontLoaders.C20.drawString(module.profile.id == 0L ? "NetEase QR Login" : "Logged in",
                    x + 4.0f, y + 7.0f, module.withAlpha(TEXT, alpha));
            FontLoaders.C14.drawString(module.profile.id == 0L ? "Scan with NetEase Cloud Music" : module.profile.nickname,
                    x + 4.0f, y + 35.0f, module.withAlpha(MUTED, alpha));
            drawQrCard(x + 6.0f, y + 76.0f, 184.0f, alpha);
            float right = x + 226.0f;
            drawInfoCard(right, y + 76.0f, w - 226.0f, 80.0f, "Login", "QR only. No phone or password entry.", alpha);
            drawInfoCard(right, y + 170.0f, w - 226.0f, 80.0f, "Library", "Read playlists and play tracks after login.", alpha);
            FontLoaders.C12.drawString(module.trim(module.status, FontLoaders.C12, w - 238.0f),
                    right + 4.0f, y + 266.0f, module.withAlpha(MUTED, alpha));
        }

        private void drawQrCard(float x, float y, float size, float alpha) {
            RenderUtil.drawGlowAround(x + 8.0f, y + 8.0f, x + size - 8.0f, y + size - 8.0f, 14.0f,
                    module.withAlpha(0x66FF8EC5, alpha), 0.85f);
            RenderServices.shapes().roundedBorder(x, y, x + size, y + size, 14.0f, 0.8f,
                    module.withAlpha(0x3A110F20, alpha), module.withAlpha(0x45FFC1DF, alpha));
            if (module.profile.id != 0L) {
                FontLoaders.C18.drawString("Logged in", x + (size - FontLoaders.C18.getStringWidth("Logged in")) / 2.0f,
                        y + size / 2.0f - 11.0f, module.withAlpha(TEXT, alpha));
                return;
            }
            ResourceLocation qr = module.qrImage.length() == 0 ? null : module.covers.texture(module.qrImage);
            if (qr != null) {
                RenderUtil.drawRoundedRect(x + 13.0f, y + 13.0f, x + size - 13.0f, y + size - 13.0f, 8.0f,
                        module.withAlpha(0xFFF8ECF7, alpha));
                RenderUtil.drawImage(qr, (int) (x + 17.0f), (int) (y + 17.0f), size - 34.0f, size - 34.0f, alpha);
            } else {
                String text = module.status;
                FontLoaders.C16.drawString(text, x + (size - FontLoaders.C16.getStringWidth(text)) / 2.0f,
                        y + size / 2.0f - 10.0f, module.withAlpha(MUTED, alpha));
                FontLoaders.C12.drawString("Click QR tab to retry", x + (size - FontLoaders.C12.getStringWidth("Click QR tab to retry")) / 2.0f,
                        y + size / 2.0f + 12.0f, module.withAlpha(FAINT, alpha));
            }
        }

        private void drawInfoCard(float x, float y, float w, float h, String title, String text, float alpha) {
            RenderServices.shapes().roundedBorder(x, y, x + w, y + h, 10.0f, 0.8f,
                    module.withAlpha(0x25110F20, alpha), module.withAlpha(0x30FFFFFF, alpha));
            FontLoaders.C16.drawString(title, x + 14.0f, y + 13.0f, module.withAlpha(TEXT, alpha));
            CFontRenderer small = FontLoaders.regular(13);
            small.drawString(module.trim(text, small, w - 28.0f), x + 14.0f, y + 42.0f,
                    module.withAlpha(MUTED, alpha));
        }

        private void drawSongRows(List<NeteaseMusicApi.Song> songs, float x, float y, float w, float h, int scroll, float alpha) {
            gq.yozakura.engine.render.GLStateManager.pushScissor(x, y, w, h);
            try {
                if (songs.isEmpty()) {
                    FontLoaders.C14.drawString("No songs", x + 8.0f, y + 10.0f, module.withAlpha(MUTED, alpha));
                    return;
                }
                int start = Math.max(0, scroll);
                int maxRows = Math.min(songs.size(), start + Math.max(1, (int) (h / 46.0f) + 2));
                float eased = ease(listAnimation);
                for (int i = start; i < maxRows; i++) {
                    NeteaseMusicApi.Song song = songs.get(i);
                    float rowY = y + (i - start) * 46.0f + 8.0f * (1.0f - eased);
                    boolean active = module.currentSong != null && module.currentSong.id == song.id;
                    drawSongRow(song, x, rowY, w, active, alpha * eased);
                }
            } finally {
                gq.yozakura.engine.render.GLStateManager.popScissor();
            }
        }

        private void drawSongRow(NeteaseMusicApi.Song song, float x, float y, float w, boolean active, float alpha) {
            RenderServices.shapes().roundedBorder(x, y, x + w, y + 38.0f, 9.0f, 0.7f,
                    module.withAlpha(active ? 0x4A301546 : 0x1CFFFFFF, alpha),
                    module.withAlpha(active ? 0x7EFF8EC5 : 0x14FFFFFF, alpha));
            ResourceLocation cover = module.covers.texture(song.coverUrl);
            RenderUtil.drawRoundedRect(x + 8.0f, y + 6.0f, x + 34.0f, y + 32.0f, 5.0f, module.withAlpha(0x3328183A, alpha));
            if (cover != null) {
                RenderUtil.drawImage(cover, (int) (x + 8.0f), (int) (y + 6.0f), 26.0f, 26.0f, alpha);
            }
            FontLoaders.C14.drawString(module.trim(song.name, FontLoaders.C14, w - 130.0f), x + 44.0f, y + 7.0f,
                    module.withAlpha(TEXT, alpha));
            FontLoaders.C12.drawString(module.trim(song.artist + " · " + song.album, FontLoaders.C12, w - 138.0f),
                    x + 44.0f, y + 22.0f, module.withAlpha(MUTED, alpha));
            String time = module.timeText(song.durationMs);
            FontLoaders.C12.drawString(time, x + w - FontLoaders.C12.getStringWidth(time) - 14.0f, y + 14.0f,
                    module.withAlpha(active ? PINK : MUTED, alpha));
        }

        private void drawPlaylistRows(List<NeteaseMusicApi.Playlist> list, float x, float y, float w, float h, float alpha) {
            gq.yozakura.engine.render.GLStateManager.pushScissor(x, y, w, h);
            try {
                if (list.isEmpty()) {
                    FontLoaders.C14.drawString("No playlists", x + 8.0f, y + 10.0f, module.withAlpha(MUTED, alpha));
                    return;
                }
                int start = Math.max(0, playlistScroll);
                int maxRows = Math.min(list.size(), start + Math.max(1, (int) (h / 48.0f) + 2));
                for (int i = start; i < maxRows; i++) {
                    NeteaseMusicApi.Playlist playlist = list.get(i);
                    float rowY = y + (i - start) * 48.0f;
                    boolean active = selectedPlaylist == playlist.id;
                    RenderServices.shapes().roundedBorder(x, rowY, x + w, rowY + 40.0f, 9.0f, 0.7f,
                            module.withAlpha(active ? 0x4A301546 : 0x1CFFFFFF, alpha),
                            module.withAlpha(active ? 0x7EFF8EC5 : 0x14FFFFFF, alpha));
                    ResourceLocation cover = module.covers.texture(playlist.coverUrl);
                    RenderUtil.drawRoundedRect(x + 7.0f, rowY + 6.0f, x + 35.0f, rowY + 34.0f, 5.0f,
                            module.withAlpha(0x3328183A, alpha));
                    if (cover != null) {
                        RenderUtil.drawImage(cover, (int) (x + 7.0f), (int) (rowY + 6.0f), 28.0f, 28.0f, alpha);
                    }
                    CFontRenderer titleFont = FontLoaders.regular(13);
                    titleFont.drawString(module.trim(playlist.name, titleFont, w - 50.0f),
                            x + 43.0f, rowY + 8.0f, module.withAlpha(TEXT, alpha));
                    String count = playlist.trackCount + " tracks";
                    FontLoaders.C12.drawString(count, x + 43.0f, rowY + 24.0f, module.withAlpha(MUTED, alpha));
                }
            } finally {
                gq.yozakura.engine.render.GLStateManager.popScissor();
            }
        }

        private void drawCommandButton(String text, float x, float y, float w, float h, float alpha) {
            RenderUtil.drawGlowAround(x + 2.0f, y + 2.0f, x + w - 2.0f, y + h - 2.0f, 10.0f,
                    module.withAlpha(0x50FF8EC5, alpha), 0.7f);
            RenderServices.shapes().roundedBorder(x, y, x + w, y + h, 10.0f, 0.8f,
                    module.withAlpha(0x55301546, alpha), module.withAlpha(0x82FF8EC5, alpha));
            FontLoaders.C14.drawString(text, x + (w - FontLoaders.C14.getStringWidth(text)) / 2.0f,
                    y + (h - FontLoaders.C14.getHeight()) / 2.0f + 2.0f, module.withAlpha(TEXT, alpha));
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
            if (mouseButton != 0 || closing) {
                super.mouseClicked(mouseX, mouseY, mouseButton);
                return;
            }
            ScaledResolution sr = new ScaledResolution(mc);
            float w = Math.min(700.0f, sr.getScaledWidth() - 36.0f);
            float h = Math.min(380.0f, sr.getScaledHeight() - 36.0f);
            float x = (sr.getScaledWidth() - w) / 2.0f;
            float y = (sr.getScaledHeight() - h) / 2.0f;
            if (inside(mouseX, mouseY, x + w - 44.0f, y + 14.0f, 30.0f, 30.0f)) {
                startClosing();
                return;
            }
            handleControlClick(mouseX, mouseY, x, y, h);
            float cx = x + 238.0f;
            float cy = y + 20.0f;
            float cw = w - 260.0f;
            if (inside(mouseX, mouseY, cx, cy, 74.0f, 28.0f)) {
                setTab(LibraryTab.SEARCH);
                return;
            }
            if (inside(mouseX, mouseY, cx + 84.0f, cy, 74.0f, 28.0f)) {
                setTab(LibraryTab.PLAYLISTS);
                if (module.profile.id != 0L && module.playlistSnapshot().isEmpty()) {
                    module.loadPlaylists();
                }
                return;
            }
            if (inside(mouseX, mouseY, cx + 168.0f, cy, 54.0f, 28.0f)) {
                setTab(LibraryTab.QR_LOGIN);
                if (module.profile.id == 0L) {
                    module.startQrLogin();
                }
                return;
            }
            if (inside(mouseX, mouseY, cx + cw - 170.0f, cy, 48.0f, 28.0f)) {
                module.displayMode.setValue(DisplayMode.EXPANDED);
                return;
            }
            if (inside(mouseX, mouseY, cx + cw - 114.0f, cy, 58.0f, 28.0f)) {
                module.displayMode.setValue(DisplayMode.LYRICS);
                return;
            }
            if (inside(mouseX, mouseY, cx + cw - 48.0f, cy, 48.0f, 28.0f)) {
                module.displayMode.setValue(DisplayMode.MINI);
                return;
            }
            if (tab == LibraryTab.SEARCH) {
                handleSearchClick(mouseX, mouseY, cx, cy + 47.0f, cw);
            } else if (tab == LibraryTab.PLAYLISTS) {
                handlePlaylistClick(mouseX, mouseY, cx, cy + 47.0f, cw, h - 91.0f);
            } else if (tab == LibraryTab.QR_LOGIN && module.profile.id == 0L) {
                module.startQrLogin();
            }
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }

        private void handleControlClick(int mouseX, int mouseY, float x, float y, float h) {
            float bx = x + 38.0f;
            float by = y + h - 40.0f;
            long now = System.currentTimeMillis();
            if (now - lastAction < 180L) {
                return;
            }
            if (inside(mouseX, mouseY, bx, by, 38.0f, 30.0f)) {
                lastAction = now;
                module.playPrevious();
            } else if (inside(mouseX, mouseY, bx + 47.0f, by - 4.0f, 46.0f, 38.0f)) {
                lastAction = now;
                module.togglePlayback();
            } else if (inside(mouseX, mouseY, bx + 102.0f, by, 38.0f, 30.0f)) {
                lastAction = now;
                module.playNext();
            } else if (inside(mouseX, mouseY, bx + 149.0f, by, 38.0f, 30.0f)) {
                lastAction = now;
                module.displayMode.setValue(module.displayMode.getValue() == DisplayMode.MINI
                        ? DisplayMode.EXPANDED : DisplayMode.MINI);
            }
        }

        private void handleSearchClick(int mouseX, int mouseY, float x, float y, float w) {
            searchFocused = inside(mouseX, mouseY, x, y, w - 88.0f, 32.0f);
            if (inside(mouseX, mouseY, x + w - 78.0f, y, 78.0f, 32.0f)) {
                module.search(searchText, false);
                searchScroll = 0;
                listAnimation = 0.0f;
                return;
            }
            int row = (int) ((mouseY - (y + 62.0f)) / 46.0f) + searchScroll;
            List<NeteaseMusicApi.Song> songs = module.searchSnapshot();
            if (row >= 0 && row < songs.size()) {
                module.selectSong(songs.get(row), true);
                listAnimation = 0.0f;
            }
        }

        private void handlePlaylistClick(int mouseX, int mouseY, float x, float y, float w, float h) {
            if (module.profile.id == 0L) {
                setTab(LibraryTab.QR_LOGIN);
                module.startQrLogin();
                return;
            }
            float leftW = Math.min(190.0f, w * 0.42f);
            if (inside(mouseX, mouseY, x, y + 28.0f, leftW, h - 28.0f)) {
                int row = (int) ((mouseY - (y + 28.0f)) / 48.0f) + playlistScroll;
                List<NeteaseMusicApi.Playlist> list = module.playlistSnapshot();
                if (row >= 0 && row < list.size()) {
                    selectedPlaylist = list.get(row).id;
                    songScroll = 0;
                    listAnimation = 0.0f;
                    module.loadPlaylist(selectedPlaylist, false);
                }
                return;
            }
            int row = (int) ((mouseY - (y + 28.0f)) / 46.0f) + songScroll;
            List<NeteaseMusicApi.Song> songs = module.playlistSongSnapshot();
            if (row >= 0 && row < songs.size()) {
                module.selectSong(songs.get(row), true);
                listAnimation = 0.0f;
            }
        }

        private void setTab(LibraryTab next) {
            if (next == null || tab == next) {
                return;
            }
            previousTab = tab;
            tab = next;
            tabAnimation = 0.0f;
            listAnimation = 0.0f;
            searchFocused = next == LibraryTab.SEARCH;
        }

        @Override
        public void handleMouseInput() throws IOException {
            int wheel = Mouse.getEventDWheel();
            if (wheel != 0) {
                if (tab == LibraryTab.SEARCH) {
                    searchScroll = clampScroll(searchScroll + (wheel < 0 ? 1 : -1), module.searchSnapshot().size());
                } else if (tab == LibraryTab.PLAYLISTS) {
                    ScaledResolution sr = new ScaledResolution(mc);
                    int mouseX = Mouse.getEventX() * sr.getScaledWidth() / Math.max(1, mc.displayWidth);
                    int mouseY = sr.getScaledHeight() - Mouse.getEventY() * sr.getScaledHeight() / Math.max(1, mc.displayHeight) - 1;
                    float w = Math.min(700.0f, sr.getScaledWidth() - 36.0f);
                    float h = Math.min(380.0f, sr.getScaledHeight() - 36.0f);
                    float x = (sr.getScaledWidth() - w) / 2.0f + 238.0f;
                    float y = (sr.getScaledHeight() - h) / 2.0f + 67.0f;
                    float leftW = Math.min(190.0f, (w - 260.0f) * 0.42f);
                    if (inside(mouseX, mouseY, x, y + 28.0f, leftW, h - 119.0f)) {
                        playlistScroll = clampScroll(playlistScroll + (wheel < 0 ? 1 : -1), module.playlistSnapshot().size());
                    } else {
                        songScroll = clampScroll(songScroll + (wheel < 0 ? 1 : -1), module.playlistSongSnapshot().size());
                    }
                }
            }
            super.handleMouseInput();
        }

        private int clampScroll(int value, int size) {
            return Math.max(0, Math.min(Math.max(0, size - 1), value));
        }

        @Override
        protected void keyTyped(char typedChar, int keyCode) throws IOException {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                startClosing();
                return;
            }
            if (tab == LibraryTab.SEARCH && searchFocused) {
                if (keyCode == Keyboard.KEY_RETURN) {
                    module.search(searchText, false);
                    searchScroll = 0;
                    listAnimation = 0.0f;
                    return;
                }
                if (keyCode == Keyboard.KEY_BACK) {
                    if (searchText.length() > 0) {
                        searchText = searchText.substring(0, searchText.length() - 1);
                    }
                    return;
                }
                if (keyCode == Keyboard.KEY_DELETE) {
                    searchText = "";
                    return;
                }
                if (typedChar >= 32 && typedChar != 127 && searchText.length() < 48) {
                    searchText += typedChar;
                    return;
                }
            }
            super.keyTyped(typedChar, keyCode);
        }

        void startClosing() {
            closing = true;
            searchFocused = false;
        }

        @Override
        public void onGuiClosed() {
            Keyboard.enableRepeatEvents(false);
            super.onGuiClosed();
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }

        private boolean inside(float mouseX, float mouseY, float x, float y, float w, float h) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }
}
