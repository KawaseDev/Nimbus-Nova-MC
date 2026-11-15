package cc.nimbusnova.mc.moon;

public final class LunarLandingTest {
    public static void main(String[] args) {
        LunarLanding landing = new LunarLanding();
        if (!landing.preview(LunarLanding.Burn.INSUFFICIENT).contains("pass")) throw new AssertionError();
        reject(() -> landing.capture(LunarLanding.Burn.EXCESSIVE));
        if (landing.orbitalFuel() != 20) throw new AssertionError("A prediction must not consume fuel");
        landing.capture(LunarLanding.Burn.PLANNED);
        reject(() -> landing.chooseSite(LunarLanding.Site.ALTERNATE));
        reject(() -> landing.checkGuidance(true, false));
        landing.checkGuidance(true, true);
        reject(() -> landing.chooseSite(LunarLanding.Site.PRIMARY));
        landing.chooseSite(LunarLanding.Site.ALTERNATE);
        reject(landing::land);
        landing.brakeLateral();
        if (landing.verticalSpeed() != 4 || landing.lateralSpeed() != 1 || landing.descentFuel() != 29)
            throw new AssertionError("Lateral thrust must trade vertical deceleration and consume descent fuel");
        landing.brakeVertical(); landing.brakeVertical(); landing.brakeVertical();
        landing.land();
        reject(landing::collectSample);
        reject(() -> landing.checkSuits(false, true));
        landing.checkSuits(true, true); landing.collectSample();
        if (!landing.sampleCollected() || landing.ascentFuel() != 15)
            throw new AssertionError("Surface work must not consume the separate ascent reserve");
        reject(landing::collectSample);
        landing.launch();
        reject(() -> landing.rendezvous(true, false));
        landing.rendezvous(true, true);
        if (landing.phase() != LunarLanding.Phase.RETURN || landing.ascentFuel() != 5)
            throw new AssertionError("Ascent and rendezvous must use only their dedicated reserve");
        LunarLanding abort = new LunarLanding();
        abort.abort();
        reject(() -> abort.capture(LunarLanding.Burn.PLANNED));
        System.out.println("Lunar landing checks passed: prediction, braking tradeoff, safe site, suits and separate reserves.");
    }
    private static void reject(Runnable action) {
        try { action.run(); throw new AssertionError("Expected rejection"); }
        catch (IllegalStateException expected) { }
    }
}
