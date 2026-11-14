package cc.nimbusnova.mc.effect;

public final class DustStormProfile {
    private DustStormProfile() {
        /* w */
    }

    public static float advance(final float current, final boolean storm, final boolean paused) {
        if (paused) return current;
        return Math.clamp(current + (storm ? 0.01F : -0.015F), 0, 1);
    }

    public static float opacity(final int age, final int lifetime, final float intensity) {
        if (lifetime <= 0) return 0;
        final float arrival = Math.clamp(age / 12.0F, 0, 1), departure = Math.clamp((lifetime - age) / 24.0F, 0, 1);
        return 0.28F * Math.clamp(intensity, 0, 1) * arrival * departure;
    }
}
