package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.mission.CrewRole;
import cc.nimbusnova.mc.mission.MissionSnapshotPayload;
import cc.nimbusnova.mc.mission.MissionState;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

/**
 * A short checklist shown on the HUD before the mission starts. Steps tick themselves off as the player does them,
 * so a new crew member learns the controls without reading a manual.
 */
final class MissionTutorial implements MissionPalette {
    static final int HEIGHT = 124;
    // Small screens drop the last two lines so the panel stays above the chat.
    static final int COMPACT_HEIGHT = 76;
    // Set once the player opens the console this session; cleared on disconnect.
    static boolean consoleOpened;

    private MissionTutorial() {
        /* w */
    }

    static boolean visible(final MissionSnapshotPayload packet) {
        return packet.state().phase() == MissionState.Phase.READY;
    }

    static void render(final GuiGraphicsExtractor graphics, final MissionSnapshotPayload packet,
                       final int x, final int y, final int width, final boolean compact) {
        final boolean hasRole = packet.role() != CrewRole.OBSERVER || packet.operator();
        MissionUi.panel(graphics, x, y, width, compact ? COMPACT_HEIGHT : HEIGHT);
        MissionUi.text(graphics, "HOW TO PLAY", x + 10, y + 10, ACCENT);
        MissionUi.rightText(graphics, (consoleOpened ? 1 : 0) + (hasRole ? 1 : 0) + "/2", x + width - 10, y + 10, MUTED);

        step(graphics, "Press " + NimbusNovaClient.consoleKey.getTranslatedKeyMessage().getString() + " to open the mission console",
                consoleOpened, x + 10, y + 27, width - 20);
        step(graphics, "Pick a role in the Systems tab", hasRole, x + 10, y + 41, width - 20);
        MissionUi.wrap(graphics, job(packet.role()), x + 10, y + 57, width - 20, 2, TEXT);
        if (compact) return;

        MissionUi.text(graphics, packet.operator() ? "You are Mission Control: press Start when the crew is ready."
                : "Then wait for Mission Control to press Start.", x + 10, y + 82, MUTED);
        MissionUi.wrap(graphics, "Goal: keep battery and oxygen above 0 for 4 minutes and finish 90 s of research.",
                x + 10, y + 98, width - 20, 2, SUCCESS);
    }

    static String job(final CrewRole role) {
        return switch (role) {
            case POWER -> "Your job: switch research off when the sun drops, and back on when it returns.";
            case LIFE_SUPPORT -> "Your job: keep oxygen flowing. You also have 2 emergency oxygen canisters.";
            case WEATHER -> "Your job: only you can see the sun. Press Send forecast to warn the crew.";
            case OBSERVER -> "Each role sees and controls different things, so talk to each other!";
        };
    }

    private static void step(final GuiGraphicsExtractor graphics, final String text, final boolean done,
                             final int x, final int y, final int width) {
        final Color mark = done ? SUCCESS : BORDER;
        NovaRenderer.rect(graphics, x, y, 7, 7, 2, mark);
        MissionUi.text(graphics, text, x + 13, y, done ? MUTED : TEXT);
    }
}
