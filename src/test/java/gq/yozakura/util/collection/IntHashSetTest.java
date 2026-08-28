package gq.yozakura.util.collection;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Behavioural contract for {@link IntHashSet}. The hot-path motivation is to
 * avoid per-call Integer boxing when bookkeeping entity ids inside the render
 * loop (see Chams.renderedEntities). The contract must match HashSet<Integer>
 * for add/remove/contains/clear semantics.
 */
public class IntHashSetTest {

    @Test
    public void addReturnsTrueForNewKeyAndFalseForExisting() {
        IntHashSet set = new IntHashSet();
        assertTrue(set.add(42));
        assertFalse(set.add(42));
        assertEquals(1, set.size());
    }

    @Test
    public void containsMatchesAddSemantics() {
        IntHashSet set = new IntHashSet();
        set.add(1001);
        assertTrue(set.contains(1001));
        assertFalse(set.contains(1002));
    }

    @Test
    public void removeClearsEntryAndReturnsWhetherItWasPresent() {
        IntHashSet set = new IntHashSet();
        set.add(7);
        assertTrue(set.remove(7));
        assertFalse(set.contains(7));
        assertFalse(set.remove(7));
        assertEquals(0, set.size());
    }

    @Test
    public void clearDropsAllEntriesButKeepsCapacityUsable() {
        IntHashSet set = new IntHashSet();
        for (int i = 1; i <= 100; i++) {
            set.add(i);
        }
        assertEquals(100, set.size());
        set.clear();
        assertEquals(0, set.size());
        // After clear, re-adding the same keys must still work (no stale slots).
        set.add(5);
        assertTrue(set.contains(5));
        assertEquals(1, set.size());
    }

    @Test
    public void handlesCollisionsAndProbeChainsCorrectly() {
        // Pick keys that collide under the default mask; the implementation uses
        // linear probing, so all collisions must still be retrievable.
        IntHashSet set = new IntHashSet(8); // capacity 8 -> mask 7
        int[] keys = {1, 9, 17, 25, 33}; // 1 & 7 == 1 & (1,9,17,25,33) all collide at slot 1
        for (int k : keys) {
            assertTrue(set.add(k));
        }
        for (int k : keys) {
            assertTrue("expected " + k + " to remain in set after probing", set.contains(k));
        }
        // Removing an interior probe-chain entry must keep later entries reachable.
        assertTrue(set.remove(17));
        assertTrue(set.contains(9));
        assertTrue(set.contains(25));
        assertTrue(set.contains(33));
        assertFalse(set.contains(17));
    }

    @Test
    public void resizingPreservesAllEntriesWhenLoadFactorIsExceeded() {
        IntHashSet set = new IntHashSet(4); // capacity 4, threshold ~3
        for (int i = 1; i <= 500; i++) {
            assertTrue(set.add(i));
        }
        assertEquals(500, set.size());
        for (int i = 1; i <= 500; i++) {
            assertTrue("expected " + i + " to survive resize", set.contains(i));
        }
    }

    @Test
    public void removingAbsentKeyIsSafe() {
        IntHashSet set = new IntHashSet();
        set.add(10);
        assertFalse(set.remove(11));
        assertTrue(set.contains(10));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroKeyBecauseZeroIsTheEmptyMarker() {
        new IntHashSet().add(0);
    }

    @Test
    public void clearOnEmptySetIsNoOp() {
        IntHashSet set = new IntHashSet();
        set.clear();
        assertEquals(0, set.size());
    }
}
