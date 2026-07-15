package gq.yozakura.bridge;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerPacketTickGateTest {
    @Test
    public void advancesOnlyForTheOnePlayerPacketMarkedForATick() {
        PlayerPacketTickGate gate = new PlayerPacketTickGate();

        gate.markNextPlayerPacket(41L);

        assertTrue(gate.consumeNextPlayerPacket());
        assertFalse(gate.consumeNextPlayerPacket());
    }

    @Test
    public void keepsOnlyTheLatestMarkerWhenVanillaSkipsAPlayerPacket() {
        PlayerPacketTickGate gate = new PlayerPacketTickGate();

        gate.markNextPlayerPacket(41L);
        gate.markNextPlayerPacket(42L);

        assertTrue(gate.consumeNextPlayerPacket());
        assertFalse(gate.consumeNextPlayerPacket());
    }

    @Test
    public void doesNotLetALateOlderMarkerReplaceANewerOne() {
        PlayerPacketTickGate gate = new PlayerPacketTickGate();

        gate.markNextPlayerPacket(42L);
        gate.markNextPlayerPacket(41L);

        assertTrue(gate.consumeNextPlayerPacket());
        assertFalse(gate.consumeNextPlayerPacket());
    }

    @Test
    public void nonCanonicalPlayerPacketsCannotConsumeTheVanillaTickBoundary() {
        PlayerPacketTickGate gate = new PlayerPacketTickGate();

        gate.markNextPlayerPacket(41L);

        assertFalse(gate.consumeNextCanonicalPlayerPacket(false));
        assertTrue(gate.consumeNextCanonicalPlayerPacket(true));
        assertFalse(gate.consumeNextCanonicalPlayerPacket(true));
    }
}
