package gq.yozakura.module.render.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CoverCache {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final NeteaseMusicApi api;
    private final Map<String, byte[]> pending = new ConcurrentHashMap<String, byte[]>();
    private final Map<String, ResourceLocation> textures = new HashMap<String, ResourceLocation>();
    private final Map<String, Boolean> requested = new ConcurrentHashMap<String, Boolean>();

    public CoverCache(NeteaseMusicApi api) {
        this.api = api;
    }

    public ResourceLocation texture(String url) {
        if (url == null || url.length() == 0) {
            return null;
        }
        ResourceLocation ready = textures.get(url);
        if (ready != null) {
            return ready;
        }
        byte[] data = pending.remove(url);
        if (data != null) {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                if (image != null) {
                    ResourceLocation location = mc.getTextureManager().getDynamicTextureLocation(
                            "yozakura_music_cover_" + Math.abs(url.hashCode()), new DynamicTexture(image));
                    textures.put(url, location);
                    return location;
                }
            } catch (Throwable ignored) {
            }
        }
        request(url);
        return null;
    }

    private void request(final String url) {
        if (requested.put(url, Boolean.TRUE) != null) {
            return;
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    pending.put(url, decode(url));
                } catch (Throwable ignored) {
                }
            }
        }, "Yozakura Music Cover");
        thread.setDaemon(true);
        thread.start();
    }

    private byte[] decode(String url) throws Exception {
        if (url.startsWith("data:image")) {
            int comma = url.indexOf(',');
            if (comma >= 0) {
                return Base64.getDecoder().decode(url.substring(comma + 1));
            }
        }
        return api.download(url);
    }

    public void clear() {
        pending.clear();
        requested.clear();
        textures.clear();
    }
}
