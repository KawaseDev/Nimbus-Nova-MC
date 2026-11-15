package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.airlock.EntranceAirlockClient;
import cc.nimbusnova.mc.world.EntranceAirlock;
import cc.nimbusnova.mc.world.ship.ShipMap;
import cc.nimbusnova.mc.world.moon.LunarSurface;
import cc.nimbusnova.mc.client.suit.SuitLifeSupportClient;
import cc.nimbusnova.mc.suit.SuitAirPayload;
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
        if (client.level.dimension().equals(ShipMap.DIMENSION) || client.level.dimension().equals(LunarSurface.DIMENSION)
                || client.level.dimension().identifier().toString().startsWith("nimbus_nova:scenario_")) return;

        final int width = Math.min(252, graphics.guiWidth() - 12), left = 6, top = 6;
        if (!ObjectiveGuidance.onMars()) {
            drawObjective(graphics, left, top, width, "Mission Control",
                    "Choose a mission in the library, or join the one Mission Control selected for your crew.",
                    NimbusNovaClient.consoleKey.getTranslatedKeyMessage().getString() + "  Open mission library", null, ACCENT, "START HERE");
            return;
        }

        final MissionState.Snapshot state = packet.state();
        final MissionProgressPayload objective = GuidedMissionClient.progress();
        final SuitAirPayload suit = SuitLifeSupportClient.state();
        final int summaryHeight = suit == null ? 37 : 49, objectiveTop = top + summaryHeight + 3;
        NovaRenderer.hud(graphics, left, top, width, summaryHeight, BACKGROUND);
        NovaRenderer.chip(graphics, packet.role().label, left + 6, top + 5, ACCENT);

        final int remaining = MissionState.DURATION_SECONDS - state.elapsedSeconds();
        final boolean running = state.phase() == MissionState.Phase.ACTIVE;
        final String status = state.phase() == MissionState.Phase.READY ? "PREPARE"
                : state.phase() == MissionState.Phase.PAUSED ? "PAUSED"
                : state.phase().ended() ? state.phase().name() : MissionUi.time(remaining);
        final Color statusColor = state.phase() == MissionState.Phase.FAILED || running && remaining <= 30
                ? NovaRenderer.mix(DANGER, TEXT, NovaRenderer.pulse(8) * 0.5F) : running ? TEXT : OXYGEN;
        NovaRenderer.chip(graphics, status, left + width - 6 - NovaRenderer.chipWidth(status), top + 5, statusColor);

        final int meterWidth = (width - 24) / 3;
        final int research = Math.min(state.researchSeconds(), MissionState.RESEARCH_TARGET_SECONDS);
        reading(graphics, "Power", Math.round(state.battery()) + "%", left + 6, top + 19, meterWidth, state.battery() / 100, ACCENT, state.battery() <= 25);
        reading(graphics, "Air", Math.round(state.oxygen()) + "%", left + 12 + meterWidth, top + 19, meterWidth, state.oxygen() / 100, OXYGEN, state.oxygen() <= 25);
        reading(graphics, "Science", research + "/" + MissionState.RESEARCH_TARGET_SECONDS, left + 18 + meterWidth * 2, top + 19, meterWidth,
                (double) research / MissionState.RESEARCH_TARGET_SECONDS, SUCCESS, false);

        if (suit != null) {
            final String location = suit.frozen() ? "paused" : suit.pressurized() ? "habitat air, refilling"
                    : !suit.helmet() ? "HELMET MISSING" : !suit.sealedSuit() ? "SUIT INCOMPLETE" : "outside, suit sealed";
            final boolean alarm = suit.air() <= 25 || !suit.pressurized() && !suit.sealedSuit();
            final String label = "Suit " + (int) Math.ceil(suit.air()) + "%";
            NovaRenderer.text(graphics, label, left + 6, top + 38, 8, alarm ? NovaRenderer.mix(DANGER, TEXT, NovaRenderer.pulse(8) * 0.5F) : MUTED);
            NovaRenderer.meter(graphics, left + 10 + NovaRenderer.width(label, 8), top + 39, 44, 5, suit.air() / 100, alarm ? DANGER : OXYGEN);
            NovaRenderer.text(graphics, location, left + 59 + NovaRenderer.width(label, 8), top + 38, 8, alarm ? DANGER : MUTED);
        }

        if (EntranceAirlockClient.scan() != null && EntranceAirlockClient.scan().visible()
                && EntranceAirlockClient.scan().stage() > 0 && EntranceAirlock.isChamber(client.player.blockPosition())) return;

        if (state.phase().ended()) {
            drawObjective(graphics, left, objectiveTop, width, state.objective(),
                    "Open your mission tablet to review the crew's decisions.", "J  Read mission report", null, SUCCESS, "REPORT");
            return;
        }

        if (state.phase() == MissionState.Phase.PAUSED) {
            drawObjective(graphics, left, objectiveTop, width, "Discuss your next move",
                    "Mission Control paused the clock and reserves. Talk with your crew.", "Wait for Mission Control to resume", null, ACCENT, "PAUSED");
            return;
        }

        final boolean solar = state.phase() == MissionState.Phase.ACTIVE
                && (MissionState.solarWarning(state.elapsedSeconds()) || MissionState.solarStorm(state.elapsedSeconds()));
        final Color accent = objective != null && objective.stage().equals("SHELTER_SAFE") ? SUCCESS
                : solar || state.battery() <= 20 || state.oxygen() <= 20 ? DANGER : ACCENT;
        drawObjective(graphics, left, objectiveTop, width,
                objective == null ? "Meet Commander Vega" : objective.title(),
                objective == null || objective.stage().equals("BRIEFING")
                        ? "WASD: walk. Mouse: look. Follow the arrow to Commander Vega, then right-click him."
                        : objective.stage().equals("ROLE") ? "Open your mission tablet, choose Crew, and pick your astronaut job." : objective.instruction(),
                objective == null ? "Right-click the astronaut" : ObjectiveGuidance.navigation(objective), objective, accent,
                objective == null ? "FIRST TASK" : state.phase() != MissionState.Phase.READY ? solar ? "SAFETY" : "MISSION"
                        : objective.ready() ? "CREW READY" : "CREW " + objective.completed() + "/" + objective.total());
    }

    private static void reading(final GuiGraphicsExtractor graphics, final String name, final String value, final int x, final int y,
                                final int width, final double fraction, final Color color, final boolean alarm) {
        final Color shown = alarm ? NovaRenderer.mix(DANGER, TEXT, NovaRenderer.pulse(8) * 0.5F) : color;
        NovaRenderer.text(graphics, name, x, y, 8, alarm ? shown : MUTED);
        NovaRenderer.text(graphics, value, x + width - NovaRenderer.width(value, 8), y, 8, shown);
        NovaRenderer.meter(graphics, x, y + 10, width, 5, fraction, alarm ? DANGER : color);
    }

    private static void drawObjective(final GuiGraphicsExtractor graphics, final int x, final int y, final int width,
                                      final String title, final String instruction, final String action,
                                      final MissionProgressPayload objective, final Color accent, final String badge) {
        final int footer = NovaRenderer.objective(graphics, x, y, width, title, badge, instruction, accent);
        ObjectiveGuidance.arrow(graphics, objective, x + 15, footer + 3, accent);
        NovaRenderer.wrap(graphics, action, x + (objective != null && objective.hasWorldTarget() ? 27 : 9), footer,
                width - (objective != null && objective.hasWorldTarget() ? 36 : 18), 8, 1, accent);
    }
}
