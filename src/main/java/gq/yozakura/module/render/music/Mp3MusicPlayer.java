package gq.yozakura.module.render.music;

import javazoom.jl.player.Player;

import java.io.BufferedInputStream;
import java.net.URL;

public final class Mp3MusicPlayer {
    private volatile Player player;
    private volatile Thread thread;
    private volatile boolean playing;
    private volatile boolean paused;
    private volatile long startedAt;
    private volatile long pausedAt;
    private volatile long offsetMs;

    public synchronized void play(final String url, long startMs) {
        stop();
        if (url == null || url.length() == 0) {
            return;
        }
        offsetMs = Math.max(0L, startMs);
        pausedAt = 0L;
        playing = true;
        paused = false;
        startedAt = System.currentTimeMillis() - offsetMs;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedInputStream input = new BufferedInputStream(new URL(url).openStream());
                    try {
                        Player next = new Player(input);
                        player = next;
                        next.play();
                    } finally {
                        input.close();
                    }
                } catch (Throwable ignored) {
                } finally {
                    playing = false;
                }
            }
        }, "Yozakura Music Playback");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        playing = false;
        paused = false;
        pausedAt = 0L;
        Player old = player;
        player = null;
        if (old != null) {
            try {
                old.close();
            } catch (Throwable ignored) {
            }
        }
        thread = null;
    }

    public synchronized void pause() {
        if (!playing) {
            return;
        }
        pausedAt = positionMs();
        stop();
        offsetMs = pausedAt;
        paused = true;
    }

    public synchronized void resume(String url) {
        if (playing) {
            return;
        }
        paused = false;
        play(url, offsetMs);
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isPaused() {
        return paused && !playing;
    }

    public long positionMs() {
        return playing ? Math.max(0L, System.currentTimeMillis() - startedAt) : Math.max(0L, offsetMs);
    }
}
