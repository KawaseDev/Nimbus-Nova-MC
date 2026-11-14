package cc.nimbusnova.mc.npc;

import cc.nimbusnova.mc.mission.CrewRole;
import cc.nimbusnova.mc.mission.DustStorm;
import cc.nimbusnova.mc.mission.MissionDebrief;
import cc.nimbusnova.mc.mission.MissionState;

/**
 * Authored, state-aware lines for the outpost astronauts. They explain consequences and point to the right crew
 * member, but never choose the crew's strategy (PLAN.md section 6).
 */
public final class NpcDialogue {
    private NpcDialogue() {
        /* w */
    }

    public static String briefing(final MissionState.Snapshot state) {
        return briefing(state, CrewRole.OBSERVER);
    }

    public static String briefing(final MissionState.Snapshot state, final CrewRole role) {
        final DustStorm storm = state.dustStorm();
        return switch (state.phase()) {
            case READY -> "Welcome to Aster Outpost. You and your crew share one battery and oxygen reserve. "
                    + (role == CrewRole.OBSERVER ? "Choose your role in the mission console, then ask Mission Control to start. "
                    : "You are our " + role.label.toLowerCase() + ". " + roleJob(role) + " When the crew is ready, ask Mission Control to start. ")
                    + "A dust storm is coming. It follows a real storm that NASA's Opportunity rover lived through on Mars. "
                    + "If a solar storm alert sounds, everyone goes to the crew shelter south of the habitat. "
                    + "Survive four minutes and complete 90 seconds of powered research for full success.";
            case ACTIVE -> state.storm()
                    ? "The dust front is here. Less sunlight reaches our solar panels: in the real " + storm.label
                    + ", Opportunity got only " + storm.peakPercent() + "% of its normal sunlight at the worst point. "
                    + "Ask the weather officer how the sky is changing, and tell the other engineers what your system needs "
                    + "before changing the power budget. Keep the crew safe while making time for research."
                    : state.elapsedSeconds() < MissionState.STORM_START_SECONDS
                    ? "Your expedition is running and the sky is still clear. A dust storm will start dimming the sun at 01:15. "
                    + "Compare your reserves with the time remaining and agree on priorities with your crew."
                    : "The dust is thinning, but the sun is not fully back yet. Ask the weather officer for a forecast "
                    + "before you switch everything back on.";
            case PAUSED -> "Mission Control paused the simulation. Time and reserves are frozen, so this is a good moment "
                    + "to explain your next decision to the other engineers.";
            case SUCCESS -> "Your crew survived and completed the research objective. Discuss which decision protected your reserves "
                    + "and how you made room for science. " + debriefPointer(state);
            case PARTIAL -> "Your crew survived, but the research objective was incomplete. Safety matters. Next time, "
                    + "look for a period when you can power research without exhausting essential reserves. " + debriefPointer(state);
            case FAILED -> "The mission ended when a shared reserve reached zero. Review what was running and when the dust arrived. "
                    + "Explain one change your crew would make before trying again. " + debriefPointer(state);
            case ABORTED -> "Mission Control recalled the crew. Review the last warning together, then reset when everyone is ready.";
        };
    }

    public static String powerHint(final MissionState.Snapshot state) {
        return "At this briefing, the battery is at " + Math.round(state.battery()) + " percent. "
                + (state.storm() ? "Dust is reducing solar generation. " : "Solar generation will fall during the dust event. ")
                + "Dust in the air blocks sunlight before it reaches the panels, so cleaning the panels would not help. "
                + "Research is the biggest load at 0.65 energy per second and earns mission progress. "
                + "Keeping it off for the whole mission cannot earn full success. "
                + "Ask life support how much oxygen is stored before agreeing on your allocation.";
    }

    public static String oxygenHint(final MissionState.Snapshot state) {
        return "At this briefing, oxygen is at " + Math.round(state.oxygen()) + " percent. "
                + (state.oxygenOnline() ? "The generator is running and uses 0.45 energy per second. "
                : "The generator is off and the crew is using stored oxygen, about 0.4 units every second. ")
                + "Compare the reserve with your crew's plan before changing production. The life-support engineer can use "
                + (state.canistersLeft() == 0 ? "no more oxygen canisters this mission. "
                : "one of the " + state.canistersLeft() + " remaining oxygen canisters when the mission is active and the reserve has space. ")
                + "These are prototype game units, not astronaut safety measurements.";
    }

    static String roleJob(final CrewRole role) {
        return switch (role) {
            case POWER -> "You switch research on and off, so save power when the sun drops.";
            case LIFE_SUPPORT -> "You keep oxygen flowing and hold the emergency canisters.";
            case WEATHER -> "Only you can see the sun, so warn the crew before the storm gets worse.";
            case OBSERVER -> "";
        };
    }

    private static String debriefPointer(final MissionState.Snapshot state) {
        return "Press J and open the Debrief tab to see what happened. " + MissionDebrief.realStory(state);
    }
}
