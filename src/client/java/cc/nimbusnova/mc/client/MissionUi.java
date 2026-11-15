package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.mission.DustStormProfile;
import cc.nimbusnova.mc.mission.MissionSnapshotPayload;
import cc.nimbusnova.mc.mission.MissionState;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;
import java.util.Locale;

final class MissionUi implements MissionPalette {
    private MissionUi() {
        /* w */
    }

    static void panel(final GuiGraphicsExtractor graphics, final int x, final int y, final int width, final int height) {
        NovaRenderer.window(graphics, x, y, width, height, BACKGROUND);
    }

    static void text(final GuiGraphicsExtractor graphics, final String text, final int x, final int y, final Color color) {
        NovaRenderer.text(graphics, text, x, y - 2, 10, color);
    }

    static void rightText(final GuiGraphicsExtractor graphics, final String text, final int right, final int y, final Color color) {
        NovaRenderer.text(graphics, text, right - NovaRenderer.width(text, 10), y - 2, 10, color);
    }

    static void wrap(final GuiGraphicsExtractor graphics, final String text, final int x, final int y,
                     final int width, final int lines, final Color color) {
        NovaRenderer.wrap(graphics, text, x, y - 2, width, 9, lines, color);
    }

    static void bar(final GuiGraphicsExtractor graphics, final int x, final int y,
                    final int width, final double percentage, final Color color) {
        NovaRenderer.meter(graphics, x, y, width, 5, percentage / 100, color);
    }

    static String time(final int seconds) {
        return String.format(Locale.ROOT, "%02d:%02d", Math.max(0, seconds) / 60, Math.max(0, seconds) % 60);
    }

    static long sunPercent(final MissionState.Snapshot state) {
        return Math.round(DustStormProfile.solarFactor(state.dustStorm(), state.elapsedSeconds()) * 100);
    }

    static long forecastPercent(final MissionState.Snapshot state) {
        return Math.round(DustStormProfile.forecastFactor(state.dustStorm(), state.elapsedSeconds()) * 100);
    }

    static boolean seesWeather(final MissionSnapshotPayload packet) {
        return packet.operator() || packet.role().seesWeather();
    }

    static String rate(final double rate) {
        return String.format(Locale.ROOT, "%+.2f", rate);
    }
}
