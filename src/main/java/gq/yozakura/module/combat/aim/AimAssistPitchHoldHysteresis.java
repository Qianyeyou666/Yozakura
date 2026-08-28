package gq.yozakura.module.combat.aim;

/** Applies separate enter and exit hit regions to prevent pitch chatter. */
public final class AimAssistPitchHoldHysteresis {
    private boolean holding;

    public boolean update(boolean eligible, boolean innerBoxHit, boolean outerBoxHit) {
        if (!eligible) {
            holding = false;
        } else if (holding) {
            holding = outerBoxHit;
        } else {
            holding = innerBoxHit;
        }
        return holding;
    }

    public void reset() {
        holding = false;
    }
}
