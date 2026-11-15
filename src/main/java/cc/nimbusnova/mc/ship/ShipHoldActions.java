package cc.nimbusnova.mc.ship;

import cc.nimbusnova.mc.moon.MoonMission;

public final class ShipHoldActions {
    private ShipHoldActions() {
        /* w */
    }

    public static boolean requires(final ShipTask task, final MoonMission.Stage stage, final int action) {
        return switch (task) {
            case ENGINE -> stage == MoonMission.Stage.EMERGENCY && action == 3;
            case PILOT -> stage == MoonMission.Stage.DOCKING && action == 5 || stage == MoonMission.Stage.DESCENT && action == 6;
            case COMMS -> action == 4;
            case MEDICAL -> action == (stage == MoonMission.Stage.FILTER ? 5 : 6);
            case GALLEY -> action == (stage == MoonMission.Stage.RESTART ? 3 : 6);
            case PLANTS -> action == 8;
            case SCIENCE -> action == 9;
            case STORAGE -> action == 3;
            case MEETING -> action == 0;
            case EXERCISE -> false;
        };
    }

    public static int durationTicks(final ShipTask task, final MoonMission.Stage stage, final int action) {
        if (!requires(task, stage, action)) return 0;
        return task == ShipTask.COMMS || task == ShipTask.SCIENCE ? 30 : 20;
    }

    public static boolean elapsedValid(final int startedTick, final int currentTick, final int durationTicks) {
        final long elapsed = (long) currentTick - startedTick;
        return durationTicks > 0 && elapsed >= durationTicks && elapsed <= 200;
    }
}
