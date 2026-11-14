package cc.nimbusnova.mc.ship;

import cc.nimbusnova.mc.moon.MoonMission;

public final class ShipMissionTest {
    private ShipMissionTest() {
        /* w */
    }

    public static void main(final String[] arguments) {
        final ShipMission cartridges = new ShipMission();
        act(cartridges, ShipTask.ENGINE, 1);
        act(cartridges, ShipTask.ENGINE, 5);
        if ((cartridges.board(ShipTask.ENGINE) & 15) != 0 || (cartridges.board(ShipTask.ENGINE) >> 4 & 15) != 2)
            throw new AssertionError("A moved cartridge must leave its old socket, never duplicate");
        act(cartridges, ShipTask.ENGINE, 0);
        act(cartridges, ShipTask.ENGINE, 1);
        if ((cartridges.board(ShipTask.ENGINE) & 15) != 2 || (cartridges.board(ShipTask.ENGINE) >> 4 & 15) != 1)
            throw new AssertionError("Moving an installed cartridge must swap the previous socket contents");
        final var validPlacement = cartridges.snapshot();
        if (cartridges.action(ShipTask.ENGINE, cartridges.revision(), 16)
                || cartridges.action(ShipTask.ENGINE, cartridges.revision(), 1) || !cartridges.snapshot().equals(validPlacement))
            throw new AssertionError("Invalid and unchanged cartridge placements must not mutate state");

        final ShipMission drift = prepared(false);
        drift.start();
        verifyPaused(drift);
        resume(drift);
        act(drift, ShipTask.PILOT, 1);
        final double fuelBeforeCoast = drift.snapshot().fuel();
        act(drift, ShipTask.PILOT, 4);
        final int radar = drift.board(ShipTask.PILOT);
        if ((radar & 15) != 3 || (radar >> 8 & 7) - 2 != 1 || drift.snapshot().fuel() != fuelBeforeCoast)
            throw new AssertionError("Coasting must preserve momentum without firing a thruster");
        act(drift, ShipTask.PILOT, 5);
        if (drift.completed(ShipTask.PILOT))
            throw new AssertionError("Docking capture cannot accept residual drift");

        final ShipMission complete = prepared(true);
        if (complete.snapshot().elapsed() != 0)
            throw new AssertionError("Preparation must be untimed");
        final int stale = complete.revision();
        act(complete, ShipTask.GALLEY, 0);
        final var beforeDuplicate = complete.snapshot();
        if (complete.action(ShipTask.GALLEY, complete.revision(), 0)
                || complete.action(ShipTask.ENGINE, stale, 0) || !complete.snapshot().equals(beforeDuplicate))
            throw new AssertionError("Duplicate rewards and stale packets must not change the mission");
        reachFilter(complete);
        filter(complete);
        verifyPaused(complete);
        resume(complete);
        powerCartridges(complete);
        act(complete, ShipTask.GALLEY, 3);
        if (complete.snapshot().stage() != MoonMission.Stage.RESTART)
            throw new AssertionError("A missing heat allocation must not progress");
        act(complete, ShipTask.GALLEY, 0);
        act(complete, ShipTask.GALLEY, 1);
        act(complete, ShipTask.GALLEY, 3);
        act(complete, ShipTask.PILOT, 0);
        for (int second = 0; second < 3; second++)
            complete.tickSecond();
        act(complete, ShipTask.PILOT, 1);
        if (complete.completed(ShipTask.PILOT) || (complete.board(ShipTask.PILOT) & 256) != 0)
            throw new AssertionError("An early return cutoff must stop the engine without verifying the corridor");
        act(complete, ShipTask.PILOT, 0);
        for (int second = 0; second < 11; second++)
            complete.tickSecond();
        if ((complete.board(ShipTask.PILOT) & 255) != 11 || (complete.board(ShipTask.PILOT) & 256) != 0)
            throw new AssertionError("An excessive burn must automatically abort at the model bound");
        act(complete, ShipTask.PILOT, 0);
        complete.tickSecond();
        complete.pause();
        final int pausedBurn = complete.board(ShipTask.PILOT);
        final var pausedReserves = complete.snapshot();
        complete.tickSecond();
        if (complete.board(ShipTask.PILOT) != pausedBurn || !complete.snapshot().equals(pausedReserves))
            throw new AssertionError("Teacher pauses must freeze burn progression and fuel");
        complete.resume();
        for (int second = 0; second < 5; second++)
            complete.tickSecond();
        act(complete, ShipTask.PILOT, 1);
        tuneSignal(complete);
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
            throw new AssertionError("Waiting at docking must close the return window");

        final ShipMission co2 = prepared(false);
        reachFilter(co2);
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
            throw new AssertionError("Exercise must not provide unlimited rewards");
        System.out.println("Ship mission checks passed: unique cartridge, momentum, star-fix, waveform, adapter and timed-burn tasks; bounded reserves and failures.");
    }

    private static ShipMission prepared(final boolean extraParts) {
        final ShipMission mission = new ShipMission();
        if (mission.action(ShipTask.PILOT, mission.revision(), 0))
            throw new AssertionError("Flight controls are not available before launch");
        mission.tickSecond();
        powerCartridges(mission);
        act(mission, ShipTask.STORAGE, extraParts ? 0 : 1);
        if (!mission.ready())
            throw new AssertionError("Verified power and cargo must enable launch");
        return mission;
    }

    private static void reachFilter(final ShipMission mission) {
        mission.start();
        verifyPaused(mission);
        resume(mission);
        for (final int input : new int[] { 1, 1, 0, 0, 3, 3, 2, 2 })
            act(mission, ShipTask.PILOT, input);
        act(mission, ShipTask.PILOT, 5);
        if (!mission.completed(ShipTask.PILOT))
            throw new AssertionError("Centered, stationary docking must capture");
        tuneSignal(mission);
        if (mission.snapshot().stage() != MoonMission.Stage.NAVIGATION)
            throw new AssertionError("Both docking stations must be verified before navigation");
        act(mission, ShipTask.PILOT, 0);
        act(mission, ShipTask.PILOT, 6);
        if (mission.snapshot().stage() != MoonMission.Stage.NAVIGATION)
            throw new AssertionError("Unreliable guide stars must not verify navigation");
        act(mission, ShipTask.PILOT, 0);
        for (final int star : new int[] { 1, 3, 5 })
            act(mission, ShipTask.PILOT, star);
        act(mission, ShipTask.PILOT, 6);
        verifyPaused(mission);
        resume(mission);
        final int emergencyRevision = mission.revision();
        if (mission.action(ShipTask.ENGINE, emergencyRevision - 1, 1))
            throw new AssertionError("Old packets must not change live loads");
        act(mission, ShipTask.ENGINE, 3);
        if (mission.snapshot().stage() != MoonMission.Stage.EMERGENCY)
            throw new AssertionError("An unsafe load plan must not progress");
        act(mission, ShipTask.ENGINE, 1);
        act(mission, ShipTask.ENGINE, 2);
        act(mission, ShipTask.ENGINE, 3);
        verifyPaused(mission);
        resume(mission);
    }

    private static void powerCartridges(final ShipMission mission) {
        for (int socket = 0; socket < 4; socket++) {
            final int cartridge = mission.target(ShipTask.ENGINE) >> socket * 4 & 15;
            if ((mission.board(ShipTask.ENGINE) >> socket * 4 & 15) != cartridge)
                act(mission, ShipTask.ENGINE, socket * 4 + cartridge - 1);
        }
        if (!mission.completed(ShipTask.ENGINE))
            throw new AssertionError("Correctly rated, unique power cartridges must be verified");
    }

    private static void tuneSignal(final ShipMission mission) {
        final int frequency = mission.target(ShipTask.COMMS);
        for (int pulse = 0; pulse < frequency; pulse++)
            act(mission, ShipTask.COMMS, 0);
        for (int gain = 0; gain < 4; gain++)
            act(mission, ShipTask.COMMS, 2);
        act(mission, ShipTask.COMMS, 4);
        if ((mission.aux(ShipTask.COMMS) & 256) == 0 || mission.completed(ShipTask.COMMS))
            throw new AssertionError("Clipped signals cannot confirm recovery communications");
        act(mission, ShipTask.COMMS, 3);
        act(mission, ShipTask.COMMS, 3);
        if ((mission.aux(ShipTask.COMMS) & 255) != 100)
            throw new AssertionError("The matched, unclipped carrier must achieve lock");
        act(mission, ShipTask.COMMS, 4);
    }

    private static void filter(final ShipMission mission) {
        act(mission, ShipTask.MEDICAL, 5);
        act(mission, ShipTask.MEDICAL, 0);
        if (mission.board(ShipTask.MEDICAL) != 0 || mission.snapshot().stage() != MoonMission.Stage.FILTER)
            throw new AssertionError("An incompatible part cannot precede the adapter seal");
        for (final int part : new int[] { 2, 0, 3, 1 })
            act(mission, ShipTask.MEDICAL, part);
        act(mission, ShipTask.MEDICAL, 4);
        mission.tickSecond();
        mission.tickSecond();
        act(mission, ShipTask.MEDICAL, 5);
        if (mission.snapshot().stage() != MoonMission.Stage.FILTER)
            throw new AssertionError("A pressure test must hold for three seconds before airflow activation");
        mission.tickSecond();
        if (mission.aux(ShipTask.MEDICAL) != 3)
            throw new AssertionError("A sealed adapter must finish its pressure test");
        act(mission, ShipTask.MEDICAL, 5);
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
