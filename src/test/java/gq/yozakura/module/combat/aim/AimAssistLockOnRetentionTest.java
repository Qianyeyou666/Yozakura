package gq.yozakura.module.combat.aim;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AimAssistLockOnRetentionTest {
    @Test
    public void retainsALockedTargetThroughBriefEligibilityLoss() {
        AimAssistLockOnRetention retention = new AimAssistLockOnRetention();
        retention.confirmEligible(1000L);

        assertTrue(retention.shouldRetain(1349L));
        assertFalse(retention.shouldRetain(1351L));
    }

    @Test
    public void resetNeverRetainsAFormerTarget() {
        AimAssistLockOnRetention retention = new AimAssistLockOnRetention();
        retention.confirmEligible(1000L);
        retention.reset();

        assertFalse(retention.shouldRetain(1001L));
    }
}
