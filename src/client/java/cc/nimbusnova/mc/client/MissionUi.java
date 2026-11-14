package cc.nimbusnova.mc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.awt.Color;
import java.util.Locale;

final class MissionUi implements MissionPalette {
    private MissionUi() {
        /* w */
    }

    static void panel(final GuiGraphicsExtractor graphics, final int x, final int y, final int width, final int height) {
        graphics.fill(x, y, x + width, y + height, BACKGROUND.getRGB());
        graphics.outline(x, y, width, height, BORDER.getRGB());
    }

    static void text(final GuiGraphicsExtractor graphics, final String text, final int x, final int y, final Color color) {
        graphics.text(Minecraft.getInstance().font, text, x, y, color.getRGB(), false);
    }

    static void rightText(final GuiGraphicsExtractor graphics, final String text, final int right, final int y, final Color color) {
        text(graphics, text, right - Minecraft.getInstance().font.width(text), y, color);
    }

    static void wrap(final GuiGraphicsExtractor graphics, final String text, final int x, final int y,
                     final int width, final int lines, final Color color) {
        int line = 0;
        for (final FormattedCharSequence part : Minecraft.getInstance().font.split(Component.literal(text), width)) {
            if (line >= lines) break;
            graphics.text(Minecraft.getInstance().font, part, x, y + line++ * 11, color.getRGB(), false);
        }
    }

    static void bar(final GuiGraphicsExtractor graphics, final int x, final int y,
                    final int width, final double percentage, final Color color) {
        graphics.fill(x, y, x + width, y + 4, SURFACE.getRGB());
        graphics.fill(x, y, x + (int) Math.round(width * Math.clamp(percentage, 0, 100) / 100), y + 4, color.getRGB());
        for (int divider = 1; divider < 10; divider++)
            graphics.fill(x + width * divider / 10, y, x + width * divider / 10 + 1, y + 4, BACKGROUND.getRGB());
    }

    static String time(final int seconds) {
        return String.format(Locale.ROOT, "%02d:%02d", Math.max(0, seconds) / 60, Math.max(0, seconds) % 60);
    }

    static String rate(final double rate) {
        return String.format(Locale.ROOT, "%+.2f", rate);
    }
}
