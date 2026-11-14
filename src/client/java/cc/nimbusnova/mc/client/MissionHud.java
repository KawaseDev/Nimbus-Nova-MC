package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.mission.MissionProgressPayload;
import cc.nimbusnova.mc.mission.MissionSnapshotPayload;
import cc.nimbusnova.mc.mission.MissionState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

final class MissionHud implements MissionPalette {
    private MissionHud() {
        /* w */
    }

    static void render(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
        final Minecraft client = Minecraft.getInstance();
        final MissionSnapshotPayload packet = NimbusNovaClient.snapshot;
        if (packet == null || client.player == null || client.level == null || client.gui.hud.isHidden() || client.gui.screen() != null) return;

        final MissionState.Snapshot state = packet.state();
        final MissionProgressPayload objective = GuidedMissionClient.progress();
        final int width = Math.min(320, graphics.guiWidth() - 16), left = 8, top = 8;
        MissionUi.panel(graphics, left, top, width, 32);
        NovaRenderer.text(graphics, packet.role().label, left + 10, top + 5, 10, ACCENT);
        final String status = state.phase() == MissionState.Phase.READY ? "PREPARE / no timer"
                : state.phase() == MissionState.Phase.PAUSED ? "PAUSED"
                : state.phase().ended() ? state.phase().name() : MissionUi.time(MissionState.DURATION_SECONDS - state.elapsedSeconds()) + " remaining";
        MissionUi.rightText(graphics, status, left + width - 10, top + 8, state.phase() == MissionState.Phase.FAILED ? DANGER : TEXT);
        NovaRenderer.text(graphics, "Power " + Math.round(state.battery()) + "%", left + 10, top + 20, 8.5F, state.battery() <= 25 ? DANGER : MUTED);
        NovaRenderer.text(graphics, "Air " + Math.round(state.oxygen()) + "%", left + width / 3F, top + 20, 8.5F, state.oxygen() <= 25 ? DANGER : MUTED);
        NovaRenderer.text(graphics, "Science " + Math.min(state.researchSeconds(), MissionState.RESEARCH_TARGET_SECONDS) + "/90s",
                left + width * 2 / 3F, top + 20, 8.5F, MUTED);

        if (state.phase().ended()) {
            drawObjective(graphics, left, top + 36, width, state.objective(),
                    "Open your mission tablet to review the crew's decisions.", "J  Read mission report", null, SUCCESS, "REPORT");
            return;
        }

        if (state.phase() == MissionState.Phase.PAUSED) {
            drawObjective(graphics, left, top + 36, width, "Discuss your next move",
                    "Mission Control paused the clock and reserves. Talk with your crew.", "Wait for Mission Control to resume", null, ACCENT, "PAUSED");
            return;
        }

        if (!ObjectiveGuidance.onMars()) {
            drawObjective(graphics, left, top + 36, width, "Enter Aster Outpost",
                    "Open the mission tablet and choose Enter outpost. Your crew is preparing on Mars.",
                    NimbusNovaClient.consoleKey.getTranslatedKeyMessage().getString() + "  Open mission tablet", null, ACCENT, "START HERE");
            return;
        }

        final boolean solar = state.phase() == MissionState.Phase.ACTIVE
                && (MissionState.solarWarning(state.elapsedSeconds()) || MissionState.solarStorm(state.elapsedSeconds()));
        final Color accent = objective != null && objective.stage().equals("SHELTER_SAFE") ? SUCCESS
                : solar || state.battery() <= 20 || state.oxygen() <= 20 ? DANGER : ACCENT;
        drawObjective(graphics, left, top + 36, width,
                objective == null ? "Meet Commander Vega" : objective.title(),
                objective == null || objective.stage().equals("BRIEFING")
                        ? "WASD: walk. Mouse: look. Follow the arrow to Commander Vega, then right-click him."
                        : objective.stage().equals("ROLE") ? "Open your mission tablet, choose Crew, and pick your astronaut job." : objective.instruction(),
                objective == null ? "Right-click the astronaut" : ObjectiveGuidance.navigation(objective), objective, accent,
                objective == null ? "FIRST TASK" : state.phase() != MissionState.Phase.READY ? solar ? "SAFETY" : "MISSION"
                        : objective.ready() ? "CREW READY" : "CREW " + objective.completed() + "/" + objective.total());
    }

    private static void drawObjective(final GuiGraphicsExtractor graphics, final int x, final int y, final int width,
                                      final String title, final String instruction, final String action,
                                      final MissionProgressPayload objective, final Color accent, final String badge) {
        MissionUi.panel(graphics, x, y, width, 72);
        final float titleSize = NovaRenderer.width(title, 13) > width - 95 ? 11 : 13;
        NovaRenderer.text(graphics, title, x + 12, y + 7, titleSize, TEXT);
        NovaRenderer.text(graphics, badge, x + width - 10 - NovaRenderer.width(badge, 8), y + 10, 8, accent);
        NovaRenderer.wrap(graphics, instruction, x + 12, y + 29, width - 24, 9.5F, 2, TEXT);
        ObjectiveGuidance.arrow(graphics, objective, x + 17, y + 61, accent);
        NovaRenderer.wrap(graphics, action, x + (objective == null ? 12 : 31), y + 55, width - (objective == null ? 24 : 43), 9, 1, accent);
    }
}
