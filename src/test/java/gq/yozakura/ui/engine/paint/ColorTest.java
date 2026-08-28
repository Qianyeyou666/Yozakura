package gq.yozakura.ui.engine.paint;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * 阶段 3 切片 1：Color 值对象契约测试。
 *
 * <p>覆盖 CSS 颜色字符串解析（#rgb、#rrggbb、#rgba、#rrggbbaa、rgb()、rgba()、命名色），
 * 输出归一化 RGBA float 分量（0..1）与 packed int。
 *
 * <p>不可变值对象；解析失败不静默降级。
 */
public class ColorTest {

    @Test
    public void parsesHexRgbShort() {
        // #abc → #aabbcc
        Color c = Color.parse("#abc");
        assertEquals(0xaa / 255f, c.r(), 0.0001f);
        assertEquals(0xbb / 255f, c.g(), 0.0001f);
        assertEquals(0xcc / 255f, c.b(), 0.0001f);
        assertEquals(1f, c.a(), 0.0001f);
    }

    @Test
    public void parsesHexRrggbb() {
        Color c = Color.parse("#e98bc1");
        assertEquals(0xe9 / 255f, c.r(), 0.0001f);
        assertEquals(0x8b / 255f, c.g(), 0.0001f);
        assertEquals(0xc1 / 255f, c.b(), 0.0001f);
        assertEquals(1f, c.a(), 0.0001f);
    }

    @Test
    public void parsesHexRgbaShort() {
        // #abcd → #aabbccdd
        Color c = Color.parse("#abcd");
        assertEquals(0xdd / 255f, c.a(), 0.0001f);
    }

    @Test
    public void parsesHexRrggbbaa() {
        Color c = Color.parse("#11223344");
        assertEquals(0x11 / 255f, c.r(), 0.0001f);
        assertEquals(0x44 / 255f, c.a(), 0.0001f);
    }

    @Test
    public void parsesRgbFunctional() {
        Color c = Color.parse("rgb(255, 128, 0)");
        assertEquals(1f, c.r(), 0.0001f);
        assertEquals(128f / 255f, c.g(), 0.0001f);
        assertEquals(0f, c.b(), 0.0001f);
        assertEquals(1f, c.a(), 0.0001f);
    }

    @Test
    public void parsesRgbaFunctional() {
        Color c = Color.parse("rgba(255, 0, 0, 0.5)");
        assertEquals(1f, c.r(), 0.0001f);
        assertEquals(0.5f, c.a(), 0.0001f);
    }

    @Test
    public void parsesRgbaWithAlphaPercentage() {
        Color c = Color.parse("rgba(0, 0, 0, 50%)");
        assertEquals(0.5f, c.a(), 0.0001f);
    }

    @Test
    public void parsesNamedColors() {
        assertEquals(1f, Color.parse("red").r(), 0.0001f);
        assertEquals(1f, Color.parse("blue").b(), 0.0001f);
        assertEquals(0f, Color.parse("black").r(), 0.0001f);
        assertEquals(1f, Color.parse("white").r(), 0.0001f);
        assertEquals(1f, Color.parse("white").a(), 0.0001f);
    }

    @Test
    public void parsesTransparent() {
        Color c = Color.parse("transparent");
        assertEquals(0f, c.r(), 0.0001f);
        assertEquals(0f, c.g(), 0.0001f);
        assertEquals(0f, c.b(), 0.0001f);
        assertEquals(0f, c.a(), 0.0001f);
    }

    @Test
    public void parsesCaseInsensitiveNamedColor() {
        Color c = Color.parse("RED");
        assertEquals(1f, c.r(), 0.0001f);
    }

    @Test
    public void parseNullThrows() {
        try {
            Color.parse(null);
            fail("expected IllegalArgumentException for null color");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void parseEmptyThrows() {
        try {
            Color.parse("");
            fail("expected IllegalArgumentException for empty color");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void parseInvalidThrows() {
        // 不静默降级为黑色
        try {
            Color.parse("not-a-color");
            fail("expected IllegalArgumentException for invalid color");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void parseInvalidHexThrows() {
        try {
            Color.parse("#xyz");
            fail("expected IllegalArgumentException for invalid hex");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void packedRgbaIntRoundTrip() {
        // packed ARGB int 与分量互逆
        Color c = Color.parse("#8090a0");
        int packed = c.packedArgb();
        Color back = Color.fromPackedArgb(packed);
        assertEquals(c.r(), back.r(), 0.0001f);
        assertEquals(c.g(), back.g(), 0.0001f);
        assertEquals(c.b(), back.b(), 0.0001f);
        assertEquals(c.a(), back.a(), 0.0001f);
    }

    @Test
    public void equalsAndHashCodeByComponents() {
        Color a = Color.parse("#e98bc1");
        Color b = Color.parse("rgba(233, 139, 193, 1)");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void factoryRgbInt() {
        Color c = Color.fromRgba(0.5f, 0.25f, 0.0f, 1.0f);
        assertEquals(0.5f, c.r(), 0.0001f);
        assertEquals(0.25f, c.g(), 0.0001f);
        assertEquals(0.0f, c.b(), 0.0001f);
        assertEquals(1.0f, c.a(), 0.0001f);
    }

    @Test
    public void factoryClampsOutOfRange() {
        // 超出 [0,1] 的分量钳到 [0,1]
        Color c = Color.fromRgba(2f, -1f, 0.5f, 0f);
        assertEquals(1f, c.r(), 0.0001f);
        assertEquals(0f, c.g(), 0.0001f);
        assertEquals(0.5f, c.b(), 0.0001f);
        assertEquals(0f, c.a(), 0.0001f);
    }
}
