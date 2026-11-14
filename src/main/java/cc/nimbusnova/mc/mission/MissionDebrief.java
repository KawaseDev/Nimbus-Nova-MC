package cc.nimbusnova.mc.mission;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * End-of-mission summary for the crew and teacher. It reports what was measured and rule-based consequences;
 * it never guesses why the crew decided something.
 */
public final class MissionDebrief {
    public static final String REFLECTION = "Talk about it: Which resource limited you? "
            + "What did the weather forecast change? What would you do differently next time?";

    private MissionDebrief() {
        /* w */
    }

    public static String realStory(final MissionState.Snapshot state) {
        final DustStorm storm = state.dustStorm();
        return "Your storm was the real " + storm.label + ": Opportunity's solar power fell from "
                + (int) storm.clearSkyWattHours + " Wh to " + (int) storm.peakWattHours + " Wh a day. " + storm.realOutcome;
    }

    public static List<String> facts(final MissionState.Snapshot state) {
        final List<String> facts = new ArrayList<>();
        facts.add("Time survived " + time(state.elapsedSeconds()) + " of " + time(MissionState.DURATION_SECONDS)
                + "  |  Research " + Math.min(state.researchSeconds(), MissionState.RESEARCH_TARGET_SECONDS)
                + " of " + MissionState.RESEARCH_TARGET_SECONDS + " s");
        facts.add("Lowest battery " + Math.round(state.lowestBattery()) + "%  |  Lowest oxygen "
                + Math.round(state.lowestOxygen()) + "%  |  Radiation dose " + Math.round(state.radiationDose()) + "%  |  "
                + state.decisions() + " equipment decisions");
        return facts;
    }

    public static List<String> consequences(final MissionState.Snapshot state) {
        final List<String> lines = new ArrayList<>();
        if (state.phase() == MissionState.Phase.FAILED && state.radiationDose() >= 100)
            lines.add("The crew reached the radiation limit at " + time(state.elapsedSeconds()) + ", outside the shelter during the solar storm.");
        else if (state.phase() == MissionState.Phase.FAILED) {
            if (state.battery() <= 0)
                lines.add("The battery ran out at " + time(state.elapsedSeconds()) + " " + stormStage(state.elapsedSeconds())
                        + (state.researchOnline() ? ", with research still switched on." : "."));
            else
                lines.add("Oxygen ran out at " + time(state.elapsedSeconds())
                        + (state.oxygenOnline() ? "." : ", with oxygen production switched off."));
        }

        if (state.radiationDose() > 0 && state.radiationDose() < 100)
            lines.add("Crew members spent " + Math.round(state.radiationDose() / MissionState.DOSE_PER_EXPOSED_SECOND)
                    + " seconds outside the shelter during the solar storm.");
        else if (state.radiationDose() == 0 && state.elapsedSeconds() >= MissionState.SOLAR_END_SECONDS)
            lines.add("Nobody was caught outside the shelter during the solar storm.");

        if (state.firstResearchCutSecond() < 0)
            lines.add("Research stayed on for the whole mission.");
        else
            lines.add("Research was first switched off at " + time(state.firstResearchCutSecond()) + " "
                    + stormStage(state.firstResearchCutSecond()) + " (sun "
                    + Math.round(DustStormProfile.solarFactor(state.dustStorm(), state.firstResearchCutSecond()) * 100) + "%).");

        return lines;
    }

    static String stormStage(final int second) {
        if (second < MissionState.STORM_START_SECONDS) return "before the dust arrived";
        if (second < DustStormProfile.PEAK_START_SECONDS) return "while the sky was darkening";
        if (second < MissionState.STORM_END_SECONDS) return "at the storm peak";
        return "while the dust was thinning";
    }

    static String time(final int seconds) {
        return String.format(Locale.ROOT, "%02d:%02d", Math.max(0, seconds) / 60, Math.max(0, seconds) % 60);
    }
}
