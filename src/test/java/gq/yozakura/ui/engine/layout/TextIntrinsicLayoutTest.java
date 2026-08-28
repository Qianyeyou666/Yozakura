package gq.yozakura.ui.engine.layout;

import gq.yozakura.ui.engine.css.ComputedStyle;
import gq.yozakura.ui.engine.css.CssParser;
import gq.yozakura.ui.engine.css.StyleResolver;
import gq.yozakura.ui.engine.dom.ElementNode;
import gq.yozakura.ui.engine.dom.HtmlParser;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TextIntrinsicLayoutTest {
    @Test
    public void directTextGivesAutoHeightAnIntrinsicLineBox() {
        ElementNode root = (ElementNode) new HtmlParser().parse("<span>Hello</span>");
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(
                new CssParser().parse("span { width: 100px; font-size: 20px; line-height: 24px; }"), root);
        LayoutBox box = new LayoutEngine().layout(root, styles, new MeasureContext() {
            @Override public int viewportWidth() { return 960; }
            @Override public int viewportHeight() { return 640; }
            @Override public float rootFontSizePx() { return 14.0F; }
        });
        assertEquals(24.0F, box.contentHeight(), 0.001F);
    }
}
