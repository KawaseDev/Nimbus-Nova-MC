package cc.nimbusnova.mc.client;

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
        if (packet == null || client.gui.hud.isHidden() || client.gui.screen() != null) return;

        final MissionState.Snapshot state = packet.state();
        final int x = 12, y = 12, width = 184;
        final Color status = state.phase() == MissionState.Phase.FAILED ? DANGER
                : state.phase() == MissionState.Phase.SUCCESS ? SUCCESS : ACCENT;

        MissionUi.panel(graphics, x, y, width, 116);
        graphics.fill(x, y, x + 3, y + 28, status.getRGB());
        MissionUi.text(graphics, "NIMBUS NOVA", x + 12, y + 10, TEXT);
        MissionUi.rightText(graphics, MissionUi.time(MissionState.DURATION_SECONDS - state.elapsedSeconds()), x + width - 10, y + 10, status);
        final String sky = MissionUi.seesWeather(packet)
                ? "SUN " + MissionUi.sunPercent(state) + "% > " + MissionUi.forecastPercent(state) + "%"
                : state.storm() ? "DUST: ASK WEATHER" : "HABITAT SYSTEMS";
        MissionUi.text(graphics, state.phase().name() + " / " + sky, x + 12, y + 25, MUTED);

        MissionUi.text(graphics, "Battery", x + 12, y + 45, MUTED);
        MissionUi.rightText(graphics, Math.round(state.battery()) + "%", x + width - 12, y + 45, state.battery() <= 25 ? DANGER : ACCENT);
        MissionUi.bar(graphics, x + 12, y + 58, width - 24, state.battery(), state.battery() <= 25 ? DANGER : ACCENT);

        MissionUi.text(graphics, "Oxygen", x + 12, y + 72, MUTED);
        MissionUi.rightText(graphics, Math.round(state.oxygen()) + "%", x + width - 12, y + 72, state.oxygen() <= 25 ? DANGER : OXYGEN);
        MissionUi.bar(graphics, x + 12, y + 85, width - 24, state.oxygen(), state.oxygen() <= 25 ? DANGER : OXYGEN);
        MissionUi.text(graphics, MissionUi.rate(state.generation() - state.demand()) + " energy/s  |  "
                + Math.min(100, state.researchSeconds() * 100 / MissionState.RESEARCH_TARGET_SECONDS) + "% science", x + 12, y + 101, MUTED);

        final boolean wide = graphics.guiWidth() >= 480;
        final int objectiveX = wide ? graphics.guiWidth() - 230 : x;
        final int objectiveY = wide ? y : y + 122;
        final int objectiveWidth = wide ? 218 : 184;
        MissionUi.panel(graphics, objectiveX, objectiveY, objectiveWidth, 67);
        MissionUi.text(graphics, packet.role().label, objectiveX + 10, objectiveY + 10, ACCENT);
        MissionUi.wrap(graphics, state.objective(), objectiveX + 10, objectiveY + 25, objectiveWidth - 20, 2, TEXT);
        MissionUi.text(graphics, NimbusNovaClient.consoleKey.getTranslatedKeyMessage().getString() + "  Mission console", objectiveX + 10, objectiveY + 51, MUTED);

        if (state.phase() == MissionState.Phase.ACTIVE && (state.battery() <= 25 || state.oxygen() <= 25 || state.storm())
                && graphics.guiHeight() >= 260) {
            final String warning = state.oxygen() <= 25 ? "LOW OXYGEN / restore production"
                    : state.battery() <= 25 ? "LOW BATTERY / reduce power demand" : "DUST FRONT / solar output reduced";
            final int warningWidth = Math.min(graphics.guiWidth() - 24, client.font.width(warning) + 24);
            final int warningX = (graphics.guiWidth() - warningWidth) / 2;
            MissionUi.panel(graphics, warningX, graphics.guiHeight() - 79, warningWidth, 24);
            MissionUi.text(graphics, warning, warningX + 12, graphics.guiHeight() - 71, DANGER);
        }
    }
}
