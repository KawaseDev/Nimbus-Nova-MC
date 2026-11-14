package cc.nimbusnova.mc.mission;

/**
 * Real dust storms the Opportunity rover lived through. Each one sets how much sunlight is left at the storm peak.
 * Values and sources are listed in CREDITS.md.
 */
public enum DustStorm {
    OPPORTUNITY_2018("2018", "June 2018 global dust storm", 645, 21, "June 10, 2018", "tau 10.5",
            "Opportunity went silent on June 10, 2018 and never woke up. "
                    + "Curiosity, powered by a nuclear generator, kept working through the same storm."),
    OPPORTUNITY_2007("2007", "July 2007 dust storm", 700, 128, "July 18, 2007", "tau above 5.5",
            "Opportunity's team skipped contact with Earth to save energy, and the rover survived the 2007 storm.");

    public final String id, label, peakDate, opacity, realOutcome;
    // Observed solar energy per sol before the storm and at its lowest point, in watt-hours.
    public final double clearSkyWattHours, peakWattHours;

    DustStorm(final String id, final String label, final double clearSkyWattHours, final double peakWattHours,
              final String peakDate, final String opacity, final String realOutcome) {
        this.id = id;
        this.label = label;
        this.clearSkyWattHours = clearSkyWattHours;
        this.peakWattHours = peakWattHours;
        this.peakDate = peakDate;
        this.opacity = opacity;
        this.realOutcome = realOutcome;
    }

    /** Share of clear-sky sunlight left at the storm peak, from the observed energy drop. */
    public double peakFactor() {
        return peakWattHours / clearSkyWattHours;
    }

    public long peakPercent() {
        return Math.round(peakFactor() * 100);
    }

    public String summary() {
        return "Opportunity, " + label + ": " + (int) clearSkyWattHours + " Wh of solar energy per day before the storm, "
                + (int) peakWattHours + " Wh on " + peakDate + " (dust opacity " + opacity + ").";
    }

    public static DustStorm byId(final String id) {
        for (final DustStorm storm : values())
            if (storm.id.equals(id)) return storm;

        throw new IllegalArgumentException("Unknown dust storm: " + id);
    }
}
