package cc.nimbusnova.mc.airlock;

public final class ScanCycleTest {
    private ScanCycleTest() {
        /* w */
    }

    public static void main(final String[] arguments) {
        final ScanCycle entry = new ScanCycle();
        assert entry.outerOpen() && !entry.innerOpen();
        entry.tick(true, true, false);
        assert !entry.outerOpen() && !entry.innerOpen() && entry.stage() == 1;
        for (int tick = 1; tick < 80; tick++) {
            entry.tick(true, true, false);
            assert !(entry.outerOpen() && entry.innerOpen()) : "Both barriers cannot open together";
        }

        assert entry.stage() == 2 && entry.innerOpen() && !entry.outerOpen();
        for (int tick = 0; tick < 20; tick++)
            entry.tick(false, false, true);

        assert entry.stage() == 0 && entry.outbound() && entry.innerOpen() && !entry.outerOpen();
        for (int tick = 0; tick < 80; tick++)
            entry.tick(true, true, true);

        assert entry.stage() == 2 && entry.outerOpen() && !entry.innerOpen();
        for (int tick = 0; tick < 20; tick++)
            entry.tick(false, false, false);

        assert entry.stage() == 0 && !entry.outbound() && entry.outerOpen();
        final ScanCycle abandoned = new ScanCycle();
        abandoned.tick(true, true, false);
        for (int tick = 0; tick < 20; tick++)
            abandoned.tick(false, false, false);

        assert abandoned.stage() == 0 : "Leaving or disconnecting must reset the lock";
        System.out.println("Entrance lock model passed: inbound/outbound scans, mutually exclusive gates and safe empty reset.");
    }
}
