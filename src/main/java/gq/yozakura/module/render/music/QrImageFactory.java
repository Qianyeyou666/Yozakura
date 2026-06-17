package gq.yozakura.module.render.music;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

public final class QrImageFactory {
    private QrImageFactory() {
    }

    public static String dataUri(String text, int size) throws Exception {
        int imageSize = Math.max(160, size);
        Map<EncodeHintType, Object> hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 1);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix matrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, imageSize, imageSize, hints);
        BufferedImage image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(255, 246, 252, 255));
            graphics.fillRect(0, 0, imageSize, imageSize);
            graphics.setColor(new Color(45, 19, 58, 255));
            for (int x = 0; x < imageSize; x++) {
                for (int y = 0; y < imageSize; y++) {
                    if (matrix.get(x, y)) {
                        image.setRGB(x, y, 0xFF2D133A);
                    }
                }
            }
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }
}
