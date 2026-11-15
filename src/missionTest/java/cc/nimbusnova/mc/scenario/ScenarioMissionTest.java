package cc.nimbusnova.mc.scenario;

public final class ScenarioMissionTest {
    private ScenarioMissionTest() {
        /* w */
    }
    public static void main(final String[] arguments) {
        final ScenarioMission shelter = preparedShelter();
        shelter.start();
        sos(shelter);
        for (int second = 0; second < 150; second++) {
            if (shelter.elapsed == 54) act(shelter, 0, 3);
            final boolean surveying = shelter.scanActive;
            shelter.tick(!surveying, surveying);
        }
        assert shelter.phase == ScenarioMission.Phase.SUCCESS && shelter.observation && shelter.dose == 9;
        final ScenarioMission passive = preparedShelter();
        passive.start(); sos(passive);
        for (int second = 0; second < 150; second++) passive.tick(true);
        assert passive.phase == ScenarioMission.Phase.PARTIAL : "Survival without valuable live data is partial";
        final ScenarioMission interrupted = preparedShelter();
        interrupted.start();
        for (int second = 0; second < 50; second++) interrupted.tick(true);
        act(interrupted, 0, 3);
        interrupted.tick(false, true);
        interrupted.tick(true, false);
        assert !interrupted.observation && interrupted.eventScanSeconds == 0;
        final ScenarioMission exposed = preparedShelter();
        exposed.start();
        for (int second = 0; second < 100; second++) exposed.tick(false);
        assert exposed.phase == ScenarioMission.Phase.FAILED;
        final ScenarioMission night = new ScenarioMission("long_night");
        night.lifeFlow = 2;
        act(night, 1, 0);
        night.start();
        for (int second = 0; second < 30; second++) night.tick(false);
        assert night.labSeconds == 30;
        act(night, 0, 2);
        for (int second = 30; second < 45; second++) night.tick(false);
        assert night.cableBroken;
        night.pause();
        assert !night.repair() && night.cableBroken : "Paused cable repairs cannot consume a kit";
        night.resume();
        assert night.repair();
        sos(night); act(night, 0, 3);
        for (int second = 45; second < 150; second++) night.tick(false);
        assert night.phase == ScenarioMission.Phase.SUCCESS && night.battery > 20;
        final ScenarioMission noScience = new ScenarioMission("long_night");
        noScience.lifeFlow = 2; act(noScience, 0, 2); noScience.start();
        for (int second = 0; second < 45; second++) noScience.tick(false);
        noScience.repair(); sos(noScience); act(noScience, 0, 3);
        for (int second = 45; second < 150; second++) noScience.tick(false);
        assert noScience.phase == ScenarioMission.Phase.PARTIAL : "Keeping the lab off forever cannot earn full success";
        final ScenarioMission reckless = new ScenarioMission("long_night");
        reckless.lifeFlow = 2; reckless.start();
        for (int second = 0; second < 150; second++) reckless.tick(false);
        assert reckless.phase == ScenarioMission.Phase.FAILED;
        final ScenarioMission garden = new ScenarioMission("greenhouse");
        act(garden, 0, 3);
        for (int bed = 0; bed < 3; bed++) act(garden, 0, bed);
        act(garden, 2, 3); garden.start();
        for (int second = 0; second < 150; second++) {
            for (int bed = 0; bed < 3; bed++) {
                final boolean open = (garden.valves & 1 << bed) != 0;
                if (garden.moisture[bed] < 48 && !open || garden.moisture[bed] > 62 && open) act(garden, 1, bed);
            }
            if (garden.cabinHumidity < 55 && garden.extractionVent || garden.cabinHumidity > 65 && !garden.extractionVent) act(garden, 2, 3);
            garden.tick(false);
            if (garden.pipeBroken) {
                garden.pause();
                assert !garden.repair() && garden.pipeBroken : "Paused pipe repairs keep the broken state";
                garden.resume();
                assert garden.repair();
            }
            if (garden.elapsed >= 80 && garden.elapsed < 140)
                for (int bed = 0; bed < 3; bed++)
                    if ((garden.harvest & 1 << bed) == 0 && garden.moisture[bed] >= 40 && garden.moisture[bed] <= 70
                            && garden.rootAir[bed] >= 60 && garden.cabinHumidity >= 40 && garden.cabinHumidity <= 70)
                        act(garden, 2, bed);
        }
        assert garden.phase == ScenarioMission.Phase.SUCCESS && garden.harvest == 7 && garden.water > 0;
        assert !garden.action(1, 0, garden.revision) && !garden.repair() : "Ended tasks cannot mutate inventory prerequisites";
        final ScenarioMission paused = new ScenarioMission("greenhouse");
        act(paused, 0, 3); paused.start(); paused.pause();
        for (int second = 0; second < 100; second++) paused.tick(false);
        assert paused.elapsed == 0 && paused.water == 100;
        paused.resume();
        assert !paused.action(1, 0, paused.revision - 1);
        System.out.println("Scenario checks passed: risky live data, passive partial outcomes, useful lab budget, five-role equipment, atomic paused repairs and three complete loops.");
    }
    private static ScenarioMission preparedShelter() {
        final ScenarioMission mission = new ScenarioMission("shelter");
        mission.shields = 7; mission.lifeFlow = 2;
        for (int bearing = 0; bearing < 3; bearing++) act(mission, 0, bearing);
        return mission;
    }
    private static void act(final ScenarioMission mission, final int station, final int value) {
        if (!mission.action(station, value, mission.revision)) throw new AssertionError("Rejected valid scenario action " + mission.id + ":" + station + ":" + value);
    }
    private static void sos(final ScenarioMission mission) {
        for (final int pulse : new int[] { 0, 0, 0, 1, 1, 1, 0, 0, 0 }) act(mission, 2, pulse);
    }
}
