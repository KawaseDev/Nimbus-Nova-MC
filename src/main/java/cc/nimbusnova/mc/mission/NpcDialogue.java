package cc.nimbusnova.mc.mission;

import java.util.List;

/**
 * Authored, state-aware lines for the outpost's two fictional NPCs. Each click shows the next line, so players can
 * replay instructions. Lines explain consequences without choosing the crew's strategy for them (PLAN.md section 6).
 */
public final class NpcDialogue {
    public static final String SPECIALIST = "Dr. Lina Vega";
    public static final String ROBOT = "Bolt";

    private NpcDialogue() {
        /* w */
    }

    /** The mission specialist: welcome, role briefing, the real storm, and what to do next. */
    public static List<String> specialist(final MissionState.Snapshot state, final CrewRole role) {
        final DustStorm storm = state.dustStorm();
        return switch (state.phase()) {
            case READY -> List.of(
                    "Welcome to Aster Outpost! I'm Dr. Lina Vega, your mission specialist.",
                    role == CrewRole.OBSERVER
                            ? "First, press J and pick a role: power engineer, life support, or weather officer."
                            : "You are our " + role.label.toLowerCase() + ". " + roleJob(role),
                    "A dust storm is coming. It is based on a real storm that NASA's Opportunity rover lived through on Mars.",
                    "Talk to your crew before Mission Control presses Start. Nobody can see everything alone.");
            case ACTIVE -> state.elapsedSeconds() < MissionState.STORM_START_SECONDS ? List.of(
                    "The sky is still clear. Research needs a lot of power, so this is a good time for science.",
                    "Weather officer, keep watching the sun. Tell us when it starts to drop.")
                    : state.storm() ? List.of(
                    "Dust blocks the sunlight, so our solar panels make less power.",
                    "Opportunity measured only " + storm.peakPercent() + "% of its normal sunlight in the " + storm.label + ".",
                    "Every system uses power. Which one can wait until the sun returns?")
                    : List.of(
                    "The dust is settling, but the sun is not fully back yet.",
                    "Ask the weather officer before you switch everything back on.");
            case PAUSED -> List.of("We are paused. Talk with your crew: what will you change when we resume?");
            case SUCCESS, PARTIAL, FAILED, ABORTED -> List.of(
                    "Press J and open the Debrief tab to see what happened and why.",
                    "Real missions learn from every attempt. The next storm may be a different real one!");
        };
    }

    /**
     * The maintenance robot gives hints in stages during a mission: which system is the problem, where to act,
     * then the exact trade-off. Outside a mission it explains what it does.
     */
    public static List<String> robot(final MissionState.Snapshot state) {
        if (state.phase() != MissionState.Phase.ACTIVE)
            return List.of("Beep boop! I am Bolt, the maintenance robot. Start the mission and ask me for hints.",
                    "Tip: dust in the sky blocks sunlight. Cleaning the panels would not help, because the dust is in the air.");

        if (state.oxygen() <= 30)
            return List.of(
                    "Beep! Hint 1: the oxygen reserve is getting low.",
                    "Hint 2: the life-support engineer controls oxygen at the stone console in the habitat. There are emergency canisters too.",
                    "Hint 3: oxygen production uses 0.45 energy/s. Without it, oxygen drops by 0.4 every second.");

        if (state.battery() <= 30 || state.storm())
            return List.of(
                    "Beep! Hint 1: the battery is the problem. Less sunlight means less power coming in.",
                    "Hint 2: the power engineer can switch research at the sensor in the power yard.",
                    "Hint 3: research uses 0.65 energy/s, the biggest load. Switching it off saves power but pauses science.",
                    "Why less power? Dust in the air blocks sunlight before it reaches the panels.");

        return List.of(
                "Beep! All systems are running. Use the clear sky to make progress on research.",
                "Hint: research needs " + MissionState.RESEARCH_TARGET_SECONDS + " seconds in total for full success.");
    }

    public static String roleJob(final CrewRole role) {
        return switch (role) {
            case POWER -> "You switch research on and off. Save power when the sun drops.";
            case LIFE_SUPPORT -> "You keep oxygen flowing for the crew, and you hold the emergency canisters.";
            case WEATHER -> "Only you can see the sun. Warn the crew before the storm gets worse.";
            case OBSERVER -> "Pick a role so you have a job on the crew.";
        };
    }
}
