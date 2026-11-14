package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.mission.MissionSnapshotPayload;
import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.client.render.NovaRenderer;
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
        if (packet == null || client.gui.hud.isHidden() || client.gui.screen() != null) return;

        final MissionState.Snapshot state = packet.state();
        if (graphics.guiWidth() < 480) {
            renderCompact(graphics, packet);
            return;
        }

        final int x = 12, y = 12, width = 184;
        final Color status = state.phase() == MissionState.Phase.FAILED ? DANGER
                : state.phase() == MissionState.Phase.SUCCESS ? SUCCESS : ACCENT;

        MissionUi.panel(graphics, x, y, width, 116);
        NovaRenderer.rect(graphics, x + 4, y + 9, 2, 20, 1, status);
        MissionUi.text(graphics, "NIMBUS NOVA", x + 12, y + 10, TEXT);
        MissionUi.rightText(graphics, MissionUi.time(MissionState.DURATION_SECONDS - state.elapsedSeconds()), x + width - 10, y + 10, status);
        MissionUi.text(graphics, state.phase().name() + " / " + (state.storm() ? "DUST EVENT" : "HABITAT SYSTEMS"), x + 12, y + 25, MUTED);

        MissionUi.text(graphics, "Battery", x + 12, y + 45, MUTED);
        MissionUi.rightText(graphics, Math.round(state.battery()) + "%", x + width - 12, y + 45, state.battery() <= 25 ? DANGER : ACCENT);
        MissionUi.bar(graphics, x + 12, y + 58, width - 24, state.battery(), state.battery() <= 25 ? DANGER : ACCENT);

        MissionUi.text(graphics, "Oxygen", x + 12, y + 72, MUTED);
        MissionUi.rightText(graphics, Math.round(state.oxygen()) + "%", x + width - 12, y + 72, state.oxygen() <= 25 ? DANGER : OXYGEN);
        MissionUi.bar(graphics, x + 12, y + 85, width - 24, state.oxygen(), state.oxygen() <= 25 ? DANGER : OXYGEN);
        MissionUi.text(graphics, MissionUi.rate(state.generation() - state.demand()) + " energy/s  |  "
                + Math.min(100, state.researchSeconds() * 100 / MissionState.RESEARCH_TARGET_SECONDS) + "% science", x + 12, y + 101, MUTED);

        final int objectiveX = graphics.guiWidth() - 230, objectiveWidth = 218;
        MissionUi.panel(graphics, objectiveX, y, objectiveWidth, 67);
        MissionUi.text(graphics, packet.role().label, objectiveX + 10, y + 10, ACCENT);
        MissionUi.wrap(graphics, state.objective(), objectiveX + 10, y + 25, objectiveWidth - 20, 2, TEXT);
        MissionUi.text(graphics, NimbusNovaClient.consoleKey.getTranslatedKeyMessage().getString() + "  Mission console", objectiveX + 10, y + 51, MUTED);

        if (state.phase() == MissionState.Phase.ACTIVE && (state.battery() <= 25 || state.oxygen() <= 25 || state.storm())
                && graphics.guiHeight() >= 260) {
            final String warning = state.oxygen() <= 25 ? "LOW OXYGEN / restore production"
                    : state.battery() <= 25 ? "LOW BATTERY / reduce power demand" : "DUST FRONT / solar output reduced";
            final int warningWidth = Math.min(graphics.guiWidth() - 24, (int) Math.ceil(NovaRenderer.width(warning, 10)) + 24);
            final int warningX = (graphics.guiWidth() - warningWidth) / 2;
            MissionUi.panel(graphics, warningX, graphics.guiHeight() - 79, warningWidth, 24);
            MissionUi.text(graphics, warning, warningX + 12, graphics.guiHeight() - 71, DANGER);
        }
    }

    private static void renderCompact(final GuiGraphicsExtractor graphics, final MissionSnapshotPayload packet) {
        final MissionState.Snapshot state = packet.state();
        final int x = 8, y = 8, width = graphics.guiWidth() - 16, meterWidth = (width - 36) / 2;
        MissionUi.panel(graphics, x, y, width, 80);
        MissionUi.text(graphics, "NIMBUS NOVA", x + 10, y + 8, TEXT);
        MissionUi.rightText(graphics, state.phase() + " / " + MissionUi.time(MissionState.DURATION_SECONDS - state.elapsedSeconds()),
                x + width - 10, y + 8, state.storm() ? DANGER : ACCENT);
        MissionUi.text(graphics, packet.role().label, x + 10, y + 23, MUTED);
        MissionUi.rightText(graphics, NimbusNovaClient.consoleKey.getTranslatedKeyMessage().getString() + "  Mission console",
                x + width - 10, y + 23, MUTED);
        MissionUi.text(graphics, "Battery " + Math.round(state.battery()) + "%", x + 10, y + 39, state.battery() <= 25 ? DANGER : ACCENT);
        MissionUi.text(graphics, "Oxygen " + Math.round(state.oxygen()) + "%", x + 26 + meterWidth, y + 39, state.oxygen() <= 25 ? DANGER : OXYGEN);
        MissionUi.bar(graphics, x + 10, y + 52, meterWidth, state.battery(), state.battery() <= 25 ? DANGER : ACCENT);
        MissionUi.bar(graphics, x + 26 + meterWidth, y + 52, meterWidth, state.oxygen(), state.oxygen() <= 25 ? DANGER : OXYGEN);
        MissionUi.text(graphics, "Science " + Math.min(state.researchSeconds(), MissionState.RESEARCH_TARGET_SECONDS) + "/90s", x + 10, y + 65, SUCCESS);
        MissionUi.rightText(graphics, state.storm() ? "DUST / solar reduced" : MissionUi.rate(state.generation() - state.demand()) + " energy/s",
                x + width - 10, y + 65, state.storm() ? DANGER : MUTED);
    }
}
