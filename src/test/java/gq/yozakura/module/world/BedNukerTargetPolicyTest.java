package gq.yozakura.module.world;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BedNukerTargetPolicyTest {
    @Test
    public void selectsNearestBedInsideReach() {
        BedNukerTargetPolicy.Position selected = BedNukerTargetPolicy.selectNearest(
                Arrays.asList(
                        new BedNukerTargetPolicy.Position(5, 64, 0),
                        new BedNukerTargetPolicy.Position(2, 64, 0),
                        new BedNukerTargetPolicy.Position(1, 64, 3)),
                0.5D, 64.5D, 0.5D, 4.5D);

        assertEquals(new BedNukerTargetPolicy.Position(2, 64, 0), selected);
    }

    @Test
    public void ignoresBedsOutsideReach() {
        BedNukerTargetPolicy.Position selected = BedNukerTargetPolicy.selectNearest(
                Arrays.asList(new BedNukerTargetPolicy.Position(6, 64, 0)),
                0.5D, 64.5D, 0.5D, 4.5D);

        assertNull(selected);
    }

    @Test
    public void keepsCurrentTargetWhenItIsStillWithinSmallLockMargin() {
        BedNukerTargetPolicy.Position current = new BedNukerTargetPolicy.Position(3, 64, 0);
        BedNukerTargetPolicy.Position selected = BedNukerTargetPolicy.selectNearest(
                Arrays.asList(current, new BedNukerTargetPolicy.Position(2, 64, 0)),
                0.5D, 64.5D, 0.5D, 4.5D, current, 1.25D);

        assertEquals(current, selected);
    }

    @Test
    public void throughWallsAlwaysKeepsTheBedAsTheMiningTarget() {
        BedNukerTargetPolicy.Position bed = new BedNukerTargetPolicy.Position(4, 64, 0);
        BedNukerTargetPolicy.Position cover = new BedNukerTargetPolicy.Position(2, 64, 0);

        assertEquals(bed, BedNukerTargetPolicy.selectMiningTarget(bed, cover, true));
    }

    @Test
    public void disabledThroughWallsSelectsTheFirstCoverBlock() {
        BedNukerTargetPolicy.Position bed = new BedNukerTargetPolicy.Position(4, 64, 0);
        BedNukerTargetPolicy.Position cover = new BedNukerTargetPolicy.Position(2, 64, 0);

        assertEquals(cover, BedNukerTargetPolicy.selectMiningTarget(bed, cover, false));
    }

    @Test
    public void disabledThroughWallsUsesTheBedOnceItIsExposed() {
        BedNukerTargetPolicy.Position bed = new BedNukerTargetPolicy.Position(4, 64, 0);

        assertEquals(bed, BedNukerTargetPolicy.selectMiningTarget(bed, bed, false));
        assertEquals(bed, BedNukerTargetPolicy.selectMiningTarget(bed, null, false));
    }

    @Test
    public void bedNukerWiresTheOpenMyauCoreSettingsAndPacketStages() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/world/BedNuker.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("enum BreakMode"));
        assertTrue(source.contains("BreakMode.NORMAL"));
        assertTrue(source.contains("BreakMode.SWAP"));
        assertTrue(source.contains("\"Ground Spoof\""));
        assertTrue(source.contains("\"Surroundings\""));
        assertTrue(source.contains("\"Tool Check\""));
        assertTrue(source.contains("\"Whitelist\""));
        assertTrue(source.contains("Action.START_DESTROY_BLOCK"));
        assertTrue(source.contains("Action.STOP_DESTROY_BLOCK"));
        assertTrue(source.contains("MinecraftAccessor.syncCurrentPlayItem"));
        assertTrue(source.contains("scheduleWhitelistScan()"));
        assertTrue(source.contains("validateBedPlacement("));
    }

    @Test
    public void runtimeDigSpeedAvoidsTheUnmappedItemMethodThatCrashesStandalone() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/world/BedNuker.java")), StandardCharsets.UTF_8);

        assertFalse(source.contains("item.getItem().getDigSpeed(item, state)"));
        assertTrue(source.contains("item.getStrVsBlock(state.getBlock())"));
    }

    @Test
    public void speedPercentUsesTheOpenMyauCompletionThreshold() {
        assertEquals(1.0D, BedNukerTargetPolicy.completionThreshold(0.0D), 0.0001D);
        assertEquals(0.85D, BedNukerTargetPolicy.completionThreshold(50.0D), 0.0001D);
        assertEquals(0.70D, BedNukerTargetPolicy.completionThreshold(100.0D), 0.0001D);
    }

    @Test
    public void surroundingTargetsPreferFastestBreakThenNearest() {
        BedNukerTargetPolicy.Position nearSlow = new BedNukerTargetPolicy.Position(1, 64, 0);
        BedNukerTargetPolicy.Position farFast = new BedNukerTargetPolicy.Position(3, 64, 0);
        BedNukerTargetPolicy.Position selected = BedNukerTargetPolicy.selectSurrounding(
                Arrays.asList(
                        new BedNukerTargetPolicy.Surrounding(nearSlow, 0.10D),
                        new BedNukerTargetPolicy.Surrounding(farFast, 0.40D)),
                0.5D, 64.5D, 0.5D);

        assertEquals(farFast, selected);
    }

    @Test
    public void whitelistExcludesOnlyRecordedBeds() {
        BedNukerTargetPolicy.Position ownBed = new BedNukerTargetPolicy.Position(1, 64, 0);
        BedNukerTargetPolicy.Position enemyBed = new BedNukerTargetPolicy.Position(3, 64, 0);

        assertTrue(BedNukerTargetPolicy.isEligibleBed(enemyBed, Arrays.asList(ownBed), true));
        assertTrue(!BedNukerTargetPolicy.isEligibleBed(ownBed, Arrays.asList(ownBed), true));
        assertTrue(BedNukerTargetPolicy.isEligibleBed(ownBed, Arrays.asList(ownBed), false));
    }
}
