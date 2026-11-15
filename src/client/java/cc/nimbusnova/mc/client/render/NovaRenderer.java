package cc.nimbusnova.mc.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.awt.Color;

public final class NovaRenderer {
    private static final Color OUTLINE = new Color(12, 11, 18);
    private static final Color METAL = new Color(195, 193, 190);
    private static final Color LIGHT_EDGE = new Color(250, 247, 238);
    private static final Color DARK_EDGE = new Color(84, 82, 87);
    private static final Color SLOT_SHADOW = new Color(17, 15, 24);
    private static final Color HUD_EDGE = new Color(118, 115, 127);
    private static final Color FOCUS = new Color(255, 218, 117);

    private NovaRenderer() {
        /* w */
    }

    public static void rect(final GuiGraphicsExtractor graphics, final float x, final float y,
                            final float width, final float height, final Color color) {
        final int left = Math.round(x), top = Math.round(y), right = Math.round(x + width), bottom = Math.round(y + height);
        if (right > left && bottom > top)
            graphics.fill(left, top, right, bottom, color.getRGB());
    }

    public static void stroke(final GuiGraphicsExtractor graphics, final float x, final float y,
                              final float width, final float height, final float thickness, final Color color) {
        final int edge = Math.max(1, Math.round(thickness));
        rect(graphics, x, y, width, edge, color);
        rect(graphics, x, y + height - edge, width, edge, color);
        rect(graphics, x, y + edge, edge, height - edge * 2, color);
        rect(graphics, x + width - edge, y + edge, edge, height - edge * 2, color);
    }

    public static void window(final GuiGraphicsExtractor graphics, final int x, final int y,
                              final int width, final int height, final Color well) {
        rect(graphics, x, y, width, height, OUTLINE);
        bevel(graphics, x + 1, y + 1, width - 2, height - 2, METAL, LIGHT_EDGE, DARK_EDGE, 2);
        bevel(graphics, x + 4, y + 4, width - 8, height - 8, well, SLOT_SHADOW, HUD_EDGE, 1);
    }

    public static void title(final GuiGraphicsExtractor graphics, final int x, final int y, final int width, final String title) {
        bevel(graphics, x + 6, y + 6, width - 12, 23, METAL, LIGHT_EDGE, DARK_EDGE, 1);
        drawText(graphics, title, x + 14, y + 12, Math.min(1.35F, (width - 49F) / Math.max(1, width(title, 10))), OUTLINE);
    }

    public static void hud(final GuiGraphicsExtractor graphics, final float x, final float y,
                           final float width, final float height, final Color well) {
        rect(graphics, x, y, width, height, OUTLINE);
        bevel(graphics, x + 1, y + 1, width - 2, height - 2, well, HUD_EDGE, DARK_EDGE, 1);
    }

    public static void slot(final GuiGraphicsExtractor graphics, final float x, final float y,
                            final float width, final float height, final Color well, final boolean highlighted) {
        bevel(graphics, x, y, width, height, well, SLOT_SHADOW, HUD_EDGE, 2);
        if (highlighted)
            stroke(graphics, x, y, width, height, 1, FOCUS);
    }

    public static void button(final GuiGraphicsExtractor graphics, final int x, final int y,
                              final int width, final int height, final boolean active, final boolean focused, final boolean selected) {
        final Color face = !active ? new Color(52, 51, 60) : selected ? new Color(75, 92, 139)
                : focused ? new Color(121, 123, 140) : new Color(99, 99, 106);
        rect(graphics, x, y, width, height, OUTLINE);
        bevel(graphics, x + 1, y + 1, width - 2, height - 2, face,
                active ? new Color(177, 177, 190) : DARK_EDGE, new Color(42, 40, 50), 1);

        if (active && (focused || selected))
            stroke(graphics, x, y, width, height, 1, selected ? FOCUS : LIGHT_EDGE);
    }

    public static void meter(final GuiGraphicsExtractor graphics, final float x, final float y,
                             final float width, final float height, final double fraction, final Color fill) {
        rect(graphics, x, y, width, height, OUTLINE);
        rect(graphics, x + 1, y + 1, width - 2, height - 2, SLOT_SHADOW);
        final float filled = (float) ((width - 2) * Math.clamp(fraction, 0, 1));
        rect(graphics, x + 1, y + 1, filled, height - 2, fill);
        rect(graphics, x + 1, y + 1, filled, 1, new Color(255, 255, 255, 72));
    }

    private static void bevel(final GuiGraphicsExtractor graphics, final float x, final float y,
                              final float width, final float height, final Color face, final Color light, final Color dark, final int edge) {
        rect(graphics, x, y, width, height, face);
        rect(graphics, x, y, width - edge, edge, light);
        rect(graphics, x, y + edge, edge, height - 2 * edge, light);
        rect(graphics, x + edge, y + height - edge, width - edge, edge, dark);
        rect(graphics, x + width - edge, y, edge, height, dark);
    }

    public static void text(final GuiGraphicsExtractor graphics, final String text, final float x,
                            final float y, final float size, final Color color) {
        drawText(graphics, text, x, y, textScale(size), color);
    }

    public static void centered(final GuiGraphicsExtractor graphics, final String text, final float x, final float y,
                                final float width, final float height, final Color color) {
        final Font font = Minecraft.getInstance().font;
        final int textWidth = font.width(text);
        final float scale = Math.min(1, (width - 6) / Math.max(1, textWidth));
        if (scale <= 0) return;
        drawText(graphics, text, x + (width - textWidth * scale) / 2, y + (height - font.lineHeight * scale) / 2, scale, color);
    }

    public static float width(final String text, final float size) {
        return Minecraft.getInstance().font.width(text) * textScale(size);
    }

    public static void wrap(final GuiGraphicsExtractor graphics, final String text, final float x, final float y,
                            final float width, final float size, final int maxLines, final Color color) {
        if (maxLines <= 0 || width <= 0) return;
        final Font font = Minecraft.getInstance().font;
        final float scale = textScale(size);
        final var lines = font.split(Component.literal(text), Math.max(1, (int) (width / scale)));
        graphics.pose().pushMatrix();
        graphics.pose().translate(Math.round(x), Math.round(y));
        graphics.pose().scale(scale, scale);
        for (int line = 0; line < Math.min(maxLines, lines.size()); line++)
            graphics.text(font, lines.get(line), 0, Math.round(line * (size + 3) / scale), color.getRGB());
        graphics.pose().popMatrix();
    }

    private static void drawText(final GuiGraphicsExtractor graphics, final String text, final float x, final float y,
                                 final float scale, final Color color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(Math.round(x), Math.round(y));
        graphics.pose().scale(scale, scale);
        graphics.text(Minecraft.getInstance().font, text, 0, 0, color.getRGB(),
                color.getRed() + color.getGreen() + color.getBlue() > 330);
        graphics.pose().popMatrix();
    }

    private static float textScale(final float size) {
        return Math.max(0.85F, size / 10);
    }
}
