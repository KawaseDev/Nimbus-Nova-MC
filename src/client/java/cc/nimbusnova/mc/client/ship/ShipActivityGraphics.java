package cc.nimbusnova.mc.client.ship;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.ship.ShipPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class ShipActivityGraphics implements ShipPalette {
    private ShipActivityGraphics() {
        /* w */
    }

    static void rations(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width) {
        NovaRenderer.text(graphics, "Meter water into each pouch; seal only the requested amounts.", x, y, 8, MUTED);
        final int spacing = (width - 12) / 3;
        for (int pouch = 0; pouch < 3; pouch++) {
            final int amount = state.board() >> pouch * 3 & 7, target = state.aux() >> pouch * 3 & 7;
            final float left = x + pouch * (spacing + 6), bodyY = y + 15;
            NovaRenderer.rect(graphics, left, bodyY, spacing, 45, 5, SURFACE);
            NovaRenderer.stroke(graphics, left + 6, bodyY + 5, 26, 33, 4, 1, BORDER);
            if (amount > 0)
                NovaRenderer.rect(graphics, left + 8, bodyY + 36 - amount * 9, 22, amount * 9, 2, ACCENT);
            final float targetY = bodyY + 36 - target * 9;
            NovaRenderer.rect(graphics, left + 4, targetY, 30, 1, 0, WARNING);
            NovaRenderer.text(graphics, "Crew " + (pouch + 1), left + 43, bodyY + 6, 8, TEXT);
            NovaRenderer.text(graphics, amount + " / " + target + " units", left + 43, bodyY + 20, 8,
                    amount == target ? SUCCESS : MUTED);
        }
    }

    static void plants(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width) {
        NovaRenderer.text(graphics, "Pour adds +2 here and +1 to the next bed. Keep all beds at 3-5.", x, y, 8, MUTED);
        final int bedWidth = (width - 6) / 2;
        for (int bed = 0; bed < 4; bed++) {
            final int column = bed == 1 || bed == 2 ? 1 : 0, row = bed >= 2 ? 1 : 0;
            final float left = x + column * (bedWidth + 6), top = y + 15 + row * 23;
            final int moisture = state.board() >> bed * 3 & 7;
            NovaRenderer.rect(graphics, left, top, bedWidth, 20, 4, SURFACE);
            NovaRenderer.text(graphics, "Bed " + (bed + 1), left + 6, top + 5, 7, TEXT);
            final float barX = left + 42, barWidth = bedWidth - 65;
            NovaRenderer.rect(graphics, barX, top + 6, barWidth, 8, 2, BACKGROUND);
            NovaRenderer.rect(graphics, barX + barWidth * 3 / 7F, top + 5, barWidth * 2 / 7F, 10, 1, BORDER);
            if (moisture > 0)
                NovaRenderer.rect(graphics, barX, top + 7, barWidth * moisture / 7F, 6, 1,
                        moisture >= 3 && moisture <= 5 ? SUCCESS : moisture > 5 ? WARNING : ACCENT);
            NovaRenderer.text(graphics, Integer.toString(moisture), left + bedWidth - 14, top + 5, 7, MUTED);
        }
    }

    static void instruments(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width) {
        NovaRenderer.text(graphics, "Scan all sensor leads. Replace the noisy lead, then self-test.", x, y, 8, MUTED);
        final int spacing = (width - 12) / 3;
        final double time = System.nanoTime() / 1_000_000_000.0;
        for (int lead = 0; lead < 3; lead++) {
            final float left = x + lead * (spacing + 6), top = y + 15;
            final boolean scanned = (state.board() & 1 << lead) != 0;
            final int quality = state.aux() >> lead * 7 & 127;
            NovaRenderer.rect(graphics, left, top, spacing, 44, 4, SURFACE);
            NovaRenderer.text(graphics, "Lead " + (lead + 1), left + 5, top + 3, 7, TEXT);
            if (!scanned) {
                NovaRenderer.text(graphics, "Awaiting scan", left + 6, top + 23, 7, MUTED);
                continue;
            }
            final float baseline = top + 25;
            for (int point = 0; point < 24; point++) {
                final float first = point / 24F, next = (point + 1) / 24F;
                final double frequency = quality < 40 ? 37 : 10;
                final float firstY = baseline + (float) Math.sin(first * frequency + time * 2) * (quality < 40 ? 8 : 3);
                final float nextY = baseline + (float) Math.sin(next * frequency + time * 2) * (quality < 40 ? 8 : 3);
                ShipPuzzleGraphics.line(graphics, left + 5 + first * (spacing - 10), firstY,
                        left + 5 + next * (spacing - 10), nextY, 0.8F, quality < 40 ? WARNING : SUCCESS);
            }
            NovaRenderer.text(graphics, "Signal " + quality, left + 5, top + 34, 6, quality < 40 ? WARNING : MUTED);
        }
    }

    static void cargo(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width) {
        final int mass = state.aux() & 15, maximum = state.aux() >> 4 & 15;
        NovaRenderer.text(graphics, "Pack at least two bays. Mass " + mass + " / " + maximum + "; left/right difference <=2.", x, y, 8,
                mass <= maximum ? MUTED : WARNING);
        NovaRenderer.rect(graphics, x, y + 70, width, 4, 2, SURFACE);
        if (mass > 0)
            NovaRenderer.rect(graphics, x, y + 70, width * Math.min(mass, maximum) / (float) maximum, 4, 2,
                    mass <= maximum ? ACCENT : WARNING);
        NovaRenderer.text(graphics, "Tap a bay to cycle: empty / spares / medical kit / science.", x, y + 77, 7, MUTED);
    }

    static void cargoBay(final GuiGraphicsExtractor graphics, final int cargo, final int x, final int y, final int width,
                         final int height, final int bay, final boolean hover) {
        final String[] names = { "Empty", "Spares", "Medical kit", "Science" };
        final int[] mass = { 0, 2, 3, 4 };
        NovaRenderer.rect(graphics, x, y, width, height, 5, SURFACE);
        NovaRenderer.stroke(graphics, x, y, width, height, 5, hover ? 1.3F : 0.6F, hover ? ACCENT : BORDER);
        NovaRenderer.text(graphics, new String[] { "LEFT BAY", "CENTER BAY", "RIGHT BAY" }[bay], x + 6, y + 4, 6, MUTED);
        if (cargo > 0) {
            final float crateX = x + 7, crateY = y + 19;
            NovaRenderer.rect(graphics, crateX + 2, crateY - 2, 19, 16, 2, BORDER);
            NovaRenderer.rect(graphics, crateX, crateY, 19, 16, 2, cargo == 2 ? SUCCESS : cargo == 3 ? ACCENT : WARNING);
            NovaRenderer.rect(graphics, crateX + 7, crateY, 4, 16, 0, SURFACE);
        }
        NovaRenderer.text(graphics, names[cargo], x + 34, y + 18, 7, TEXT);
        NovaRenderer.text(graphics, mass[cargo] + " mass", x + 34, y + 30, 7, MUTED);
    }

    static void specimen(final GuiGraphicsExtractor graphics, final int classification, final int x, final int y,
                         final int width, final int height, final int sample, final int selectedClass, final boolean hover) {
        final String[] classes = { "Unclassified", "Ferrous", "Layered", "Porous" };
        NovaRenderer.rect(graphics, x, y, width, height, 5, SURFACE);
        NovaRenderer.stroke(graphics, x, y, width, height, 5, hover ? 1.3F : 0.6F, hover ? ACCENT : BORDER);
        final float rockX = x + 7, rockY = y + 10;
        NovaRenderer.rect(graphics, rockX, rockY, 15, 18, 5, BORDER);
        if (sample == 0) {
            for (int pore = 0; pore < 4; pore++) {
                final int row = pore / 2;
                NovaRenderer.rect(graphics, rockX + 3 + pore % 2 * 6, rockY + 4 + row * 7, 3, 3, 1, BACKGROUND);
            }
        } else if (sample == 1) {
            NovaRenderer.rect(graphics, rockX + 3, rockY + 2, 8, 13, 1, TEXT);
            ShipPuzzleGraphics.line(graphics, rockX + 14, rockY + 5, rockX + 19, rockY + 1, 1, WARNING);
        } else {
            for (int layer = 0; layer < 3; layer++)
                NovaRenderer.rect(graphics, rockX + 1, rockY + 4 + layer * 5, 13, 2, 0, MUTED);
        }
        NovaRenderer.text(graphics, "Sample " + (char) ('A' + sample), x + 28, y + 4, 7, TEXT);
        NovaRenderer.text(graphics, new String[] { "Pores / no magnet", "Dense / magnetic", "Layers / weak magnet" }[sample], x + 28, y + 16, 6, MUTED);
        NovaRenderer.text(graphics, classes[classification], x + 28, y + 30, 7, classification == selectedClass ? ACCENT : TEXT);
    }

    static void intercom(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width) {
        if (state.task().equals("EXERCISE")) {
            NovaRenderer.wrap(graphics, "Close the tablet and punch the bag five times. Pace the hits.", x, y, width, 10, 2, TEXT);
            for (int hit = 0; hit < 5; hit++)
                NovaRenderer.rect(graphics, x + 8 + hit * 38, y + 40, 29, 13, 4, hit < state.board() ? SUCCESS : SURFACE);
            NovaRenderer.text(graphics, "Controlled repetitions " + state.board() + " / 5", x, y + 65, 9, ACCENT);
            return;
        }
        NovaRenderer.wrap(graphics, "Call a short crew discussion. Ship reserves freeze while you compare readings.", x, y, width, 10, 3, TEXT);
        for (int bar = 0; bar < 22; bar++) {
            final float height = 3 + (float) Math.abs(Math.sin(System.nanoTime() / 500_000_000.0 + bar * 0.6)) * 17;
            NovaRenderer.rect(graphics, x + 5 + bar * 6, y + 45 - height / 2, 3, height, 1, ACCENT);
        }
    }
}
