package gq.yozakura.module.render;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

final class BedEspBlockSelector {
    private static final int[][] FACE_OFFSETS = new int[][]{
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private BedEspBlockSelector() {
    }

    static Set<Position> collect(Iterable<Position> beds, int horizontalRadius, int heightAboveBed,
                                 Predicate<Position> include) {
        if (beds == null) {
            throw new IllegalArgumentException("beds must not be null");
        }
        if (horizontalRadius < 0 || heightAboveBed < 0) {
            throw new IllegalArgumentException("bed defense bounds must not be negative");
        }
        if (include == null) {
            throw new IllegalArgumentException("include must not be null");
        }

        Set<Position> bedPositions = new HashSet<Position>();
        for (Position bed : beds) {
            if (bed == null) {
                continue;
            }
            bedPositions.add(bed);
        }

        Set<Position> selected = new HashSet<Position>();
        ArrayDeque<Position> pending = new ArrayDeque<Position>();
        for (Position bed : bedPositions) {
            enqueueFaceNeighbors(bed, bedPositions, horizontalRadius, heightAboveBed, include, selected, pending);
        }

        while (!pending.isEmpty()) {
            Position current = pending.removeFirst();
            enqueueFaceNeighbors(current, bedPositions, horizontalRadius, heightAboveBed, include, selected, pending);
        }
        return selected;
    }

    private static void enqueueFaceNeighbors(Position origin, Set<Position> beds, int horizontalRadius,
                                             int heightAboveBed, Predicate<Position> include,
                                             Set<Position> selected, ArrayDeque<Position> pending) {
        for (int[] offset : FACE_OFFSETS) {
            Position candidate = origin.offset(offset[0], offset[1], offset[2]);
            if (beds.contains(candidate) || !withinBounds(candidate, beds, horizontalRadius, heightAboveBed)) {
                continue;
            }
            if (include.test(candidate) && selected.add(candidate)) {
                pending.addLast(candidate);
            }
        }
    }

    private static boolean withinBounds(Position position, Set<Position> beds, int horizontalRadius,
                                        int heightAboveBed) {
        for (Position bed : beds) {
            if (Math.abs(position.x - bed.x) <= horizontalRadius
                    && Math.abs(position.z - bed.z) <= horizontalRadius
                    && position.y >= bed.y && position.y <= bed.y + heightAboveBed) {
                return true;
            }
        }
        return false;
    }

    static final class Position {
        private final int x;
        private final int y;
        private final int z;

        Position(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        int getX() {
            return x;
        }

        int getY() {
            return y;
        }

        int getZ() {
            return z;
        }

        Position offset(int xOffset, int yOffset, int zOffset) {
            return new Position(x + xOffset, y + yOffset, z + zOffset);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Position)) {
                return false;
            }
            Position other = (Position) object;
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
