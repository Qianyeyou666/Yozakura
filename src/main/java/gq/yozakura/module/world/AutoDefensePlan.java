package gq.yozakura.module.world;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Deterministic shell order independent of Minecraft runtime types. */
final class AutoDefensePlan {
    private static final Offset OPTIONAL_ROOF = new Offset(0, 2, 0);
    private static final List<Offset> SHELL = Collections.unmodifiableList(Arrays.asList(
            new Offset(0, -1, 0),
            new Offset(1, -1, 0),
            new Offset(-1, -1, 0),
            new Offset(0, -1, 1),
            new Offset(0, -1, -1),
            new Offset(1, 0, 0),
            new Offset(-1, 0, 0),
            new Offset(0, 0, 1),
            new Offset(0, 0, -1),
            new Offset(1, 1, 0),
            new Offset(-1, 1, 0),
            new Offset(0, 1, 1),
            new Offset(0, 1, -1)
    ));

    private AutoDefensePlan() {
    }

    static List<Offset> shellOffsets() {
        return SHELL;
    }

    static Offset optionalRoofOffset() {
        return OPTIONAL_ROOF;
    }

    static Offset nextMissing(CellState state) {
        if (state == null) {
            return null;
        }
        for (Offset offset : SHELL) {
            if (!state.isOccupied(offset) && !state.intersectsEntity(offset)) {
                return offset;
            }
        }
        return null;
    }

    interface CellState {
        boolean isOccupied(Offset offset);

        boolean intersectsEntity(Offset offset);
    }

    static final class Offset {
        final int x;
        final int y;
        final int z;

        Offset(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Offset)) {
                return false;
            }
            Offset other = (Offset) object;
            return x == other.x && y == other.y && z == other.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }
}
