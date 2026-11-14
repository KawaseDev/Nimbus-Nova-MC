package cc.nimbusnova.mc.npc;

import cc.nimbusnova.mc.mission.MissionState;

public final class NpcDialogue {
    private NpcDialogue() {
        /* w */
    }

    public static String briefing(final MissionState.Snapshot state) {
        return switch (state.phase()) {
            case READY -> "Welcome to Aster Outpost. You and your crew share one battery and oxygen reserve. "
                    + "Choose your role in the mission console, then ask Mission Control to start. "
                    + "Survive four minutes and complete 90 seconds of powered research for full success.";
            case ACTIVE -> state.storm()
                    ? "The dust front is here. Less sunlight reaches our solar panels. Tell the other engineer what your system needs "
                    + "before changing the power budget. Keep the crew safe while making time for research."
                    : "Your expedition is running. A dust front reduces solar supply from 01:15 to 03:15. "
                    + "Compare your reserves with the time remaining and agree on priorities with your crew.";
            case PAUSED -> "Mission Control paused the simulation. Time and reserves are frozen, so this is a good moment "
                    + "to explain your next decision to the other engineer.";
            case SUCCESS -> "Your crew survived and completed the research objective. Discuss which decision protected your reserves "
                    + "and how you made room for science. You can rotate roles before your next attempt.";
            case PARTIAL -> "Your crew survived, but the research objective was incomplete. Safety matters. Next time, "
                    + "look for a period when you can power research without exhausting essential reserves.";
            case FAILED -> "The mission ended when a shared reserve reached zero. Review what was running and when the dust arrived. "
                    + "Explain one change your crew would make before trying again.";
            case ABORTED -> "Mission Control recalled the crew. Review the last warning together, then reset when everyone is ready.";
        };
    }

    public static String powerHint(final MissionState.Snapshot state) {
        return "At this briefing, the battery is at " + Math.round(state.battery()) + " percent. "
                + (state.storm() ? "Dust is reducing solar generation. " : "Solar generation will fall during the dust event. ")
                + "Research consumes power but earns mission progress. Keeping it off for the whole mission cannot earn full success. "
                + "Ask life support how much oxygen is stored before agreeing on your allocation.";
    }

    public static String oxygenHint(final MissionState.Snapshot state) {
        return "At this briefing, oxygen is at " + Math.round(state.oxygen()) + " percent. "
                + (state.oxygenOnline() ? "The generator is running and uses electricity. " : "The generator is off and the crew is using stored oxygen. ")
                + "Compare the reserve with your crew's plan before changing production. The life-support engineer can use an oxygen canister "
                + "when the mission is active and the reserve has space. These are prototype game units, not astronaut safety measurements.";
    }
}
