package gq.yozakura.ui.engine.text;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GlyphPagePackerTest {
    @Test
    public void packsRowsWithoutOverlappingAndStartsANewRow() {
        GlyphPagePacker packer = new GlyphPagePacker(16, 16, 1);

        GlyphPagePacker.Slot first = packer.allocate(6, 5);
        GlyphPagePacker.Slot second = packer.allocate(6, 5);
        GlyphPagePacker.Slot third = packer.allocate(6, 5);

        assertEquals(1, first.x());
        assertEquals(1, first.y());
        assertEquals(8, second.x());
        assertEquals(1, second.y());
        assertEquals(1, third.x());
        assertEquals(7, third.y());
    }

    @Test
    public void returnsNullWhenThePageIsFull() {
        GlyphPagePacker packer = new GlyphPagePacker(8, 8, 1);
        packer.allocate(6, 6);
        assertNull(packer.allocate(1, 1));
    }
}
