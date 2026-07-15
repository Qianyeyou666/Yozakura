package gq.yozakura.module.render;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NightBloomPotionMotionTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void removedPotionRemainsVisibleWhileItsRowFadesOut() {
        NightBloomPotionMotion motion = new NightBloomPotionMotion();
        settle(motion, Arrays.asList(4, 9));

        List<NightBloomPotionMotion.Snapshot> leaving = motion.update(Collections.singletonList(9), 0.040F);

        NightBloomPotionMotion.Snapshot removed = row(leaving, 4);
        assertFalse(removed.isActive());
        assertTrue(removed.getVisibility() > 0.0F);
        assertTrue(removed.getVisibility() < 1.0F);
        assertTrue("the panel must retain part of the disappearing row before shrinking",
                motion.getLayoutRows() > 1.0F);

        for (int index = 0; index < 8; index++) {
            motion.update(Collections.singletonList(9), 0.050F);
        }

        assertFalse(contains(motion.update(Collections.singletonList(9), 0.0F), 4));
        assertEquals(1.0F, motion.getLayoutRows(), EPSILON);
    }

    @Test
    public void survivingPotionReflowsTowardItsNewSlotInsteadOfJumping() {
        NightBloomPotionMotion motion = new NightBloomPotionMotion();
        settle(motion, Arrays.asList(4, 9));

        NightBloomPotionMotion.Snapshot surviving = row(motion.update(Collections.singletonList(9), 0.050F), 9);

        assertTrue(surviving.isActive());
        assertTrue(surviving.getY() > 0.0F);
        assertTrue(surviving.getY() < 1.0F);
    }

    @Test
    public void aPotionReacquiredDuringItsExitReversesFromItsCurrentVisibility() {
        NightBloomPotionMotion motion = new NightBloomPotionMotion();
        settle(motion, Collections.singletonList(4));

        float leavingVisibility = row(motion.update(Collections.<Integer>emptyList(), 0.050F), 4).getVisibility();
        float returningVisibility = row(motion.update(Collections.singletonList(4), 0.030F), 4).getVisibility();

        assertTrue(leavingVisibility > 0.0F && leavingVisibility < 1.0F);
        assertTrue("retargeting must continue from the current fade state", returningVisibility > leavingVisibility);
    }

    private static void settle(NightBloomPotionMotion motion, List<Integer> keys) {
        for (int index = 0; index < 6; index++) {
            motion.update(keys, 0.050F);
        }
    }

    private static NightBloomPotionMotion.Snapshot row(List<NightBloomPotionMotion.Snapshot> rows, int key) {
        for (NightBloomPotionMotion.Snapshot row : rows) {
            if (row.getKey() == key) {
                return row;
            }
        }
        throw new AssertionError("Expected potion row " + key);
    }

    private static boolean contains(List<NightBloomPotionMotion.Snapshot> rows, int key) {
        for (NightBloomPotionMotion.Snapshot row : rows) {
            if (row.getKey() == key) {
                return true;
            }
        }
        return false;
    }
}
