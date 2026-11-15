package cc.nimbusnova.mc.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.awt.Color;

/**
 * Shared terminal styling: chamfered dark consoles, pixel-snapped Minecraft text and pressable square controls.
 */
public final class NovaRenderer {
    public enum Tone {
        NORMAL, PRIMARY, DANGER, SUCCESS
    }

    private static final Color SHADOW = new Color(0, 0, 0, 110);
    private static final Color FRAME = new Color(5, 7, 13);
    private static final Color EDGE = new Color(52, 66, 96);
    private static final Color EDGE_LIGHT = new Color(88, 108, 148);
    private static final Color BODY_TOP = new Color(22, 29, 46, 248);
    private static final Color BODY_BOTTOM = new Color(11, 15, 26, 250);
    private static final Color HEADER_TOP = new Color(34, 45, 70);
    private static final Color HEADER_BOTTOM = new Color(24, 32, 51);
    private static final Color WELL = new Color(8, 11, 19);
    private static final Color WELL_EDGE = new Color(40, 51, 75);
    private static final Color TEXT = new Color(238, 242, 250);
    private static final Color MUTED = new Color(122, 134, 162);
    private static final Color AMBER = new Color(255, 200, 87);
    private static final Color CYAN = new Color(95, 225, 240);
    private static final Color RED = new Color(255, 107, 107);
    private static final Color GREEN = new Color(123, 227, 138);

    private static long openedAt = System.nanoTime();

    private NovaRenderer() {
        /* w */
    }

    public static void opened() {
        openedAt = System.nanoTime();
    }

    public static double seconds() {
        return System.nanoTime() / 1_000_000_000.0;
    }

    /** A 0..1 triangle-free pulse used for alarms and selection; frozen when screen effects are off. */
    public static float pulse(final double speed) {
        if (Minecraft.getInstance().options.screenEffectScale().get() <= 0) return 0.5F;
        return (float) (0.5 + 0.5 * Math.sin(seconds() * speed));
    }

    public static Color alpha(final Color color, final int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp(alpha, 0, 255));
    }

    public static Color mix(final Color from, final Color to, final float amount) {
        final float clamped = Math.clamp(amount, 0, 1);
        return new Color(Math.round(from.getRed() + (to.getRed() - from.getRed()) * clamped),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * clamped),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * clamped),
                Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * clamped));
    }

    public static void rect(final GuiGraphicsExtractor graphics, final float x, final float y,
                            final float width, final float height, final Color color) {
        final int left = Math.round(x), top = Math.round(y), right = Math.round(x + width), bottom = Math.round(y + height);
        if (right > left && bottom > top)
            graphics.fill(left, top, right, bottom, color.getRGB());
    }

    public static void gradient(final GuiGraphicsExtractor graphics, final float x, final float y,
                                final float width, final float height, final Color top, final Color bottom) {
        final int left = Math.round(x), upper = Math.round(y), right = Math.round(x + width), lower = Math.round(y + height);
        if (right > left && lower > upper)
            graphics.fillGradient(left, upper, right, lower, top.getRGB(), bottom.getRGB());
    }

    public static void stroke(final GuiGraphicsExtractor graphics, final float x, final float y,
                              final float width, final float height, final float thickness, final Color color) {
        final int edge = Math.max(1, Math.round(thickness));
        rect(graphics, x, y, width, edge, color);
        rect(graphics, x, y + height - edge, width, edge, color);
        rect(graphics, x, y + edge, edge, height - edge * 2, color);
        rect(graphics, x + width - edge, y + edge, edge, height - edge * 2, color);
    }

    /** A rectangle with 45-degree pixel corners cut by {@code cut} pixels. */
    public static void chamfer(final GuiGraphicsExtractor graphics, final float x, final float y,
                               final float width, final float height, final int cut, final Color color) {
        for (int step = 0; step < cut; step++)
            rect(graphics, x + cut - step, y + step, width - (cut - step) * 2, 1, color);

        rect(graphics, x, y + cut, width, height - cut * 2, color);
        for (int step = 0; step < cut; step++)
            rect(graphics, x + step + 1, y + height - cut + step, width - (step + 1) * 2, 1, color);
    }

    public static void brackets(final GuiGraphicsExtractor graphics, final float x, final float y,
                                final float width, final float height, final int length, final Color color) {
        rect(graphics, x, y, length, 1, color);
        rect(graphics, x, y, 1, length, color);
        rect(graphics, x + width - length, y, length, 1, color);
        rect(graphics, x + width - 1, y, 1, length, color);
        rect(graphics, x, y + height - 1, length, 1, color);
        rect(graphics, x, y + height - length, 1, length, color);
        rect(graphics, x + width - length, y + height - 1, length, 1, color);
        rect(graphics, x + width - 1, y + height - length, 1, length, color);
    }

    public static void window(final GuiGraphicsExtractor graphics, final int x, final int y,
                              final int width, final int height, final Color well) {
        chamfer(graphics, x + 3, y + 4, width, height, 4, SHADOW);
        chamfer(graphics, x, y, width, height, 4, FRAME);
        chamfer(graphics, x + 1, y + 1, width - 2, height - 2, 3, EDGE);
        chamfer(graphics, x + 2, y + 2, width - 4, height - 4, 3, FRAME);
        gradient(graphics, x + 3, y + 3, width - 6, height - 6, alpha(mix(BODY_TOP, well, 0.25F), 248), BODY_BOTTOM);

        final Color grid = new Color(120, 150, 210, 9);
        for (int column = x + 19; column < x + width - 4; column += 16)
            rect(graphics, column, y + 3, 1, height - 6, grid);

        for (int row = y + 19; row < y + height - 4; row += 16)
            rect(graphics, x + 3, row, width - 6, 1, grid);

        rect(graphics, x + 5, y + 2, width - 10, 1, EDGE_LIGHT);
        brackets(graphics, x + 3, y + 3, width - 6, height - 6, 7, alpha(CYAN, 150));

        final float age = (float) ((System.nanoTime() - openedAt) / 1_000_000_000.0);
        if (age < 0.45F && Minecraft.getInstance().options.screenEffectScale().get() > 0) {
            final float sweep = y + 3 + (height - 6) * (age / 0.45F);
            gradient(graphics, x + 3, sweep - 18, width - 6, 18, alpha(CYAN, 0), alpha(CYAN, Math.round(46 * (1 - age / 0.45F))));
            rect(graphics, x + 3, sweep, width - 6, 1, alpha(CYAN, Math.round(150 * (1 - age / 0.45F))));
        }
    }

    public static void title(final GuiGraphicsExtractor graphics, final int x, final int y, final int width, final String title) {
        gradient(graphics, x + 4, y + 4, width - 8, 25, HEADER_TOP, HEADER_BOTTOM);
        rect(graphics, x + 4, y + 29, width - 8, 1, EDGE);
        rect(graphics, x + 4, y + 29, Math.min(width - 8, 64), 1, AMBER);
        rect(graphics, x + 9, y + 10, 3, 13, AMBER);
        rect(graphics, x + 9, y + 10, 3, 1, new Color(255, 236, 180));
        final float available = width - 52;
        final Font font = Minecraft.getInstance().font;
        String visible = title;
        while (visible.length() > 3 && font.width(visible) > available)
            visible = visible.substring(0, visible.length() - 2);
        if (!visible.equals(title))
            visible = visible.substring(0, visible.length() - 1) + "...";

        graphics.text(font, visible, x + 18, y + 13, TEXT.getRGB(), true);
    }

    public static void hud(final GuiGraphicsExtractor graphics, final float x, final float y,
                           final float width, final float height, final Color well) {
        chamfer(graphics, x + 2, y + 2, width, height, 2, SHADOW);
        chamfer(graphics, x, y, width, height, 2, FRAME);
        chamfer(graphics, x + 1, y + 1, width - 2, height - 2, 2, EDGE);
        gradient(graphics, x + 2, y + 2, width - 4, height - 4, alpha(mix(BODY_TOP, well, 0.3F), 236), alpha(BODY_BOTTOM, 236));
        rect(graphics, x + 3, y + 1, width - 6, 1, EDGE_LIGHT);
    }

    /** Draws the HUD task card and returns the y coordinate for its footer hint. */
    public static int objective(final GuiGraphicsExtractor graphics, final int x, final int y, final int width, final String title,
                                final String badge, final String instruction, final Color accent) {
        final int lines = Math.min(3, lines(instruction, width - 20, 8)), height = 37 + lines * 11;
        hud(graphics, x, y, width, height, WELL);
        rect(graphics, x + 2, y + 3, 2, height - 6, accent);
        chip(graphics, badge, x + width - 6 - chipWidth(badge), y + 5, accent);

        final Font font = Minecraft.getInstance().font;
        String heading = title;
        while (heading.length() > 4 && font.width(heading) > width - 22 - chipWidth(badge))
            heading = heading.substring(0, heading.length() - 1);

        text(graphics, heading.equals(title) ? title : heading.trim() + "..", x + 9, y + 7, 10, TEXT);
        wrap(graphics, instruction, x + 9, y + 20, width - 20, 8, lines, new Color(206, 213, 230));
        rect(graphics, x + 9, y + 20 + lines * 11, width - 18, 1, new Color(64, 80, 114, 120));
        return y + 25 + lines * 11;
    }

    public static void slot(final GuiGraphicsExtractor graphics, final float x, final float y,
                            final float width, final float height, final Color well, final boolean highlighted) {
        rect(graphics, x, y, width, height, highlighted ? AMBER : WELL_EDGE);
        rect(graphics, x + 1, y + 1, width - 2, height - 2, mix(WELL, well, 0.35F));
        rect(graphics, x + 1, y + 1, width - 2, 1, new Color(0, 0, 0, 120));
        rect(graphics, x + 1, y + height - 2, width - 2, 1, new Color(255, 255, 255, 14));
        if (highlighted)
            rect(graphics, x + 1, y + 1, width - 2, height - 2, alpha(AMBER, 22));
    }

    /** A labelled inset area used to group readings inside a console. */
    public static void section(final GuiGraphicsExtractor graphics, final float x, final float y,
                               final float width, final float height, final String label, final Color accent) {
        slot(graphics, x, y, width, height, WELL, false);
        if (label.isEmpty()) return;
        final float labelWidth = Minecraft.getInstance().font.width(label) + 8;
        rect(graphics, x + 5, y - 4, labelWidth, 9, FRAME);
        rect(graphics, x + 5, y - 4, 2, 9, accent);
        text(graphics, label, x + 9, y - 3, 8, accent);
    }

    /** A short teaching card: one coloured heading and a few wrapped lines. */
    public static void callout(final GuiGraphicsExtractor graphics, final float x, final float y, final float width, final float height,
                               final String heading, final String body, final Color accent) {
        rect(graphics, x, y, width, height, alpha(accent, 26));
        rect(graphics, x, y, 2, height, accent);
        text(graphics, heading, x + 7, y + 4, 8, accent);
        wrap(graphics, body, x + 7, y + 15, width - 12, 8, Math.max(0, (int) ((height - 15) / 11)), new Color(214, 221, 236));
    }

    public static void chip(final GuiGraphicsExtractor graphics, final String label, final float x, final float y, final Color accent) {
        final float labelWidth = Minecraft.getInstance().font.width(label);
        rect(graphics, x, y, labelWidth + 8, 11, alpha(accent, 40));
        rect(graphics, x, y, 2, 11, accent);
        text(graphics, label, x + 5, y + 2, 8, accent);
    }

    public static float chipWidth(final String label) {
        return Minecraft.getInstance().font.width(label) + 8;
    }

    public static void button(final GuiGraphicsExtractor graphics, final int x, final int y,
                              final int width, final int height, final boolean active, final boolean focused, final boolean selected) {
        button(graphics, x, y, width, height, active, focused, selected, Tone.NORMAL);
    }

    public static void button(final GuiGraphicsExtractor graphics, final int x, final int y, final int width, final int height,
                              final boolean active, final boolean focused, final boolean selected, final Tone tone) {
        final Color base = switch (tone) {
            case PRIMARY -> new Color(176, 124, 32);
            case DANGER -> new Color(150, 52, 58);
            case SUCCESS -> new Color(46, 128, 74);
            default -> new Color(44, 56, 84);
        };

        final Color face = !active ? new Color(26, 31, 45) : selected ? mix(base, AMBER, tone == Tone.NORMAL ? 0.42F : 0.2F)
                : focused ? mix(base, Color.WHITE, 0.16F) : base;
        rect(graphics, x, y, width, height, FRAME);
        rect(graphics, x + 1, y + 1, width - 2, height - 2, face);
        if (active) {
            rect(graphics, x + 1, y + 1, width - 2, 1, mix(face, Color.WHITE, 0.32F));
            rect(graphics, x + 1, y + height - 3, width - 2, 2, mix(face, Color.BLACK, 0.45F));
        }

        if (active && (focused || selected))
            stroke(graphics, x, y, width, height, 1, selected ? AMBER : mix(EDGE_LIGHT, Color.WHITE, 0.4F));
    }

    public static void meter(final GuiGraphicsExtractor graphics, final float x, final float y,
                             final float width, final float height, final double fraction, final Color fill) {
        rect(graphics, x, y, width, height, FRAME);
        rect(graphics, x + 1, y + 1, width - 2, height - 2, WELL);
        final float filled = (float) ((width - 2) * Math.clamp(fraction, 0, 1));
        gradient(graphics, x + 1, y + 1, filled, height - 2, mix(fill, Color.WHITE, 0.25F), mix(fill, Color.BLACK, 0.2F));
        if (width < 24) return;

        final Color notch = new Color(5, 7, 13, 150);
        for (float tick = x + 1 + (width - 2) / 10; tick < x + width - 2; tick += (width - 2) / 10)
            rect(graphics, tick, y + 1, 1, height - 2, notch);
    }

    public static void text(final GuiGraphicsExtractor graphics, final String text, final float x,
                            final float y, final float size, final Color color) {
        drawText(graphics, text, x, y, textScale(size), color);
    }

    public static void centered(final GuiGraphicsExtractor graphics, final String text, final float x, final float y,
                                final float width, final float height, final Color color) {
        final Font font = Minecraft.getInstance().font;
        String visible = text;
        while (visible.length() > 2 && font.width(visible) > width - 4)
            visible = visible.substring(0, visible.length() - 1);
        if (!visible.equals(text) && visible.length() > 2)
            visible = visible.substring(0, visible.length() - 1).trim() + ".";

        drawText(graphics, visible, x + (width - font.width(visible)) / 2F, y + (height - (font.lineHeight - 1)) / 2F, 1, color);
    }

    public static float width(final String text, final float size) {
        return Minecraft.getInstance().font.width(text) * textScale(size);
    }

    public static void wrap(final GuiGraphicsExtractor graphics, final String text, final float x, final float y,
                            final float width, final float size, final int maxLines, final Color color) {
        if (maxLines <= 0 || width <= 0 || text.isEmpty()) return;
        final Font font = Minecraft.getInstance().font;
        final float scale = textScale(size);
        final var lines = font.split(Component.literal(text), Math.max(1, (int) (width / scale)));
        final float lineHeight = Math.max(font.lineHeight + 1, Math.round((size + 3) / scale));
        graphics.pose().pushMatrix();
        graphics.pose().translate(Math.round(x), Math.round(y));
        graphics.pose().scale(scale, scale);
        for (int line = 0; line < Math.min(maxLines, lines.size()); line++)
            graphics.text(font, lines.get(line), 0, Math.round(line * lineHeight), color.getRGB(), shadow(color));
        graphics.pose().popMatrix();
    }

    public static int lines(final String text, final float width, final float size) {
        return Minecraft.getInstance().font.split(Component.literal(text), Math.max(1, (int) (width / textScale(size)))).size();
    }

    private static void drawText(final GuiGraphicsExtractor graphics, final String text, final float x, final float y,
                                 final float scale, final Color color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(Math.round(x), Math.round(y));
        graphics.pose().scale(scale, scale);
        graphics.text(Minecraft.getInstance().font, text, 0, 0, color.getRGB(), shadow(color));
        graphics.pose().popMatrix();
    }

    private static boolean shadow(final Color color) {
        return color.getRed() + color.getGreen() + color.getBlue() > 330;
    }

    /**
     * Body sizes stay at the font's native scale; larger headings snap so each font pixel lands on whole
     * framebuffer pixels. Fractional scales smear the pixel font.
     */
    private static float textScale(final float size) {
        if (size < 12.5F) return 1;
        final int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        return Math.max(guiScale, Math.round(size / 10 * guiScale)) / (float) guiScale;
    }
}
