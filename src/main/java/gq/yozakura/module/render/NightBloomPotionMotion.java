package gq.yozakura.module.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retains Night Bloom potion rows long enough for their content and panel height to leave
 * continuously. Active rows reuse the same motion when a potion is reacquired mid-exit.
 */
final class NightBloomPotionMotion {
    private final Map<Integer, RowState> rows = new LinkedHashMap<Integer, RowState>();
    private float layoutRows = 1.0F;

    List<Snapshot> update(List<Integer> activeKeys, float deltaSeconds) {
        Set<Integer> active = new HashSet<Integer>();
        for (RowState state : rows.values()) {
            state.active = false;
        }

        int index = 0;
        if (activeKeys != null) {
            for (Integer key : activeKeys) {
                if (key == null || !active.add(key)) {
                    continue;
                }
                RowState state = rows.get(key);
                if (state == null) {
                    state = new RowState();
                    rows.put(key, state);
                }
                state.active = true;
                state.motion.setVisible(true);
                state.motion.setTargetY(index);
                index++;
            }
        }

        for (Map.Entry<Integer, RowState> entry : rows.entrySet()) {
            if (!entry.getValue().active) {
                entry.getValue().motion.setVisible(false);
            }
        }

        List<Snapshot> snapshots = new ArrayList<Snapshot>();
        Iterator<Map.Entry<Integer, RowState>> iterator = rows.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, RowState> entry = iterator.next();
            RowState state = entry.getValue();
            NightBloomModuleRowMotion.Snapshot frame = state.motion.update(deltaSeconds);
            if (!state.active && state.motion.isFinishedExit()) {
                iterator.remove();
                continue;
            }
            snapshots.add(new Snapshot(entry.getKey(), state.active, frame.getVisibility(), frame.getY()));
        }

        Collections.sort(snapshots, new Comparator<Snapshot>() {
            @Override
            public int compare(Snapshot first, Snapshot second) {
                int position = Float.compare(first.y, second.y);
                if (position != 0) {
                    return position;
                }
                if (first.active != second.active) {
                    return first.active ? 1 : -1;
                }
                return Integer.compare(first.key, second.key);
            }
        });
        updateLayoutRows(snapshots);
        return snapshots;
    }

    boolean hasRetainedRows() {
        return !rows.isEmpty();
    }

    float getLayoutRows() {
        return layoutRows;
    }

    void reset() {
        rows.clear();
        layoutRows = 1.0F;
    }

    private void updateLayoutRows(List<Snapshot> snapshots) {
        float extent = 1.0F;
        for (Snapshot snapshot : snapshots) {
            extent = Math.max(extent, snapshot.y + snapshot.visibility);
        }
        layoutRows = Math.max(1.0F, Math.min(NightBloomHudLayout.MAX_VISIBLE_POTION_ROWS, extent));
    }

    private static final class RowState {
        private final NightBloomModuleRowMotion motion = new NightBloomModuleRowMotion();
        private boolean active;
    }

    static final class Snapshot {
        private final int key;
        private final boolean active;
        private final float visibility;
        private final float y;

        private Snapshot(int key, boolean active, float visibility, float y) {
            this.key = key;
            this.active = active;
            this.visibility = visibility;
            this.y = y;
        }

        int getKey() {
            return key;
        }

        boolean isActive() {
            return active;
        }

        float getVisibility() {
            return visibility;
        }

        float getY() {
            return y;
        }
    }
}
