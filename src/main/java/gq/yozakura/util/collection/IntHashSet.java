package gq.yozakura.util.collection;

/**
 * Minimal primitive {@code int}-keyed hash set using open addressing with linear
 * probing. Designed for the hot path where a {@code HashSet<Integer>} would
 * force per-element Integer boxing (e.g. per-entity {@code getEntityId()} book
 * keeping inside the render loop).
 *
 * <p>Key invariants:
 * <ul>
 *   <li>{@link #EMPTY} (0) is reserved as the empty-slot marker; callers must
 *       only store non-zero keys (Minecraft entity ids are always &gt; 0).</li>
 *   <li>Single-threaded use only. Resize is internal and amortized O(1).</li>
 *   <li>Capacity is a power of two so the probe index can use bit-mask.</li>
 * </ul>
 */
public final class IntHashSet {
    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75F;
    private static final int EMPTY = 0;

    private int[] keys;
    private int mask;
    private int size;
    private int resizeThreshold;

    public IntHashSet() {
        this(DEFAULT_CAPACITY);
    }

    public IntHashSet(int initialCapacity) {
        int capacity = 1;
        while (capacity < initialCapacity) {
            capacity <<= 1;
        }
        this.keys = new int[capacity];
        this.mask = capacity - 1;
        this.resizeThreshold = (int) (capacity * LOAD_FACTOR);
    }

    public boolean add(int key) {
        if (key == EMPTY) {
            throw new IllegalArgumentException("0 is reserved as the empty marker");
        }
        int slot = slotFor(key);
        if (keys[slot] == EMPTY) {
            keys[slot] = key;
            size++;
            if (size > resizeThreshold) {
                grow();
            }
            return true;
        }
        return false;
    }

    public boolean contains(int key) {
        if (key == EMPTY) {
            return false;
        }
        return keys[slotFor(key)] == key;
    }

    /**
     * Removes the key. Returns true if it was present (matching the
     * {@code java.util.Set#remove} contract used by callers).
     *
     * <p>Uses backward-shift deletion to keep probe chains intact: after
     * clearing the slot, walk forward and re-insert any following keys that
     * would otherwise become unreachable.
     */
    public boolean remove(int key) {
        if (key == EMPTY) {
            return false;
        }
        int slot = slotFor(key);
        if (keys[slot] != key) {
            return false;
        }
        int gap = slot;
        int i = (gap + 1) & mask;
        while (keys[i] != EMPTY) {
            int candidate = keys[i];
            int home = candidate & mask;
            // If the candidate's home slot is not in the range (gap, i] (wrapping
            // considered), then it must move into the gap to keep the probe chain
            // minimal.
            boolean inRange;
            if (gap <= i) {
                inRange = gap < home && home <= i;
            } else {
                inRange = home > gap || home <= i;
            }
            if (!inRange) {
                keys[gap] = candidate;
                gap = i;
            }
            i = (i + 1) & mask;
        }
        keys[gap] = EMPTY;
        size--;
        return true;
    }

    public int size() {
        return size;
    }

    public void clear() {
        if (size == 0) {
            return;
        }
        java.util.Arrays.fill(keys, EMPTY);
        size = 0;
    }

    private int slotFor(int key) {
        int i = hash(key) & mask;
        while (keys[i] != EMPTY && keys[i] != key) {
            i = (i + 1) & mask;
        }
        return i;
    }

    private static int hash(int key) {
        // Mix high bits into low to improve distribution for small ids.
        int h = key;
        h ^= (h >>> 16);
        h *= 0x45D9F3B; // Knuth-style multiplier
        h ^= (h >>> 16);
        return h;
    }

    private void grow() {
        int[] previous = keys;
        int newCapacity = previous.length << 1;
        keys = new int[newCapacity];
        mask = newCapacity - 1;
        resizeThreshold = (int) (newCapacity * LOAD_FACTOR);
        for (int k : previous) {
            if (k != EMPTY) {
                int slot = hash(k) & mask;
                while (keys[slot] != EMPTY) {
                    slot = (slot + 1) & mask;
                }
                keys[slot] = k;
            }
        }
    }
}
