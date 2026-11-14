package cc.nimbusnova.mc.mission;

/**
 * Solar supply for "The Darkening Sky", shaped by NASA's record of the Opportunity rover in the June 2018 Mars dust storm.
 * Sources and the split between observation and game assumption are listed in CREDITS.md.
 */
public final class DustStormProfile {
    // Observed: Opportunity solar energy per sol before the storm (about June 1, 2018).
    public static final double CLEAR_SKY_WATT_HOURS = 645;
    // Observed: energy and atmospheric opacity (tau) on June 10, 2018, sol 5111, the rover's last contact.
    public static final double PEAK_STORM_WATT_HOURS = 21;
    public static final double PEAK_STORM_TAU = 10.5;
    // Derived: the storm left about 3% of the clear-sky solar energy.
    public static final double PEAK_SOLAR_FACTOR = PEAK_STORM_WATT_HOURS / CLEAR_SKY_WATT_HOURS;

    // Game assumptions: ten sols of dimming compressed into one minute, then a partial recovery for playability.
    // Opportunity itself never recovered from this storm.
    public static final int PEAK_START_SECONDS = 135;
    public static final double RECOVERY_SOLAR_FACTOR = 0.5;
    public static final double SOLAR_OUTPUT = 0.95;
    // Game assumption inspired by nuclear-powered Curiosity, which kept working through the same storm.
    public static final double BACKUP_OUTPUT = 0.15;

    private DustStormProfile() {
        /* w */
    }

    public static double solarFactor(final int elapsedSeconds) {
        if (elapsedSeconds < MissionState.STORM_START_SECONDS) return 1;
        if (elapsedSeconds < PEAK_START_SECONDS)
            return blend(1, PEAK_SOLAR_FACTOR, elapsedSeconds - MissionState.STORM_START_SECONDS,
                    PEAK_START_SECONDS - MissionState.STORM_START_SECONDS);

        if (elapsedSeconds < MissionState.STORM_END_SECONDS) return PEAK_SOLAR_FACTOR;
        return blend(PEAK_SOLAR_FACTOR, RECOVERY_SOLAR_FACTOR, elapsedSeconds - MissionState.STORM_END_SECONDS,
                MissionState.DURATION_SECONDS - MissionState.STORM_END_SECONDS);
    }

    public static final int FORECAST_SECONDS = 30;

    /** The weather officer's look-ahead: sunlight expected {@link #FORECAST_SECONDS} from now. */
    public static double forecastFactor(final int elapsedSeconds) {
        return solarFactor(Math.min(MissionState.DURATION_SECONDS, elapsedSeconds + FORECAST_SECONDS));
    }

    public static String forecast(final int elapsedSeconds) {
        final long now = Math.round(solarFactor(elapsedSeconds) * 100);
        final long later = Math.round(forecastFactor(elapsedSeconds) * 100);
        final String trend = later < now ? "falling" : later > now ? "rising" : "steady";
        return "Sun " + now + "% now, " + later + "% in " + FORECAST_SECONDS + " s (" + trend + ").";
    }

    public static double generation(final int elapsedSeconds) {
        return BACKUP_OUTPUT + SOLAR_OUTPUT * solarFactor(elapsedSeconds);
    }

    private static double blend(final double from, final double to, final int step, final int steps) {
        return from + (to - from) * Math.min(1, (double) step / steps);
    }
}
