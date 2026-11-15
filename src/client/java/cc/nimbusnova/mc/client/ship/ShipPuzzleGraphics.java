package cc.nimbusnova.mc.client.ship;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.ship.ShipPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

final class ShipPuzzleGraphics implements ShipPalette {
    static final float[] STAR_X = { 0.10F, 0.29F, 0.48F, 0.61F, 0.81F, 0.88F };
    static final float[] STAR_Y = { 0.23F, 0.70F, 0.13F, 0.49F, 0.82F, 0.19F };

    private ShipPuzzleGraphics() {
        /* w */
    }

    static void line(final GuiGraphicsExtractor graphics, final float fromX, final float fromY, final float toX, final float toY,
                     final float thickness, final Color color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(fromX, fromY);
        graphics.pose().rotate((float) Math.atan2(toY - fromY, toX - fromX));
        NovaRenderer.rect(graphics, 0, -thickness / 2, (float) Math.hypot(toX - fromX, toY - fromY), thickness, thickness / 2, color);
        graphics.pose().popMatrix();
    }

    static void docking(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int size) {
        NovaRenderer.rect(graphics, x, y, size, size, 7, SURFACE);
        for (int index = 1; index < 8; index++) {
            final float point = size * index / 8F;
            line(graphics, x + point, y + 5, x + point, y + size - 5, 0.4F, BORDER);
            line(graphics, x + 5, y + point, x + size - 5, y + point, 0.4F, BORDER);
        }

        final float scale = (size - 18) / 8F;
        final int horizontal = state.board() & 15, vertical = state.board() >> 4 & 15;
        final int velocityX = (state.board() >> 8 & 7) - 2, velocityY = (state.board() >> 11 & 7) - 2;
        final float craftX = x + 9 + horizontal * scale, craftY = y + 9 + vertical * scale;
        NovaRenderer.stroke(graphics, x + 9 + 3 * scale, y + 9 + 3 * scale, scale * 2, scale * 2, 4, 1.5F, SUCCESS);
        final float predictedX = x + 9 + Math.clamp(horizontal + velocityX, 0, 8) * scale;
        final float predictedY = y + 9 + Math.clamp(vertical + velocityY, 0, 8) * scale;
        line(graphics, craftX, craftY, predictedX, predictedY, 1.2F, WARNING);
        NovaRenderer.stroke(graphics, predictedX - 4, predictedY - 4, 8, 8, 4, 0.7F, WARNING);
        NovaRenderer.rect(graphics, craftX - 3, craftY - 3, 6, 6, 1, ACCENT);
        NovaRenderer.text(graphics, "PORT", x + size / 2F - 10, y + size / 2F + 12, 6, SUCCESS);
    }

    static void stars(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width, final int height) {
        NovaRenderer.rect(graphics, x, y, width, height, 7, SURFACE);
        for (int point = 0; point < 22; point++) {
            final float starX = x + 5 + Math.floorMod(point * 73 + 11, width - 10);
            final float starY = y + 5 + Math.floorMod(point * 37 + 19, height - 10);
            NovaRenderer.rect(graphics, starX, starY, 1, 1, 0.5F, MUTED);
        }

        int previous = -1;
        for (int star = 0; star < 6; star++) {
            if ((state.board() & 1 << star) == 0) continue;
            if (previous >= 0)
                line(graphics, x + STAR_X[previous] * width, y + STAR_Y[previous] * height,
                        x + STAR_X[star] * width, y + STAR_Y[star] * height, 1, ACCENT);
            previous = star;
        }
    }

    static void referenceStars(final GuiGraphicsExtractor graphics, final int mask, final int x, final int y, final int width, final int height) {
        NovaRenderer.text(graphics, "REFERENCE FIX", x, y, 8, MUTED);
        int previous = -1;
        for (int star = 0; star < 6; star++) {
            if ((mask & 1 << star) == 0) continue;
            final float pointX = x + STAR_X[star] * width, pointY = y + 16 + STAR_Y[star] * height;
            if (previous >= 0)
                line(graphics, x + STAR_X[previous] * width, y + 16 + STAR_Y[previous] * height, pointX, pointY, 1, WARNING);
            NovaRenderer.rect(graphics, pointX - 2, pointY - 2, 4, 4, 2, WARNING);
            previous = star;
        }
    }

    static void burn(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width, final int height) {
        NovaRenderer.rect(graphics, x, y, width, height, 7, SURFACE);
        final int units = state.board() & 255;
        final float margin = 14, chartWidth = width - margin * 2;
        NovaRenderer.rect(graphics, x + margin + chartWidth * 0.6F, y + 5, chartWidth * 0.2F, height - 15, 5, new Color(80, 161, 117, 65));
        for (int index = 0; index <= 10; index++) {
            final float pointX = x + margin + chartWidth * index / 10F;
            line(graphics, pointX, y + height - 17, pointX, y + height - 12, 0.6F, MUTED);
            if (index % 2 == 0)
                NovaRenderer.text(graphics, Integer.toString(index), pointX - 2, y + height - 11, 6, MUTED);
        }

        for (int point = 0; point < 30; point++) {
            final float first = point / 30F, next = (point + 1) / 30F;
            line(graphics, x + margin + first * chartWidth, y + height - 24 - first * first * (height - 35),
                    x + margin + next * chartWidth, y + height - 24 - next * next * (height - 35), 1.2F, ACCENT);
        }

        final float markerX = x + margin + chartWidth * Math.clamp(units / 10F, 0, 1);
        line(graphics, markerX, y + 7, markerX, y + height - 17, 2, units >= 6 && units <= 8 ? SUCCESS : WARNING);
        NovaRenderer.text(graphics, "RETURN CORRIDOR", x + width * 0.46F, y + 7, 7, SUCCESS);
    }

    static void signal(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width, final int height) {
        NovaRenderer.rect(graphics, x, y, width, height, 7, SURFACE);
        final int frequency = state.board() & 15, gain = state.board() >> 4 & 15;
        final int target = state.stage().equals("RETURN") ? 5 : state.stage().equals("DESCENT") ? 4 : 3;
        final float amplitude = height * 0.21F, noise = (100 - (state.aux() & 255)) * 0.035F;
        line(graphics, x + 7, y + height / 2F, x + width - 7, y + height / 2F, 0.5F, BORDER);
        for (int point = 0; point < 90; point++) {
            final float first = point / 90F, next = (point + 1) / 90F;
            final float expectedFirst = (float) Math.sin(first * Math.PI * 2 * (target + 1)) * amplitude;
            final float expectedNext = (float) Math.sin(next * Math.PI * 2 * (target + 1)) * amplitude;
            line(graphics, x + 7 + first * (width - 14), y + height / 2F + expectedFirst,
                    x + 7 + next * (width - 14), y + height / 2F + expectedNext, 0.6F, MUTED);
            final float actualFirst = Math.clamp((float) Math.sin(first * Math.PI * 2 * (frequency + 1)) * amplitude * gain / 2F + (float) Math.sin(first * 113) * noise, -height * 0.4F, height * 0.4F);
            final float actualNext = Math.clamp((float) Math.sin(next * Math.PI * 2 * (frequency + 1)) * amplitude * gain / 2F + (float) Math.sin(next * 113) * noise, -height * 0.4F, height * 0.4F);
            line(graphics, x + 7 + first * (width - 14), y + height / 2F + actualFirst,
                    x + 7 + next * (width - 14), y + height / 2F + actualNext, 1.2F, (state.aux() & 256) != 0 ? WARNING : ACCENT);
        }
    }

    static void adapter(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width, final int height) {
        final int progress = state.board() & 7;
        NovaRenderer.rect(graphics, x, y, width, height, 7, SURFACE);
        final int[] order = { 2, 0, 3, 1 };
        for (int index = 0; index < 4; index++) {
            final float partX = x + 10 + index * (width - 20) / 4F, partWidth = (width - 30) / 4F;
            if (index < progress) {
                NovaRenderer.rect(graphics, partX + 3, y + 17, partWidth - 3, height - 30, 3, BORDER);
                NovaRenderer.rect(graphics, partX, y + 12, partWidth - 5, height - 30, 3, order[index] == 3 ? SUCCESS : ACCENT);
                if (order[index] == 3) {
                    for (int rib = 1; rib < 5; rib++) {
                        final float ribX = partX + rib * (partWidth - 5) / 5;
                        line(graphics, ribX, y + 17, ribX, y + height - 23, 1, BORDER);
                    }
                }
            } else
                NovaRenderer.stroke(graphics, partX, y + 12, partWidth - 5, height - 30, 3, 0.8F, BORDER);
            NovaRenderer.text(graphics, new String[] { "Seal", "Housing", "Cartridge", "Fan" }[index], partX, y + height - 13, 7, index < progress ? TEXT : MUTED);
        }
        if ((state.board() & 256) != 0 || state.aux() > 0) {
            final float settling = 1F / (state.aux() + 1);
            for (int point = 0; point < 45; point++) {
                final float first = point / 45F, next = (point + 1) / 45F;
                line(graphics, x + 7 + first * (width - 14), y + 6 + (float) Math.sin(first * Math.PI * 12) * 4 * settling,
                        x + 7 + next * (width - 14), y + 6 + (float) Math.sin(next * Math.PI * 12) * 4 * settling,
                        1, state.aux() >= 3 ? SUCCESS : WARNING);
            }
        }
    }

    static void heating(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width, final int height) {
        NovaRenderer.rect(graphics, x + width * 0.35F, y, width * 0.3F, height, 8, BORDER);
        final int mask = state.board();
        final boolean emergency = state.task().equals("ENGINE");
        final String[] names = emergency ? new String[] { "Life support", "Science", "Comfort heat" } : new String[] { "Guidance", "Air loop", "Laboratory" };
        for (int room = 0; room < 3; room++) {
            final float roomX = x + room * width / 3F + 3;
            NovaRenderer.rect(graphics, roomX, y + 3 + room * 3, width / 3F - 6, height - 14, 5,
                    (mask & 1 << room) != 0 ? new Color(145, 89, 45) : SURFACE);
            NovaRenderer.text(graphics, names[room], roomX + 5, y + 11 + room * 3, 8, TEXT);
            NovaRenderer.text(graphics, (mask & 1 << room) != 0 ? emergency ? "POWERED" : "HEATING" : emergency ? "ISOLATED" : "COLD",
                    roomX + 5, y + 25 + room * 3, 7, (mask & 1 << room) != 0 ? WARNING : MUTED);
        }
    }
}
