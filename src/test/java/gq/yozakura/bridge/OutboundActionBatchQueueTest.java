package gq.yozakura.bridge;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OutboundActionBatchQueueTest {
    @Test
    public void preservesTheNativeAnimationSlotChangeAndAttackOrderAcrossASilentTick() {
        OutboundActionBatchQueue<String> queue = new OutboundActionBatchQueue<String>();

        queue.addCurrent("C0A");
        queue.addCurrent("C09");
        queue.addCurrent("C02-ATTACK");
        queue.promoteCurrent();

        assertEquals("C0A", queue.pollReady());
        assertEquals("C09", queue.pollReady());
        assertEquals("C02-ATTACK", queue.pollReady());
        assertNull(queue.pollReady());
    }

    @Test
    public void keepsTheNewTickBatchSeparateUntilItsOwnRotationPacketHasBeenSent() {
        OutboundActionBatchQueue<String> queue = new OutboundActionBatchQueue<String>();

        queue.addCurrent("prior-C0A");
        queue.promoteCurrent();
        queue.addCurrent("current-C0A");

        assertEquals("prior-C0A", queue.pollReady());
        assertNull(queue.pollReady());

        queue.promoteCurrent();
        assertEquals("current-C0A", queue.pollReady());
        assertNull(queue.pollReady());
    }
}
