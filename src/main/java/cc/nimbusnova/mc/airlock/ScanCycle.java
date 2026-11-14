package cc.nimbusnova.mc.airlock;

public final class ScanCycle {
    private int elapsed, emptyTicks;
    private boolean scanning, cleared, outbound;

    public void tick(final boolean occupied, final boolean fullyInside, final boolean insideApproach) {
        if (!occupied) {
            if (++emptyTicks >= 20) {
                elapsed = 0;
                scanning = false;
                cleared = false;
                outbound = false;
            }
            if (!insideApproach) return;
        } else
            emptyTicks = 0;
        if (!scanning && !cleared && insideApproach)
            outbound = true;

        if (!scanning && !cleared && fullyInside) {
            outbound = insideApproach;
            scanning = true;
        }

        if (scanning && ++elapsed >= 80) {
            scanning = false;
            cleared = true;
        }
    }

    public int stage() {
        return scanning ? 1 : cleared ? 2 : 0;
    }

    public int elapsed() {
        return elapsed;
    }

    public boolean outbound() {
        return outbound;
    }

    public boolean outerOpen() {
        return !scanning && cleared == outbound;
    }

    public boolean innerOpen() {
        return !scanning && cleared != outbound;
    }
}
