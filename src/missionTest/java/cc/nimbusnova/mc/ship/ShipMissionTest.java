package cc.nimbusnova.mc.ship;

import cc.nimbusnova.mc.mission.MissionPreparation;
import cc.nimbusnova.mc.moon.MoonMission;

public final class ShipMissionTest {
    private ShipMissionTest() {
        /* w */
    }

    public static void main(final String[] arguments) {
        final ShipMission complete = prepared(true);
        if (complete.snapshot().elapsed() != 0)
            throw new AssertionError("Preparation must be untimed");
        final int stale = complete.revision();
        act(complete, ShipTask.GALLEY, 0);
        final var beforeDuplicate = complete.snapshot();
        if (complete.action(ShipTask.GALLEY, complete.revision(), 0)
                || complete.action(ShipTask.ENGINE, stale, 0) || !complete.snapshot().equals(beforeDuplicate))
            throw new AssertionError("Duplicate rewards and stale puzzle packets must not change the mission");
        complete.start();
        verifyPaused(complete);
        resume(complete);
        align(complete, ShipTask.PILOT);
        align(complete, ShipTask.COMMS);
        if (complete.snapshot().stage() != MoonMission.Stage.NAVIGATION)
            throw new AssertionError("Both docking stations must be verified before navigation");
        align(complete, ShipTask.PILOT);
        verifyPaused(complete);
        resume(complete);
        final int emergencyRevision = complete.revision();
        if (complete.action(ShipTask.ENGINE, emergencyRevision - 1, 1))
            throw new AssertionError("Old control packets must not change live loads");
        act(complete, ShipTask.ENGINE, 3);
        if (complete.snapshot().stage() != MoonMission.Stage.EMERGENCY)
            throw new AssertionError("An unsafe load plan must not progress");
        act(complete, ShipTask.ENGINE, 1);
        act(complete, ShipTask.ENGINE, 2);
        act(complete, ShipTask.ENGINE, 3);
        verifyPaused(complete);
        resume(complete);
        filter(complete);
        verifyPaused(complete);
        resume(complete);
        circuit(complete);
        act(complete, ShipTask.GALLEY, 3);
        if (complete.snapshot().stage() != MoonMission.Stage.RESTART)
            throw new AssertionError("A missing heat allocation must not progress");
        act(complete, ShipTask.GALLEY, 0);
        act(complete, ShipTask.GALLEY, 1);
        act(complete, ShipTask.GALLEY, 3);
        align(complete, ShipTask.PILOT);
        align(complete, ShipTask.COMMS);
        if (complete.snapshot().phase() != ShipMission.Phase.SUCCESS || complete.snapshot().stage() != MoonMission.Stage.COMPLETE)
            throw new AssertionError("The complete physical rescue path must recover the crew");
        final var ended = complete.snapshot();
        complete.tickSecond();
        if (complete.action(ShipTask.SCIENCE, complete.revision(), 0) || !complete.snapshot().equals(ended)
                || complete.debrief().stream().noneMatch(line -> line.contains("CO2")))
            throw new AssertionError("Ended missions must stop and preserve their decision report");

        final ShipMission timeout = prepared(false);
        timeout.start();
        resume(timeout);
        for (int second = 0; second < ShipMission.DURATION_SECONDS; second++)
            timeout.tickSecond();
        if (timeout.snapshot().phase() != ShipMission.Phase.FAILED || timeout.snapshot().remaining() != 0)
            throw new AssertionError("Waiting at the docking task must close the return window");

        final ShipMission co2 = prepared(false);
        co2.start();
        resume(co2);
        align(co2, ShipTask.PILOT);
        align(co2, ShipTask.COMMS);
        align(co2, ShipTask.PILOT);
        resume(co2);
        act(co2, ShipTask.ENGINE, 1);
        act(co2, ShipTask.ENGINE, 2);
        act(co2, ShipTask.ENGINE, 3);
        resume(co2);
        for (int second = 0; second < 200; second++)
            co2.tickSecond();
        if (co2.snapshot().phase() != ShipMission.Phase.FAILED || co2.snapshot().oxygen() <= 0 || co2.snapshot().co2() != 100)
            throw new AssertionError("CO2 can fail a mission while stored oxygen remains");

        final ShipMission meeting = prepared(false);
        meeting.start();
        resume(meeting);
        act(meeting, ShipTask.MEETING, 0);
        verifyPaused(meeting);
        meeting.resume();
        for (int rep = 0; rep < 5; rep++)
            act(meeting, ShipTask.EXERCISE, 0);
        final double waterAfterSet = meeting.snapshot().water();
        if (meeting.action(ShipTask.EXERCISE, meeting.revision(), 0) || meeting.snapshot().water() != waterAfterSet)
            throw new AssertionError("Exercise must not provide unlimited resource rewards");
        System.out.println("Ship mission checks passed: physical rescue flow, stale actions, finite supplies, paused teaching, CO2 hazard and timeout.");
    }

    private static ShipMission prepared(final boolean extraParts) {
        final ShipMission mission = new ShipMission();
        if (mission.action(ShipTask.PILOT, mission.revision(), 0))
            throw new AssertionError("Flight controls are not available before launch");
        mission.tickSecond();
        circuit(mission);
        act(mission, ShipTask.STORAGE, extraParts ? 0 : 1);
        if (!mission.ready())
            throw new AssertionError("Verified power and cargo must enable launch");
        return mission;
    }

    private static void circuit(final ShipMission mission) {
        for (int cell = 0; cell < 9 && !mission.completed(ShipTask.ENGINE); cell++) {
            int turns = 0;
            while (!mission.completed(ShipTask.ENGINE) && (mission.board(ShipTask.ENGINE) >> cell * 2 & 3) != 0 && turns++ < 4)
                act(mission, ShipTask.ENGINE, cell);
        }
        if (!mission.completed(ShipTask.ENGINE) || !MissionPreparation.powerConnected(mission.board(ShipTask.ENGINE)))
            throw new AssertionError("A physically connected circuit must be verified");
    }

    private static void align(final ShipMission mission, final ShipTask task) {
        final MoonMission.Stage stage = mission.snapshot().stage();
        for (int axis = 0; axis < 3; axis++) {
            int turns = 0;
            while (ShipMission.dial(mission.board(task), axis) != ShipMission.dial(mission.target(task), axis) && turns++ < 5)
                act(mission, task, axis * 2);
        }
        act(mission, task, 6);
        if (mission.snapshot().stage() == stage && !mission.completed(task))
            throw new AssertionError("Matched guidance must be verified");
    }

    private static void filter(final ShipMission mission) {
        act(mission, ShipTask.MEDICAL, 6);
        if (mission.snapshot().stage() != MoonMission.Stage.FILTER)
            throw new AssertionError("An unbalanced filter must not start");
        for (int valve = 0; valve < 2; valve++) {
            act(mission, ShipTask.MEDICAL, valve * 2);
            act(mission, ShipTask.MEDICAL, valve * 2);
        }
        mission.tickSecond();
        mission.tickSecond();
        mission.tickSecond();
        if (mission.aux(ShipTask.MEDICAL) >>> 16 != 3)
            throw new AssertionError("Balanced pressure and flow must build stability");
        act(mission, ShipTask.MEDICAL, 6);
    }

    private static void verifyPaused(final ShipMission mission) {
        if (mission.snapshot().phase() != ShipMission.Phase.PAUSED)
            throw new AssertionError("The incident must pause the physical simulation");
        final var before = mission.snapshot();
        mission.tickSecond();
        if (mission.action(ShipTask.ENGINE, mission.revision(), 0) || !mission.snapshot().equals(before))
            throw new AssertionError("Paused clocks, reserves and controls must remain frozen");
    }

    private static void resume(final ShipMission mission) {
        if (mission.underlying().incident() != null)
            mission.acknowledge();
        mission.resume();
    }

    private static void act(final ShipMission mission, final ShipTask task, final int action) {
        if (!mission.action(task, mission.revision(), action))
            throw new AssertionError("Rejected valid physical action " + task + " / " + action + " at " + mission.snapshot().stage());
    }
}
