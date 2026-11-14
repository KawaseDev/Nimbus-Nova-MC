package cc.nimbusnova.mc.mission;

/**
 * Solar supply for "The Darkening Sky". The storm peak comes from a real Opportunity storm ({@link DustStorm});
 * the timing and rates are game assumptions. Sources and the split between observation and assumption are in CREDITS.md.
 */
public final class DustStormProfile {
    // Game assumptions: about ten sols of dimming compressed into one minute, then a partial recovery for playability.
    public static final int PEAK_START_SECONDS = 135;
    public static final double RECOVERY_SOLAR_FACTOR = 0.5;
    public static final double SOLAR_OUTPUT = 0.95;
    // Game assumption inspired by nuclear-powered Curiosity, which kept working through the 2018 storm.
    public static final double BACKUP_OUTPUT = 0.15;
    public static final int FORECAST_SECONDS = 30;

    private DustStormProfile() {
        /* w */
    }

    public static double solarFactor(final DustStorm storm, final int elapsedSeconds) {
        if (elapsedSeconds < MissionState.STORM_START_SECONDS) return 1;
        if (elapsedSeconds < PEAK_START_SECONDS)
            return blend(1, storm.peakFactor(), elapsedSeconds - MissionState.STORM_START_SECONDS,
                    PEAK_START_SECONDS - MissionState.STORM_START_SECONDS);

        if (elapsedSeconds < MissionState.STORM_END_SECONDS) return storm.peakFactor();
        return blend(storm.peakFactor(), RECOVERY_SOLAR_FACTOR, elapsedSeconds - MissionState.STORM_END_SECONDS,
                MissionState.DURATION_SECONDS - MissionState.STORM_END_SECONDS);
    }

    /** The weather officer's look-ahead: sunlight expected {@link #FORECAST_SECONDS} from now. */
    public static double forecastFactor(final DustStorm storm, final int elapsedSeconds) {
        return solarFactor(storm, Math.min(MissionState.DURATION_SECONDS, elapsedSeconds + FORECAST_SECONDS));
    }

    public static String forecast(final DustStorm storm, final int elapsedSeconds) {
        final long now = Math.round(solarFactor(storm, elapsedSeconds) * 100);
        final long later = Math.round(forecastFactor(storm, elapsedSeconds) * 100);
        final String trend = later < now ? "falling" : later > now ? "rising" : "steady";
        return "Sun " + now + "% now, " + later + "% in " + FORECAST_SECONDS + " s (" + trend + ").";
    }

    public static double generation(final DustStorm storm, final int elapsedSeconds) {
        return BACKUP_OUTPUT + SOLAR_OUTPUT * solarFactor(storm, elapsedSeconds);
    }

    private static double blend(final double from, final double to, final int step, final int steps) {
        return from + (to - from) * Math.min(1, (double) step / steps);
    }
}
