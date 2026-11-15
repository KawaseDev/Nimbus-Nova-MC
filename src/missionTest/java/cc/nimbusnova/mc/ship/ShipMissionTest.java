package cc.nimbusnova.mc.ship;

import cc.nimbusnova.mc.moon.MoonMission;

public final class ShipMissionTest {
    private ShipMissionTest() {
        /* w */
    }

    public static void main(final String[] arguments) {
        activities();
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
        final double beforeRation = complete.snapshot().water();
        act(complete, ShipTask.GALLEY, 0);
        if (complete.completed(ShipTask.GALLEY) || complete.snapshot().water() != beforeRation)
            throw new AssertionError("Starting a ration must not grant readiness or spend the final shared cost");
        for (final int input : new int[] { 0, 1, 2, 2, 6 })
            act(complete, ShipTask.GALLEY, input);
        if (!complete.completed(ShipTask.GALLEY) || complete.snapshot().water() != beforeRation - 8)
            throw new AssertionError("Only a completed ration activity may spend water and grant its reward");
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
        exploration();
        System.out.println("Ship mission checks passed: five station activities, bounded benefits, unique flight tasks, pauses and failures.");
    }

    private static void exploration() {
        final ShipMission mission = prepared(false, MoonMission.Variant.EXPLORATION);
        mission.start(); resume(mission);
        for (final int input : new int[] { 1, 1, 0, 0, 3, 3, 2, 2, 5 }) act(mission, ShipTask.PILOT, input);
        tuneSignal(mission);
        for (final int star : new int[] { 1, 3, 5, 6 }) act(mission, ShipTask.PILOT, star);
        if (mission.snapshot().stage() != MoonMission.Stage.NAVIGATION) throw new AssertionError("Navigation requires maintenance confirmation");
        tuneSignal(mission);
        if (mission.snapshot().stage() != MoonMission.Stage.ORBIT) throw new AssertionError("Exploration must approach lunar orbit");
        act(mission, ShipTask.PILOT, 0); act(mission, ShipTask.PILOT, 3);
        if (mission.snapshot().stage() != MoonMission.Stage.ORBIT) throw new AssertionError("An invalid orbit prediction cannot be committed");
        act(mission, ShipTask.PILOT, 1); act(mission, ShipTask.PILOT, 3);
        if (mission.snapshot().stage() != MoonMission.Stage.ORBIT) throw new AssertionError("Orbit capture requires a power check");
        act(mission, ShipTask.ENGINE, 0); act(mission, ShipTask.PILOT, 3);
        verifyPaused(mission); resume(mission);
        act(mission, ShipTask.PILOT, 0);
        if ((mission.board(ShipTask.PILOT) & 256) != 0) throw new AssertionError("Guidance requires a verified radar link");
        tuneSignal(mission);
        act(mission, ShipTask.PILOT, 0); act(mission, ShipTask.PILOT, 1);
        act(mission, ShipTask.PILOT, 4);
        if (mission.snapshot().stage() != MoonMission.Stage.DESCENT) throw new AssertionError("Unsafe contact must not land");
        act(mission, ShipTask.PILOT, 2);
        for (int i = 0; i < 3; i++) act(mission, ShipTask.PILOT, 3);
        act(mission, ShipTask.PILOT, 4);
        if (mission.snapshot().stage() != MoonMission.Stage.SURFACE) throw new AssertionError("Safe speeds must permit landing");
        verifyPaused(mission); resume(mission);
        if (mission.collectSurfaceSample() || mission.completed(ShipTask.SCIENCE)) throw new AssertionError("A sample requires a suit check");
        act(mission, ShipTask.MEDICAL, 0);
        if (!mission.collectSurfaceSample()) throw new AssertionError("A suited crew must be able to collect the surface sample");
        act(mission, ShipTask.PILOT, 5); act(mission, ShipTask.PILOT, 6);
        if (mission.snapshot().stage() != MoonMission.Stage.RETURN) throw new AssertionError("Rendezvous must lead to return");
        act(mission, ShipTask.PILOT, 0);
        for (int i = 0; i < 6; i++) mission.tickSecond();
        act(mission, ShipTask.PILOT, 1); tuneSignal(mission);
        if (mission.snapshot().phase() != ShipMission.Phase.SUCCESS) throw new AssertionError("Exploration must support a safe return");
    }

    private static void activities() {
        final ShipActivities tasks = new ShipActivities();
        if (!tasks.action(ShipTask.GALLEY, 6) || tasks.completed(ShipTask.GALLEY))
            throw new AssertionError("An empty pouch rack must not seal");
        for (final int action : new int[] { 0, 0, 1, 2, 2, 6 })
            if (!tasks.action(ShipTask.GALLEY, action)) throw new AssertionError("Ration activity rejected a valid step");
        if (!tasks.completed(ShipTask.GALLEY) || tasks.action(ShipTask.GALLEY, 6))
            throw new AssertionError("A ration is finite and requires a completed metering plan");

        tasks.action(ShipTask.PLANTS, 8);
        if (tasks.completed(ShipTask.PLANTS))
            throw new AssertionError("Dry and overwatered beds must not pass");
        for (final int action : new int[] { 0, 3, 3, 2, 5, 8 })
            if (!tasks.action(ShipTask.PLANTS, action)) throw new AssertionError("Irrigation rejected a valid dose");
        if (!tasks.completed(ShipTask.PLANTS) || tasks.action(ShipTask.PLANTS, 8))
            throw new AssertionError("Coupled irrigation must finish once without infinite rewards");

        tasks.action(ShipTask.MEDICAL, 4);
        if (tasks.board(ShipTask.MEDICAL) != 0)
            throw new AssertionError("An unobserved lead cannot be repaired");
        for (int lead = 0; lead < 3; lead++)
            tasks.action(ShipTask.MEDICAL, lead);
        tasks.action(ShipTask.MEDICAL, 3);
        tasks.action(ShipTask.MEDICAL, 6);
        if (tasks.completed(ShipTask.MEDICAL) || (tasks.board(ShipTask.MEDICAL) & 8) != 0)
            throw new AssertionError("A healthy lead does not fix the failed sensor");
        tasks.action(ShipTask.MEDICAL, 4);
        tasks.action(ShipTask.MEDICAL, 6);
        if (!tasks.completed(ShipTask.MEDICAL) || (tasks.aux(ShipTask.MEDICAL) >> 7 & 127) != 92)
            throw new AssertionError("The replacement must pass its signal self-test");

        tasks.action(ShipTask.SCIENCE, 0);
        tasks.action(ShipTask.SCIENCE, 9);
        if (tasks.completed(ShipTask.SCIENCE))
            throw new AssertionError("Incorrect specimen evidence must not archive");
        for (final int action : new int[] { 2, 3, 7, 9 })
            tasks.action(ShipTask.SCIENCE, action);
        if (!tasks.completed(ShipTask.SCIENCE) || tasks.action(ShipTask.SCIENCE, 9))
            throw new AssertionError("All synthetic specimen records must be classified once");

        tasks.action(ShipTask.STORAGE, 3);
        if (tasks.completed(ShipTask.STORAGE))
            throw new AssertionError("An empty hold cannot pass cargo restraint checks");
        for (int bay = 0; bay < 3; bay++)
            for (int selection = 0; selection < 3; selection++)
                tasks.action(ShipTask.STORAGE, bay);
        tasks.action(ShipTask.STORAGE, 3);
        if (tasks.completed(ShipTask.STORAGE) || ShipActivities.mass(tasks.board(ShipTask.STORAGE)) != 12)
            throw new AssertionError("Overweight cargo must not launch");
        tasks.action(ShipTask.STORAGE, 0);
        tasks.action(ShipTask.STORAGE, 3);
        if (tasks.completed(ShipTask.STORAGE))
            throw new AssertionError("Unbalanced outer bays must not pass");
        tasks.action(ShipTask.STORAGE, 1);
        tasks.action(ShipTask.STORAGE, 2);
        for (int selection = 0; selection < 3; selection++) {
            tasks.action(ShipTask.STORAGE, 0);
            tasks.action(ShipTask.STORAGE, 2);
        }
        tasks.action(ShipTask.STORAGE, 3);
        if (!tasks.completed(ShipTask.STORAGE) || tasks.action(ShipTask.STORAGE, 3))
            throw new AssertionError("Balanced cargo must complete exactly once");
        if (new ShipActivities().action(ShipTask.SCIENCE, 10) || new ShipActivities().action(ShipTask.PLANTS, -1))
            throw new AssertionError("Malformed activity opcodes must be rejected");

        final ShipMission benefits = new ShipMission();
        final int initialParts = benefits.snapshot().parts();
        for (final int action : new int[] { 0, 2, 3 })
            act(benefits, ShipTask.STORAGE, action);
        if (benefits.snapshot().parts() != initialParts + 2 || benefits.action(ShipTask.STORAGE, benefits.revision(), 3))
            throw new AssertionError("Cargo contents must grant their parts only on final secure");
        for (final int action : new int[] { 0, 1, 2, 4, 6 })
            act(benefits, ShipTask.MEDICAL, action);
        final int repairedParts = benefits.snapshot().parts();
        if (repairedParts != initialParts + 1 || benefits.action(ShipTask.MEDICAL, benefits.revision(), 6))
            throw new AssertionError("Instrument completion must consume exactly one finite kit");
    }

    private static ShipMission prepared(final boolean extraParts) {
        return prepared(extraParts, MoonMission.Variant.RESCUE);
    }

    private static ShipMission prepared(final boolean extraParts, final MoonMission.Variant variant) {
        final ShipMission mission = new ShipMission(variant);
        if (mission.action(ShipTask.PILOT, mission.revision(), 0))
            throw new AssertionError("Flight controls are not available before launch");
        mission.tickSecond();
        powerCartridges(mission);
        final int repetitions = extraParts ? 1 : 3;
        for (int repeat = 0; repeat < repetitions; repeat++) {
            act(mission, ShipTask.STORAGE, 0);
            act(mission, ShipTask.STORAGE, 2);
        }
        act(mission, ShipTask.STORAGE, 3);
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
        tuneSignal(mission);
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
