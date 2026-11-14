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
        assert Math.abs(initial.snapshot().oxygen() - 83.15) < 0.00001 : "A canister adds a small emergency buffer";
        assert initial.replenishOxygen() && initial.snapshot().canistersLeft() == 0;
        assert !initial.replenishOxygen() : "Used-up canisters must not add oxygen";
        initial.pause();
        final MissionState.Snapshot paused = initial.snapshot();
        for (int second = 0; second < 50; second++)
            initial.tickSecond();

        assert paused.equals(initial.snapshot()) : "Pause must freeze reserves, time and research";
        initial.resume();
        initial.setEquipment(MissionState.Equipment.RESEARCH, false);
        initial.setEquipment(MissionState.Equipment.RESEARCH, false);
        assert initial.snapshot().decisions() == 3 :"Repeating the same command must not count a second decision";
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

        final DustStorm storm2018 = DustStorm.OPPORTUNITY_2018;
        final double peak = DustStormProfile.BACKUP_OUTPUT + DustStormProfile.SOLAR_OUTPUT * 21 / 645;
        assert storm2018.peakPercent() == 3 && DustStorm.OPPORTUNITY_2007.peakPercent() == 18 : "Peaks come from observed Wh drops";
        assert DustStormProfile.solarFactor(storm2018, 0) == 1 && DustStormProfile.solarFactor(storm2018, MissionState.DURATION_SECONDS)
                == DustStormProfile.RECOVERY_SOLAR_FACTOR : "Solar curve starts clear and ends at the authored recovery";

        final MissionState greedy = new MissionState();
        greedy.start();
        for (int second = 0; second < MissionState.DURATION_SECONDS; second++) {
            if (greedy.relayFault())
                greedy.repairRelay();

            if (second == 110)
                greedy.setEquipment(MissionState.Equipment.RESEARCH, false);

            if (second == MissionState.STORM_END_SECONDS)
                greedy.setEquipment(MissionState.Equipment.RESEARCH, true);

            greedy.tickSecond();
        }

        assert greedy.snapshot().phase() == MissionState.Phase.FAILED : "Late cuts plus an early restart must drain the battery";
        assert greedy.snapshot().phase().ended() && !MissionState.Phase.PAUSED.ended();
        assert greedy.snapshot().lowestBattery() == 0 && greedy.snapshot().firstResearchCutSecond() == 110;
        final String greedyStory = String.join(" ", MissionDebrief.consequences(greedy.snapshot()));
        assert greedyStory.contains("battery ran out") && greedyStory.contains("research still switched on")
                && greedyStory.contains("01:50 while the sky was darkening") : greedyStory;

        final MissionState managed = new MissionState();
        managed.start();
        for (int second = 0; second < MissionState.DURATION_SECONDS; second++) {
            if (managed.relayFault())
                managed.repairRelay();

            if (second == 60)
                managed.setEquipment(MissionState.Equipment.RESEARCH, false);

            if (second == 195)
                managed.setEquipment(MissionState.Equipment.RESEARCH, true);

            managed.tickSecond();
            final int elapsed = managed.snapshot().elapsedSeconds();
            if (elapsed == MissionState.STORM_START_SECONDS)
                assert managed.snapshot().storm() && Math.abs(managed.snapshot().generation() - 1.1) < 1e-9 : "Dimming starts from clear sky";

            if (elapsed == MissionState.STORM_START_SECONDS + 30)
                assert managed.snapshot().generation() < 1.1 && managed.snapshot().generation() > peak : "Dust dims the sun gradually";

            if (elapsed == DustStormProfile.PEAK_START_SECONDS || elapsed == MissionState.STORM_END_SECONDS)
                assert Math.abs(managed.snapshot().generation() - peak) < 1e-9 : "The peak holds Opportunity's observed 21/645 ratio";

            if (elapsed == MissionState.STORM_END_SECONDS + 1)
                assert !managed.snapshot().storm() && managed.snapshot().generation() > peak : "Generation recovers after the storm";
        }

        assert managed.snapshot().phase() == MissionState.Phase.SUCCESS : "A balanced allocation must be winnable";
        assert managed.snapshot().researchSeconds() >= MissionState.RESEARCH_TARGET_SECONDS;
        assert managed.snapshot().lowestBattery() > 0 && managed.snapshot().lowestBattery() < 80 : "Debrief records the lowest reserve";
        assert String.join(" ", MissionDebrief.consequences(managed.snapshot())).contains("01:00 before the dust arrived");
        assert managed.snapshot().oxygen() <= 100 && managed.snapshot().battery() <= 100 : "Reserves cannot exceed capacity";

        final MissionState canisters = new MissionState();
        canisters.start();
        canisters.setEquipment(MissionState.Equipment.OXYGEN, false);
        int used = 0;
        for (int second = 0; second < MissionState.DURATION_SECONDS; second++) {
            canisters.tickSecond();
            if (canisters.snapshot().oxygen() < 50 && canisters.replenishOxygen())
                used++;
        }

        assert used == MissionState.OXYGEN_CANISTERS && canisters.snapshot().canistersLeft() == 0 : "Canisters are limited";
        assert canisters.snapshot().phase() == MissionState.Phase.FAILED : "Canisters alone cannot replace oxygen production";

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
        assert CrewRole.WEATHER.seesWeather() && !CrewRole.POWER.seesWeather() : "Only the weather officer reads the sky";
        assert !CrewRole.WEATHER.canControl(MissionState.Equipment.RESEARCH) && !CrewRole.WEATHER.canControl(MissionState.Equipment.OXYGEN);
        assert DustStormProfile.forecast(storm2018, MissionState.STORM_START_SECONDS).contains("falling") : "Forecast warns before the peak";
        assert DustStormProfile.forecast(storm2018, 0).contains("steady");

        // The milder real 2007 storm is easier but still punishes running everything.
        final MissionState mild = new MissionState(DustStorm.OPPORTUNITY_2007);
        mild.start();
        for (int second = 0; second < MissionState.DURATION_SECONDS; second++)
            mild.tickSecond();

        assert mild.snapshot().phase() == MissionState.Phase.FAILED : "Even the 2007 storm cannot power everything";
        final MissionState mildManaged = new MissionState(DustStorm.OPPORTUNITY_2007);
        mildManaged.start();
        for (int second = 0; second < MissionState.DURATION_SECONDS; second++) {
            if (mildManaged.relayFault())
                mildManaged.repairRelay();

            if (second == 110)
                mildManaged.setEquipment(MissionState.Equipment.RESEARCH, false);

            if (second == MissionState.STORM_END_SECONDS)
                mildManaged.setEquipment(MissionState.Equipment.RESEARCH, true);

            mildManaged.tickSecond();
        }

        assert mildManaged.snapshot().phase() == MissionState.Phase.SUCCESS : "A late cut that fails in 2018 survives the 2007 storm";
        assert MissionDebrief.realStory(mildManaged.snapshot()).contains("survived");
        assert DustStorm.byId("2018") == storm2018;
        final MissionState relay = new MissionState();
        relay.start();
        for (int second = 0; second < 80; second++)
            relay.tickSecond();

        assert relay.relayFault() : "The authored relay fault must occur at 80 seconds";
        final int researchBeforeFault = relay.snapshot().researchSeconds();
        relay.tickSecond();
        assert relay.snapshot().researchSeconds() == researchBeforeFault : "Damaged equipment must not produce science";
        rejected = false;
        try {
            relay.setEquipment(MissionState.Equipment.RESEARCH, true);
        } catch (final IllegalStateException expected) {
            rejected = true;
        }
        assert rejected : "The crew must repair the relay before re-enabling research";
        relay.setEquipment(MissionState.Equipment.RESEARCH, false);
        relay.repairRelay();
        assert !relay.relayFault() && !relay.snapshot().researchOnline() : "Repair restores availability without choosing the crew budget";
        relay.setEquipment(MissionState.Equipment.RESEARCH, true);
        relay.tickSecond();
        assert relay.snapshot().researchSeconds() > researchBeforeFault;

        final MissionState largerCrew = new MissionState();
        largerCrew.start();
        for (int second = 0; second < MissionState.SOLAR_START_SECONDS; second++)
            largerCrew.tickSecond();

        largerCrew.recordExposure(6);
        assert largerCrew.snapshot().radiationDose() == MissionState.DOSE_PER_EXPOSED_SECOND
                : "Adding classmates must not multiply the shared radiation penalty";
        assert MissionState.SOLAR_START_SECONDS - MissionState.SOLAR_WARNING_SECONDS >= 30
                : "Children need a clear travel window before the solar event";

        final MissionState solar = new MissionState();
        solar.start();
        for (int second = 0; second < MissionState.SOLAR_START_SECONDS - 1; second++) {
            solar.tickSecond();
            solar.recordExposure(3);
        }

        assert solar.snapshot().radiationDose() == 0 : "No dose before the particles arrive";
        assert MissionState.solarWarning(solar.snapshot().elapsedSeconds()) : "The alert gives warning before the particles arrive";
        for (int second = 0; second < 6; second++) {
            solar.tickSecond();
            solar.recordExposure(1);
        }

        assert solar.snapshot().radiationDose() == 30 && solar.snapshot().phase() == MissionState.Phase.ACTIVE : "Dose builds per exposed crew-second";
        assert String.join(" ", MissionDebrief.consequences(solar.snapshot())).contains("6 seconds outside the shelter");
        for (int second = 0; second < 14; second++) {
            solar.tickSecond();
            solar.recordExposure(1);
        }

        assert solar.snapshot().phase() == MissionState.Phase.FAILED : "Twenty unprotected crew-seconds reach the limit";
        assert String.join(" ", MissionDebrief.consequences(solar.snapshot())).contains("radiation limit");

        final MissionState sheltered = new MissionState();
        sheltered.start();
        for (int second = 0; second < MissionState.SOLAR_END_SECONDS + 5; second++) {
            sheltered.tickSecond();
            sheltered.recordExposure(MissionState.solarStorm(sheltered.snapshot().elapsedSeconds()) ? 0 : 4);
        }

        assert sheltered.snapshot().radiationDose() == 0 && sheltered.snapshot().phase() == MissionState.Phase.ACTIVE
                : "Exposure outside the storm window costs nothing";

        System.out.println("Mission self-check passed: resource balance, phases, dust, outcomes, idempotency and roles.");
    }
}
