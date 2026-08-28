package gq.yozakura.module.world;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AutoDefensePlanTest {
    @Test
    public void shellCoversFloorAndTwoHighWallsWithoutThirdLayer() {
        List<AutoDefensePlan.Offset> shell = AutoDefensePlan.shellOffsets();

        assertEquals(13, shell.size());
        assertFalse(shell.contains(new AutoDefensePlan.Offset(0, 0, 0)));
        assertFalse(shell.contains(new AutoDefensePlan.Offset(0, 1, 0)));
        assertEquals(new AutoDefensePlan.Offset(0, -1, 0), shell.get(0));
        assertEquals(new AutoDefensePlan.Offset(0, 1, -1), shell.get(shell.size() - 1));
        for (AutoDefensePlan.Offset offset : shell) {
            assertTrue("third-height target must never be planned", offset.y <= 1);
        }
    }

    @Test
    public void roofIsOptionalAndNeverPartOfRequiredShell() {
        AutoDefensePlan.Offset roof = AutoDefensePlan.optionalRoofOffset();

        assertEquals(new AutoDefensePlan.Offset(0, 2, 0), roof);
        assertFalse(AutoDefensePlan.shellOffsets().contains(roof));
    }

    @Test
    public void existingBlocksAreSatisfiedAndNeverSelectedAgain() {
        final Set<AutoDefensePlan.Offset> occupied = new HashSet<AutoDefensePlan.Offset>();
        occupied.add(new AutoDefensePlan.Offset(0, -1, 0));
        occupied.add(new AutoDefensePlan.Offset(1, -1, 0));

        AutoDefensePlan.Offset next = AutoDefensePlan.nextMissing(new AutoDefensePlan.CellState() {
            @Override
            public boolean isOccupied(AutoDefensePlan.Offset offset) {
                return occupied.contains(offset);
            }

            @Override
            public boolean intersectsEntity(AutoDefensePlan.Offset offset) {
                return false;
            }
        });

        assertEquals(new AutoDefensePlan.Offset(-1, -1, 0), next);
    }

    @Test
    public void entityOccupiedTargetsAreDeferred() {
        AutoDefensePlan.Offset next = AutoDefensePlan.nextMissing(new AutoDefensePlan.CellState() {
            @Override
            public boolean isOccupied(AutoDefensePlan.Offset offset) {
                return offset.equals(new AutoDefensePlan.Offset(0, -1, 0));
            }

            @Override
            public boolean intersectsEntity(AutoDefensePlan.Offset offset) {
                return offset.equals(new AutoDefensePlan.Offset(1, -1, 0));
            }
        });

        assertEquals(new AutoDefensePlan.Offset(-1, -1, 0), next);
    }

    @Test
    public void completedShellHasNoNextTarget() {
        assertNull(AutoDefensePlan.nextMissing(new AutoDefensePlan.CellState() {
            @Override
            public boolean isOccupied(AutoDefensePlan.Offset offset) {
                return true;
            }

            @Override
            public boolean intersectsEntity(AutoDefensePlan.Offset offset) {
                return false;
            }
        }));
    }
}
