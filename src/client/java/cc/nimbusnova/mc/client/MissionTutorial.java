package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.mission.CrewRole;

final class MissionTutorial {
    static boolean consoleOpened;

    private MissionTutorial() {
        /* w */
    }

    static String job(final CrewRole role) {
        return switch (role) {
            case POWER -> "Repair the solar circuit. During the storm, decide when the crew can afford to run research.";
            case LIFE_SUPPORT -> "Prepare the oxygen recycler. Keep the crew's air supply safe and use emergency supplies carefully.";
            case WEATHER -> "Collect the field sample. Watch the sky instruments and tell your crew when conditions will change.";
            case OBSERVER -> "Choose a crew role. Follow the marked objective and ask your teammates what their systems need.";
        };
    }
}
