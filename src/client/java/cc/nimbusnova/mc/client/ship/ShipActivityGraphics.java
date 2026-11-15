package cc.nimbusnova.mc.client.ship;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.ship.ShipPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.awt.Color;

final class ShipActivityGraphics implements ShipPalette {
    private static final String[] BAY_LABELS = { "LEFT BAY", "CENTER BAY", "RIGHT BAY" };
    private static final String[] CARGO_LABELS = { "Empty", "Spares", "Medical kit", "Science" };
    private static final int[] CARGO_MASS = { 0, 2, 3, 4 };
    private static final ItemStack[] CARGO_ICONS = { ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT),
            new ItemStack(Items.POTION), new ItemStack(Items.AMETHYST_SHARD) };
    private static final Color WATER = new Color(86, 170, 255);

    private ShipActivityGraphics() {
        /* w */
    }

    static void rations(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width) {
        NovaRenderer.text(graphics, "Fill each pouch to its yellow line, then seal.", x, y, 8, MUTED);
        final int spacing = (width - 12) / 3;
        for (int pouch = 0; pouch < 3; pouch++) {
            final int amount = state.board() >> pouch * 3 & 7, target = state.aux() >> pouch * 3 & 7;
            final float left = x + pouch * (spacing + 6), bodyY = y + 13;
            final boolean matched = amount == target;
            NovaRenderer.slot(graphics, left, bodyY, spacing, 47, SURFACE, false);
            NovaRenderer.rect(graphics, left + 12, bodyY + 3, 10, 4, matched ? SUCCESS : BORDER);
            NovaRenderer.rect(graphics, left + 6, bodyY + 7, 22, 35, new Color(5, 7, 13));
            NovaRenderer.rect(graphics, left + 7, bodyY + 8, 20, 33, new Color(20, 30, 50));
            if (amount > 0) {
                NovaRenderer.gradient(graphics, left + 7, bodyY + 41 - amount * 11, 20, amount * 11, NovaRenderer.mix(WATER, Color.WHITE, 0.3F), WATER);
                NovaRenderer.rect(graphics, left + 7, bodyY + 41 - amount * 11 + (NovaRenderer.pulse(3) > 0.5F ? 0 : 1), 20, 1, new Color(220, 240, 255));
            }

            NovaRenderer.rect(graphics, left + 3, bodyY + 41 - target * 11, 28, 1, WARNING);
            NovaRenderer.text(graphics, "Crew " + (pouch + 1), left + 36, bodyY + 6, 8, TEXT);
            NovaRenderer.text(graphics, amount + " / " + target, left + 36, bodyY + 18, 8, matched ? SUCCESS : amount > target ? WARNING : MUTED);
            NovaRenderer.text(graphics, matched ? "exact" : amount > target ? "too much" : "add water", left + 36, bodyY + 30, 8, matched ? SUCCESS : MUTED);
        }
    }

    static void plants(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width) {
        NovaRenderer.text(graphics, "Water spills into the next bed. Keep all four green.", x, y, 8, MUTED);
        final int bedWidth = (width - 18) / 4;
        for (int bed = 0; bed < 4; bed++) {
            final float left = x + bed * (bedWidth + 6), top = y + 13;
            final int moisture = state.board() >> bed * 3 & 7;
            final boolean healthy = moisture >= 3 && moisture <= 5, wet = moisture > 5;
            final Color leaf = healthy ? SUCCESS : wet ? new Color(96, 150, 170) : new Color(170, 140, 80);
            NovaRenderer.slot(graphics, left, top, bedWidth, 47, SURFACE, false);

            final float soilY = top + 30, centerX = left + bedWidth / 2F;
            NovaRenderer.rect(graphics, left + 4, soilY, bedWidth - 8, 6, new Color(96, 66, 44));
            NovaRenderer.rect(graphics, left + 4, soilY, (bedWidth - 8) * moisture / 7F, 6, NovaRenderer.alpha(WATER, 170));
            NovaRenderer.rect(graphics, left + 3 + (bedWidth - 8) * 3 / 7F, soilY + 6, (bedWidth - 8) * 2 / 7F + 2, 2, SUCCESS);

            final int stem = healthy ? 15 : 9;
            NovaRenderer.rect(graphics, centerX - 1, soilY - stem, 2, stem, leaf);
            NovaRenderer.rect(graphics, centerX - 6, soilY - stem + (healthy ? 2 : 5), 5, 3, leaf);
            NovaRenderer.rect(graphics, centerX + 1, soilY - stem + (healthy ? 5 : 3), 5, 3, leaf);
            if (healthy)
                NovaRenderer.rect(graphics, centerX - 2, soilY - stem - 3, 4, 4, NovaRenderer.mix(SUCCESS, Color.WHITE, 0.4F));

            NovaRenderer.text(graphics, "Bed " + (bed + 1), left + 4, top + 3, 8, TEXT);
            final String label = healthy ? "good " + moisture : wet ? "soggy " + moisture : "dry " + moisture;
            NovaRenderer.text(graphics, label, left + bedWidth - 4 - NovaRenderer.width(label, 8), top + 38, 8, healthy ? SUCCESS : WARNING);
            if (bed < 3)
                NovaRenderer.text(graphics, ">", left + bedWidth, top + 28, 8, NovaRenderer.alpha(WATER, 200));
        }
    }

    static void instruments(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width) {
        NovaRenderer.text(graphics, "Scan every lead. Replace only the noisy one.", x, y, 8, MUTED);
        final int spacing = (width - 12) / 3;
        final double time = NovaRenderer.seconds();
        for (int lead = 0; lead < 3; lead++) {
            final float left = x + lead * (spacing + 6), top = y + 13;
            final boolean scanned = (state.board() & 1 << lead) != 0;
            final int quality = state.aux() >> lead * 7 & 127;
            final boolean noisy = quality < 40;
            NovaRenderer.slot(graphics, left, top, spacing, 47, new Color(6, 14, 24), false);
            NovaRenderer.text(graphics, "Lead " + (lead + 1), left + 5, top + 4, 8, TEXT);
            if (!scanned) {
                NovaRenderer.text(graphics, "not scanned", left + 5, top + 22, 8, MUTED);
                continue;
            }

            final float baseline = top + 26;
            NovaRenderer.rect(graphics, left + 4, baseline, spacing - 8, 1, new Color(40, 51, 75));
            for (int point = 0; point < 28; point++) {
                final float first = point / 28F, next = (point + 1) / 28F;
                final double frequency = noisy ? 37 : 10;
                final float firstY = baseline + (float) Math.sin(first * frequency + time * 3) * (noisy ? 8 : 3)
                        + (noisy ? (float) Math.sin(first * 91 + time * 11) * 3 : 0);
                final float nextY = baseline + (float) Math.sin(next * frequency + time * 3) * (noisy ? 8 : 3)
                        + (noisy ? (float) Math.sin(next * 91 + time * 11) * 3 : 0);
                ShipPuzzleGraphics.line(graphics, left + 5 + first * (spacing - 10), firstY,
                        left + 5 + next * (spacing - 10), nextY, 1, noisy ? WARNING : SUCCESS);
            }

            final String label = (noisy ? "NOISY " : "CLEAN ") + quality + "%";
            NovaRenderer.text(graphics, label, left + spacing - 5 - NovaRenderer.width(label, 8), top + 4, 8, noisy ? WARNING : SUCCESS);
        }
    }

    static void cargo(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width) {
        final int mass = state.aux() & 15, maximum = state.aux() >> 4 & 15;
        final int left = CARGO_MASS[state.board() & 3], right = CARGO_MASS[state.board() >> 4 & 3];
        final boolean balanced = Math.abs(left - right) <= 2, light = mass <= maximum;
        NovaRenderer.text(graphics, "Click a bay to change its cargo.", x, y, 8, MUTED);

        final float beamX = x + width - 74, beamY = y + 5;
        graphics.pose().pushMatrix();
        graphics.pose().translate(beamX + 34, beamY);
        graphics.pose().rotate((float) Math.toRadians(Math.clamp((right - left) * 5, -14, 14)));
        NovaRenderer.rect(graphics, -30, -1, 60, 2, balanced ? SUCCESS : WARNING);
        NovaRenderer.rect(graphics, -32, -4, 5, 5, balanced ? SUCCESS : WARNING);
        NovaRenderer.rect(graphics, 27, -4, 5, 5, balanced ? SUCCESS : WARNING);
        graphics.pose().popMatrix();
        NovaRenderer.rect(graphics, beamX + 32, beamY, 4, 6, MUTED);

        final String load = "Mass " + mass + "/" + maximum;
        NovaRenderer.text(graphics, load, x, y + 72, 8, light ? TEXT : WARNING);
        NovaRenderer.meter(graphics, x + 62, y + 73, width - 150, 6, (double) mass / Math.max(1, maximum), light ? ACCENT : WARNING);
        final String balance = balanced ? "BALANCED" : "TILTING";
        NovaRenderer.text(graphics, balance, x + width - NovaRenderer.width(balance, 8), y + 72, 8, balanced ? SUCCESS : WARNING);
    }

    static void cargoBay(final GuiGraphicsExtractor graphics, final int cargo, final int x, final int y, final int width,
                         final int height, final int bay, final boolean hover) {
        NovaRenderer.slot(graphics, x, y, width, height, SURFACE, hover);
        NovaRenderer.text(graphics, BAY_LABELS[bay], x + 6, y + 5, 8, MUTED);
        NovaRenderer.slot(graphics, x + 6, y + 17, 22, 22, BACKGROUND, false);
        graphics.item(CARGO_ICONS[cargo], x + 9, y + 20);
        NovaRenderer.text(graphics, CARGO_LABELS[cargo], x + 33, y + 18, 8, cargo == 0 ? MUTED : TEXT);
        for (int unit = 0; unit < 4; unit++)
            NovaRenderer.rect(graphics, x + 33 + unit * 7, y + 31, 5, 5, unit < CARGO_MASS[cargo] ? WARNING : new Color(8, 11, 19));
    }

    static void specimen(final GuiGraphicsExtractor graphics, final int classification, final int x, final int y,
                         final int width, final int height, final int sample, final int selectedClass, final boolean hover) {
        final String[] classes = { "tap to sort", "Ferrous", "Layered", "Porous" };
        NovaRenderer.slot(graphics, x, y, width, height, SURFACE, hover);
        final float rockX = x + 6, rockY = y + 8;
        NovaRenderer.chamfer(graphics, rockX - 1, rockY - 1, 20, 24, 4, new Color(5, 7, 13));
        NovaRenderer.chamfer(graphics, rockX, rockY, 18, 22, 4, sample == 1 ? new Color(92, 98, 112) : sample == 0 ? new Color(120, 104, 92) : new Color(140, 120, 88));
        if (sample == 0) {
            for (int pore = 0; pore < 6; pore++)
                NovaRenderer.rect(graphics, rockX + 3 + pore % 2 * 7 + pore / 2 % 2 * 2, rockY + 3 + pore / 2 * 6, 3, 3, new Color(40, 32, 30));
        } else if (sample == 1) {
            NovaRenderer.rect(graphics, rockX + 4, rockY + 4, 4, 3, new Color(190, 198, 214));
            NovaRenderer.rect(graphics, rockX + 10, rockY + 12, 4, 3, new Color(190, 198, 214));
            NovaRenderer.rect(graphics, rockX + 19, rockY + 2, 3, 8, new Color(255, 107, 107));
            NovaRenderer.rect(graphics, rockX + 19, rockY + 10, 3, 8, new Color(210, 218, 232));
        } else {
            for (int layer = 0; layer < 4; layer++)
                NovaRenderer.rect(graphics, rockX + 1, rockY + 3 + layer * 5, 16, 2, layer % 2 == 0 ? new Color(196, 170, 120) : new Color(96, 80, 60));
        }

        NovaRenderer.text(graphics, "Sample " + (char) ('A' + sample), x + 29, y + 5, 8, TEXT);
        NovaRenderer.text(graphics, new String[] { "holes, no pull", "heavy, magnetic", "striped bands" }[sample], x + 29, y + 17, 8, MUTED);
        NovaRenderer.text(graphics, classes[classification], x + 29, y + 30, 8, classification == 0 ? WARNING : classification == selectedClass ? ACCENT : TEXT);
    }

    static void intercom(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width) {
        if (state.task().equals("EXERCISE")) {
            NovaRenderer.wrap(graphics, "Close the tablet and punch the bag five times. Pace the hits.", x, y, width, 10, 2, TEXT);
            for (int hit = 0; hit < 5; hit++)
                NovaRenderer.slot(graphics, x + hit * 36, y + 34, 31, 15, hit < state.board() ? SUCCESS : SURFACE, false);

            NovaRenderer.text(graphics, "Controlled repetitions " + state.board() + " / 5", x, y + 58, 8, ACCENT);
            return;
        }

        NovaRenderer.wrap(graphics, state.phase().equals("READY") ? "Test the intercom. Crew discussions become available after launch."
                : "Call a short crew discussion. Ship reserves freeze while you compare readings.", x, y, width, 10, 2, TEXT);
        NovaRenderer.slot(graphics, x, y + 30, width, 30, new Color(6, 14, 24), false);
        final int bars = (width - 8) / 5;
        for (int bar = 0; bar < bars; bar++) {
            final float height = 3 + (float) Math.abs(Math.sin(NovaRenderer.seconds() * 2 + bar * 0.6)) * 9
                    + (float) Math.abs(Math.sin(NovaRenderer.seconds() * 5.3 + bar * 1.7)) * 6;
            NovaRenderer.rect(graphics, x + 5 + bar * 5, y + 45 - height / 2, 3, height, NovaRenderer.alpha(ACCENT, 120 + bar * 7 % 120));
        }
    }
}
