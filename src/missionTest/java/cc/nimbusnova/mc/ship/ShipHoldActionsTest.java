package cc.nimbusnova.mc.ship;

import cc.nimbusnova.mc.moon.MoonMission;

public final class ShipHoldActionsTest {
    private ShipHoldActionsTest() {
        /* w */
    }

    public static void main(final String[] arguments) {
        assert ShipHoldActions.requires(ShipTask.COMMS, MoonMission.Stage.DOCKING, 4);
        assert ShipHoldActions.requires(ShipTask.STORAGE, MoonMission.Stage.PREPARATION, 3);
        assert ShipHoldActions.requires(ShipTask.MEDICAL, MoonMission.Stage.FILTER, 5);
        assert !ShipHoldActions.requires(ShipTask.PILOT, MoonMission.Stage.RETURN, 1) : "A timed cutoff must react immediately";
        assert !ShipHoldActions.requires(ShipTask.ENGINE, MoonMission.Stage.PREPARATION, 3) : "Cartridge placement stays direct";
        final int duration = ShipHoldActions.durationTicks(ShipTask.COMMS, MoonMission.Stage.DOCKING, 4);
        assert !ShipHoldActions.elapsedValid(100, 100 + duration - 1, duration) : "An instant/released control cannot commit";
        assert ShipHoldActions.elapsedValid(100, 100 + duration, duration);
        assert !ShipHoldActions.elapsedValid(100, 301, duration) : "Abandoned holds expire";
        assert !ShipHoldActions.elapsedValid(100, 99, duration);
        assert !ShipHoldActions.elapsedValid(100, 140, 0);
        System.out.println("Ship hold checks passed: commit classification, minimum duration, expiry and immediate steering/cutoff.");
    }
}
