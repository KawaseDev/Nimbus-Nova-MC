package cc.nimbusnova.mc.mission;

public final class MissionStateTest {
    private MissionStateTest() {
        /* w */
    }

    public static void main(final String[] arguments) {
        final MissionState initial = new MissionState();
        assert !initial.replenishOxygen() : "Canisters must not mutate an idle mission";
        initial.tickSecond();
        assert initial.snapshot().elapsedSeconds() == 0 : "Ready missions must not advance";

        boolean rejected = false;
        try {
            initial.setEquipment(MissionState.Equipment.OXYGEN, false);
        } catch (final IllegalStateException expected) {
            rejected = true;
        }

        assert rejected : "Equipment changes before start must be rejected";
        initial.start();
        initial.tickSecond();
        assert Math.abs(initial.snapshot().battery() - 79.85) < 0.00001 : "Power balance uses generation minus demand";
        assert Math.abs(initial.snapshot().oxygen() - 75.15) < 0.00001 : "Production and crew consumption both apply";
        assert initial.replenishOxygen();
        assert initial.snapshot().oxygen() == 100 : "Canisters cap at reserve capacity";
        assert !initial.replenishOxygen() : "A full reserve must not consume a canister";
        initial.pause();
        final MissionState.Snapshot paused = initial.snapshot();
        for (int second = 0; second < 50; second++)
            initial.tickSecond();

        assert paused.equals(initial.snapshot()) : "Pause must freeze reserves, time and research";
        initial.resume();
        initial.setEquipment(MissionState.Equipment.RESEARCH, false);
        initial.setEquipment(MissionState.Equipment.RESEARCH, false);
        assert initial.snapshot().decisions() == 2 : "Repeating the same command must not count a second decision";
        initial.abort();
        final MissionState.Snapshot aborted = initial.snapshot();
        initial.tickSecond();
        assert aborted.equals(initial.snapshot()) : "Ended missions must stay ended";

        final MissionState unmanaged = new MissionState();
        unmanaged.start();
        for (int second = 0; second < MissionState.DURATION_SECONDS; second++)
            unmanaged.tickSecond();

        assert unmanaged.snapshot().phase() == MissionState.Phase.FAILED : "Running all equipment through dust must fail";
        assert unmanaged.snapshot().battery() == 0 : "Battery cannot become negative";

        final MissionState survivalOnly = new MissionState();
        survivalOnly.start();
        survivalOnly.setEquipment(MissionState.Equipment.RESEARCH, false);
        for (int second = 0; second < MissionState.DURATION_SECONDS; second++)
            survivalOnly.tickSecond();

        assert survivalOnly.snapshot().phase() == MissionState.Phase.PARTIAL : "Disabling research forever is not full success";
        assert survivalOnly.snapshot().researchSeconds() == 0;

        final MissionState managed = new MissionState();
        managed.start();
        for (int second = 0; second < MissionState.DURATION_SECONDS; second++) {
            if (second == 60)
                managed.setEquipment(MissionState.Equipment.RESEARCH, false);

            if (second == 195)
                managed.setEquipment(MissionState.Equipment.RESEARCH, true);

            managed.tickSecond();
            if (managed.snapshot().elapsedSeconds() == MissionState.STORM_START_SECONDS)
                assert managed.snapshot().storm() && managed.snapshot().generation() == 0.2 : "Storm must reduce generation at its boundary";

            if (managed.snapshot().elapsedSeconds() == MissionState.STORM_END_SECONDS)
                assert !managed.snapshot().storm() && managed.snapshot().generation() == 1.1 : "Generation must recover at storm end";
        }

        assert managed.snapshot().phase() == MissionState.Phase.SUCCESS : "A balanced allocation must be winnable";
        assert managed.snapshot().researchSeconds() >= MissionState.RESEARCH_TARGET_SECONDS;
        assert managed.snapshot().oxygen() <= 100 && managed.snapshot().battery() <= 100 : "Reserves cannot exceed capacity";

        final MissionState noOxygen = new MissionState();
        noOxygen.start();
        noOxygen.setEquipment(MissionState.Equipment.OXYGEN, false);
        for (int second = 0; second < MissionState.DURATION_SECONDS; second++)
            noOxygen.tickSecond();

        assert noOxygen.snapshot().phase() == MissionState.Phase.FAILED;
        assert noOxygen.snapshot().oxygen() == 0 : "Oxygen depletion must fail without becoming negative";
        assert CrewRole.POWER.canControl(MissionState.Equipment.RESEARCH);
        assert !CrewRole.POWER.canControl(MissionState.Equipment.OXYGEN);
        assert CrewRole.LIFE_SUPPORT.canControl(MissionState.Equipment.OXYGEN);
        assert !CrewRole.OBSERVER.canControl(MissionState.Equipment.RESEARCH);
        System.out.println("Mission self-check passed: resource balance, phases, dust, outcomes, idempotency and roles.");
    }
}
