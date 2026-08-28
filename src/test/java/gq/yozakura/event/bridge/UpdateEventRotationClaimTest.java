package gq.yozakura.event.bridge;

import gq.yozakura.event.bus.types.EventType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateEventRotationClaimTest {
    @Test
    public void acceptsEqualOrHigherRotationPriorityDuringPreUpdate() {
        UpdateEvent event = preEvent();

        assertTrue(event.trySetRotation(30.0F, 70.0F, 1));
        assertFalse(event.trySetRotation(40.0F, 80.0F, 0));
        assertTrue(event.trySetRotation(50.0F, 85.0F, 1));

        assertEquals(50.0F, event.getNewYaw(), 0.0F);
        assertEquals(85.0F, event.getNewPitch(), 0.0F);
    }

    @Test
    public void rejectsRotationClaimsOutsidePreUpdate() {
        UpdateEvent event = new UpdateEvent(EventType.POST, 0.0F, 0.0F, 0.0F, 0.0F);

        assertFalse(event.trySetRotation(30.0F, 70.0F, 1));
        assertEquals(0.0F, event.getNewYaw(), 0.0F);
        assertEquals(0.0F, event.getNewPitch(), 0.0F);
    }

    @Test
    public void legacySetterKeepsTheExistingRotationSemantics() {
        UpdateEvent event = preEvent();

        event.setRotation(30.0F, 70.0F, 1);
        event.setRotation(40.0F, 80.0F, 0);

        assertEquals(30.0F, event.getNewYaw(), 0.0F);
        assertEquals(70.0F, event.getNewPitch(), 0.0F);
        assertTrue(event.isRotated());
    }

    @Test
    public void packetAndMovementYawPrioritiesRemainIndependent() {
        UpdateEvent event = preEvent();

        assertTrue(event.trySetRotation(30.0F, 70.0F, 4));
        event.setPervRotation(45.0F, 1);
        assertTrue(event.isMoveFix());

        assertTrue(event.trySetRotation(50.0F, 85.0F, 4));

        assertEquals(50.0F, event.getNewYaw(), 0.0F);
        assertEquals(45.0F, event.getPreYaw(), 0.0F);
        assertEquals(1, event.isRotating());
        assertTrue(event.isMoveFix());
    }

    @Test
    public void higherMovementPriorityCanOverrideWithoutChangingPacketRotation() {
        UpdateEvent event = preEvent();

        assertTrue(event.trySetRotation(30.0F, 70.0F, 4));
        event.setPervRotation(45.0F, 1);
        event.setPervRotation(60.0F, 3);
        event.setPervRotation(75.0F, 2);

        assertEquals(30.0F, event.getNewYaw(), 0.0F);
        assertEquals(60.0F, event.getPreYaw(), 0.0F);
        assertEquals(3, event.isRotating());
        assertTrue(event.isMoveFix());
    }

    @Test
    public void movementYawCanSelectPhysicsWithoutGenericInputRemapping() {
        UpdateEvent event = preEvent();

        event.setPervRotation(45.0F, 5, false);

        assertEquals(45.0F, event.getPreYaw(), 0.0F);
        assertEquals(5, event.isRotating());
        assertTrue(event.isRotated());
        assertFalse(event.isMoveFix());
    }

    private static UpdateEvent preEvent() {
        return new UpdateEvent(EventType.PRE, 0.0F, 0.0F, 0.0F, 0.0F);
    }
}
