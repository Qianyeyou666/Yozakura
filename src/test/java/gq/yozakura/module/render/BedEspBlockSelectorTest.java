package gq.yozakura.module.render;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BedEspBlockSelectorTest {
    @Test
    public void selectsOnlyNonBedBlocksAtOrAboveTheBed() {
        BedEspBlockSelector.Position bed = new BedEspBlockSelector.Position(12, 64, -8);

        Set<BedEspBlockSelector.Position> blocks = BedEspBlockSelector.collect(
                Collections.singleton(bed), 2, 2, position -> true);

        assertEquals(74, blocks.size());
        assertTrue(blocks.contains(bed.offset(2, 2, -2)));
        assertFalse(blocks.contains(bed));
        assertFalse(blocks.contains(bed.offset(0, -1, 0)));
        for (BedEspBlockSelector.Position position : blocks) {
            assertTrue(position.getY() >= bed.getY());
        }
    }

    @Test
    public void selectsOnlyFaceConnectedDefenseBlocksAndExcludesBedAnchors() {
        BedEspBlockSelector.Position foot = new BedEspBlockSelector.Position(0, 64, 0);
        BedEspBlockSelector.Position head = foot.offset(1, 0, 0);
        BedEspBlockSelector.Position front = foot.offset(0, 0, 1);
        BedEspBlockSelector.Position cover = foot.offset(0, 1, 0);
        BedEspBlockSelector.Position upperFront = front.offset(0, 1, 0);
        BedEspBlockSelector.Position diagonalOnly = foot.offset(-1, 1, -1);
        BedEspBlockSelector.Position detachedNearby = foot.offset(-2, 2, -2);
        BedEspBlockSelector.Position floorBelow = foot.offset(0, -1, 0);

        Set<BedEspBlockSelector.Position> solid = new HashSet<BedEspBlockSelector.Position>(
                Arrays.asList(foot, head, front, cover, upperFront,
                        diagonalOnly, detachedNearby, floorBelow));

        Set<BedEspBlockSelector.Position> selected = BedEspBlockSelector.collect(
                Arrays.asList(foot, head), 2, 2, solid::contains);

        Set<BedEspBlockSelector.Position> expected = new HashSet<BedEspBlockSelector.Position>(
                Arrays.asList(front, cover, upperFront));
        assertEquals(expected, selected);
        assertFalse(selected.contains(foot));
        assertFalse(selected.contains(head));
        assertFalse(selected.contains(diagonalOnly));
        assertFalse(selected.contains(detachedNearby));
        assertFalse(selected.contains(floorBelow));
    }

    @Test
    public void keepsOnlyBlocksAcceptedByTheWorldPredicate() {
        BedEspBlockSelector.Position bed = new BedEspBlockSelector.Position(0, 70, 0);
        BedEspBlockSelector.Position defense = bed.offset(1, 0, 0);

        Set<BedEspBlockSelector.Position> blocks = BedEspBlockSelector.collect(
                Collections.singleton(bed), 1, 1, defense::equals);

        assertEquals(Collections.singleton(defense), blocks);
    }
}
