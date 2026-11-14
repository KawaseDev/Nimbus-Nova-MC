package cc.nimbusnova.mc.effect;

public final class DustStormProfileTest {
    private DustStormProfileTest() {
        /* w */
    }

    public static void main(final String[] arguments) {
        float intensity = 0;
        for (int tick = 0; tick < 110; tick++)
            intensity = DustStormProfile.advance(intensity, true, false);

        assert intensity == 1 : "Storm intensity must saturate after its smooth arrival";
        assert DustStormProfile.advance(intensity, false, true) == intensity : "A paused mission must freeze storm intensity";
        assert DustStormProfile.opacity(0, 100, 1) == 0 : "A new cloud must fade in";
        assert DustStormProfile.opacity(100, 100, 1) == 0 : "A cloud must fade out before removal";
        assert DustStormProfile.opacity(40, 100, 1) > 0;
        assert DustStormProfile.opacity(40, 100, 0) == 0 : "Clear weather must hide residual particles";
        for (int tick = 0; tick < 80; tick++)
            intensity = DustStormProfile.advance(intensity, false, false);

        assert intensity == 0 : "Clear conditions must fully remove the effect";
        System.out.println("Dust profile check passed: bounded fade, pause and cloud opacity.");
    }
}
