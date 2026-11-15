package cc.nimbusnova.mc.mission;

public final class MissionVehicleChargeTest {
    private MissionVehicleChargeTest() {
        /* w */
    }

    public static void main(final String[] arguments) {
        final MissionState state = new MissionState();
        if (!state.transferVehicleCharge() || state.snapshot().battery() != 80)
            throw new AssertionError("Preparation must be a free charging rehearsal");
        state.start();
        if (!state.transferVehicleCharge() || state.snapshot().battery() != 75 || state.snapshot().decisions() != 1)
            throw new AssertionError("Driving energy must come from the shared reserve");
        state.pause();
        if (state.transferVehicleCharge() || state.snapshot().battery() != 75)
            throw new AssertionError("Paused missions must not spend reserve power");
        state.resume();
        for (int charge = 0; charge < 15; charge++) {
            if (!state.transferVehicleCharge()) throw new AssertionError("A valid reserve transfer was rejected");
        }
        if (state.snapshot().battery() != 0 || state.transferVehicleCharge())
            throw new AssertionError("Charging cannot produce negative shared energy");
        state.abort();
        if (state.transferVehicleCharge()) throw new AssertionError("Ended attempts cannot charge vehicles");
        System.out.println("Vehicle charge checks passed: free rehearsal, finite transfer, pause and ended guards.");
    }
}
