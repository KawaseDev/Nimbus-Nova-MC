package cc.nimbusnova.mc.scenario;

public final class ScenarioMissionTest {
    private ScenarioMissionTest() {
        /* w */
    }
    public static void main(final String[] arguments) {
        final ScenarioMission shelter = new ScenarioMission("shelter");
        shelter.shields = 7;
        for (int bearing = 0; bearing < 3; bearing++) act(shelter, 0, bearing);
        shelter.start();
        sos(shelter);
        assert !shelter.action(2, 0, shelter.revision) && shelter.signal == 9 : "A completed beacon cannot be undone by duplicate input";
        for (int second = 0; second < 150; second++) shelter.tick(true);
        assert shelter.phase == ScenarioMission.Phase.SUCCESS && shelter.dose == 0;
        final ScenarioMission exposed = new ScenarioMission("shelter");
        exposed.shields = 7; exposed.scans = 7; exposed.start();
        for (int second = 0; second < 100; second++) exposed.tick(false);
        assert exposed.phase == ScenarioMission.Phase.FAILED;
        final ScenarioMission night = new ScenarioMission("long_night");
        act(night, 0, 2);
        night.start();
        for (int second = 0; second < 45; second++) night.tick(false);
        assert night.cableBroken && night.signal == 0;
        night.repair();
        sos(night);
        for (int second = 45; second < 150; second++) night.tick(false);
        assert night.phase == ScenarioMission.Phase.SUCCESS && night.battery > 20;
        final ScenarioMission reckless = new ScenarioMission("long_night");
        reckless.start();
        for (int second = 0; second < 150; second++) reckless.tick(false);
        assert reckless.phase == ScenarioMission.Phase.FAILED;
        final ScenarioMission garden = new ScenarioMission("greenhouse");
        garden.start();
        for (int second = 0; second < 150; second++) {
            for (int bed = 0; bed < 3; bed++) {
                final boolean open = (garden.valves & 1 << bed) != 0;
                if (garden.moisture[bed] < 48 && !open || garden.moisture[bed] > 62 && open) act(garden, 1, bed);
            }
            garden.tick(false);
            if (garden.pipeBroken) garden.repair();
            if (garden.elapsed >= 80 && garden.elapsed < 140)
                for (int bed = 0; bed < 3; bed++)
                    if ((garden.harvest & 1 << bed) == 0 && garden.moisture[bed] >= 40 && garden.moisture[bed] <= 70 && garden.rootAir[bed] >= 60)
                        act(garden, 2, bed);
        }
        assert garden.phase == ScenarioMission.Phase.SUCCESS && garden.harvest == 7 && garden.water > 0;
        final int revision = garden.revision;
        assert !garden.action(1, 0, revision) : "Ended activities reject resource changes";
        final ScenarioMission paused = new ScenarioMission("greenhouse");
        paused.start(); paused.pause();
        for (int second = 0; second < 100; second++) paused.tick(false);
        assert paused.elapsed == 0 && paused.water == 100;
        paused.resume();
        assert !paused.action(1, 0, paused.revision - 1) : "Stale actions are rejected";
        System.out.println("Scenario checks passed: three complete mission loops, exposure, power loss, repair, garden care, pauses and stale actions.");
    }
    private static void act(final ScenarioMission mission, final int station, final int value) {
        if (!mission.action(station, value, mission.revision)) throw new AssertionError("Rejected valid scenario action");
    }
    private static void sos(final ScenarioMission mission) {
        for (final int pulse : new int[] { 0, 0, 0, 1, 1, 1, 0, 0, 0 }) act(mission, 2, pulse);
    }
}
