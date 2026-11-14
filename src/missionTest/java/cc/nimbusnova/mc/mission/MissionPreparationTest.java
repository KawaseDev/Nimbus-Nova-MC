package cc.nimbusnova.mc.mission;

public final class MissionPreparationTest {
    private MissionPreparationTest() {
        /* w */
    }

    public static void main(final String[] arguments) {
        final MissionPreparation prep = new MissionPreparation();
        assert prep.completed() == 0 && !prep.ready();
        assert !prep.step(MissionPreparation.TaskKind.POWER, 0, 0) : "Briefing must precede equipment work";
        assert !prep.collectSample() && !prep.deliverSample();
        assert prep.briefing() && !prep.briefing() : "Duplicate briefings must not award progress";
        assert prep.collectSample() && !prep.collectSample() : "Sampling can run in parallel without duplicate cargo";
        assert prep.deliverSample() && !prep.deliverSample();
        assert !prep.ready();

        final int initialBoard = prep.puzzleState(MissionPreparation.TaskKind.POWER, false);
        assert !MissionPreparation.powerConnected(initialBoard) : "The initial board must actually need repair";
        assert MissionPreparation.powerConnected(0) : "The upper route must be a valid solution";
        assert MissionPreparation.powerConnected(3 << 6 | 1 << 10) : "The lower route must also work";
        assert !MissionPreparation.powerConnected(1 << 6) : "No power enters a source tile without a west port";
        assert !prep.step(MissionPreparation.TaskKind.POWER, 1, 0) : "Future revisions must be rejected";
        assert prep.step(MissionPreparation.TaskKind.POWER, 0, 0);
        assert !prep.step(MissionPreparation.TaskKind.POWER, 0, 0) : "Replayed rotations must not rotate twice";
        assert !prep.step(MissionPreparation.TaskKind.POWER, 1, 9) : "Out-of-board input must be rejected";
        solvePower(prep, false);
        assert prep.checked(MissionPreparation.TaskKind.POWER) && prep.puzzleRevision(MissionPreparation.TaskKind.POWER, false) == -1;
        assert !prep.step(MissionPreparation.TaskKind.POWER, 0, 0) : "Completed work must not be rewarded twice";

        assert !prep.step(MissionPreparation.TaskKind.OXYGEN, 0, 6) : "Pump activation requires a stable balance";
        for (int action = 0; action < 2; action++) {
            assert prep.step(MissionPreparation.TaskKind.OXYGEN, prep.puzzleRevision(MissionPreparation.TaskKind.OXYGEN, false), 0);
            assert prep.step(MissionPreparation.TaskKind.OXYGEN, prep.puzzleRevision(MissionPreparation.TaskKind.OXYGEN, false), 2);
        }
        final int valves = prep.puzzleState(MissionPreparation.TaskKind.OXYGEN, false);
        assert MissionPreparation.oxygenPressure(valves) == 60 && MissionPreparation.oxygenFlow(valves) == 40;
        assert MissionPreparation.oxygenBalanced(valves);
        assert !MissionPreparation.oxygenBalanced(4) : "Pressure alone must not substitute for enough oxygen flow";
        assert MissionPreparation.oxygenBalanced(1 | 4 << 3 | 2 << 6) : "The pressure puzzle must permit another valid balance";
        for (int tick = 0; tick < MissionPreparation.OXYGEN_STABLE_TICKS - 1; tick++)
            prep.tickOxygen();
        assert !prep.step(MissionPreparation.TaskKind.OXYGEN, prep.puzzleRevision(MissionPreparation.TaskKind.OXYGEN, false), 6)
                : "The crew must wait for pressure to settle";
        assert prep.tickOxygen();
        assert prep.step(MissionPreparation.TaskKind.OXYGEN, prep.puzzleRevision(MissionPreparation.TaskKind.OXYGEN, false), 6);
        assert prep.ready() && prep.completed() == 5;
        assert !prep.step(MissionPreparation.TaskKind.OXYGEN, 0, 6);

        assert !MissionPreparation.powerConnected(prep.puzzleState(MissionPreparation.TaskKind.POWER, true));
        solvePower(prep, true);
        assert prep.puzzleRevision(MissionPreparation.TaskKind.POWER, true) == -1;
        assert !prep.repairStep(0, 0) : "The active repair completes only once";
        final MissionPreparation adjusting = new MissionPreparation();
        adjusting.briefing();
        for (int action = 0; action < 2; action++) {
            adjusting.step(MissionPreparation.TaskKind.OXYGEN, adjusting.puzzleRevision(MissionPreparation.TaskKind.OXYGEN, false), 0);
            adjusting.step(MissionPreparation.TaskKind.OXYGEN, adjusting.puzzleRevision(MissionPreparation.TaskKind.OXYGEN, false), 2);
        }
        for (int tick = 0; tick < 20; tick++)
            adjusting.tickOxygen();
        assert adjusting.puzzleAux(MissionPreparation.TaskKind.OXYGEN, false) >>> 16 == 20;
        assert adjusting.step(MissionPreparation.TaskKind.OXYGEN, adjusting.puzzleRevision(MissionPreparation.TaskKind.OXYGEN, false), 4);
        assert adjusting.puzzleAux(MissionPreparation.TaskKind.OXYGEN, false) >>> 16 == 0 : "Moving a valve resets settling time";
        assert new MissionPreparation().completed() == 0 : "A reset clears preparation and puzzle state";
        System.out.println("Mission puzzle checks passed: two circuit routes, server revisions, valve balance, settling time, parallel tasks and replay rejection.");
    }

    private static void solvePower(final MissionPreparation prep, final boolean relay) {
        for (final int cell : new int[] { 0, 1, 2, 3, 5 }) {
            while (prep.puzzleRevision(MissionPreparation.TaskKind.POWER, relay) >= 0
                    && (prep.puzzleState(MissionPreparation.TaskKind.POWER, relay) >> cell * 2 & 3) != 0) {
                final int revision = prep.puzzleRevision(MissionPreparation.TaskKind.POWER, relay);
                assert relay ? prep.repairStep(revision, cell) : prep.step(MissionPreparation.TaskKind.POWER, revision, cell);
            }
        }
        assert prep.puzzleRevision(MissionPreparation.TaskKind.POWER, relay) == -1;
    }
}
