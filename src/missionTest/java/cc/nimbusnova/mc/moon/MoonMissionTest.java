package cc.nimbusnova.mc.moon;

public final class MoonMissionTest {
    public static void main(String[] args) {
        for (MoonMission.Variant variant : MoonMission.Variant.values()) {
            MoonMission mission = new MoonMission(variant);
            expectFailure(mission::advance);
            boolean visitedSurface = false, visitedFilter = false;
            while (mission.stage() != MoonMission.Stage.COMPLETE) {
                visitedSurface |= mission.stage() == MoonMission.Stage.SURFACE;
                visitedFilter |= mission.stage() == MoonMission.Stage.FILTER;
                if (mission.paused()) {
                    int battery = mission.battery(), fuel = mission.fuel();
                    expectFailure(mission::resume);
                    expectFailure(() -> mission.confirm(MoonMission.Role.PILOT));
                    assert mission.battery() == battery && mission.fuel() == fuel;
                    mission.acknowledge();
                    mission.resume();
                }
                for (MoonMission.Role role : MoonMission.required(mission.stage())) mission.confirm(role);
                expectFailure(mission::advance);
                MoonMission.Decision decision = MoonMission.decision(mission.stage());
                MoonMission.Role wrong = decision.role() == MoonMission.Role.POWER ? MoonMission.Role.PILOT : MoonMission.Role.POWER;
                expectFailure(() -> mission.choose(wrong, true));
                mission.choose(decision.role(), true);
                mission.advance();
            }
            assert visitedSurface == (variant == MoonMission.Variant.EXPLORATION);
            assert visitedFilter == (variant == MoonMission.Variant.RESCUE);
            assert mission.fuel() < 100 && mission.battery() <= 100 && mission.parts() >= 0;
            expectFailure(mission::advance);
        }
        MoonMission manuallyPaused = new MoonMission(MoonMission.Variant.EXPLORATION);
        manuallyPaused.pause();
        manuallyPaused.resume();
        assert !manuallyPaused.paused();
        MoonMission alternative = new MoonMission(MoonMission.Variant.RESCUE);
        alternative.choose(MoonMission.Role.SUPPLIES, false);
        assert alternative.parts() == 5 && alternative.decisionMade();
        for (MoonMission.Role role : MoonMission.required(alternative.stage())) alternative.confirm(role);
        alternative.advance();
        alternative.acknowledge();
        alternative.resume();
        alternative.choose(MoonMission.Role.MAINTENANCE, false);
        assert alternative.fuel() == 97 && !alternative.decisionMade();
        alternative.choose(MoonMission.Role.MAINTENANCE, true);
        assert alternative.decisionMade();
        System.out.println("Moon mission checks passed: both routes, crew reports and educational pauses.");
    }
    private static void expectFailure(Runnable action) {
        try { action.run(); throw new AssertionError("Expected rejection"); }
        catch (IllegalStateException expected) { }
    }
}
