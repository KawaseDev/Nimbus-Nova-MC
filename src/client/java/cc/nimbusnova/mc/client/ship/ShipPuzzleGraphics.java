package cc.nimbusnova.mc.client.ship;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.ship.ShipPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

final class ShipPuzzleGraphics implements ShipPalette {
    static final float[] STAR_X = { 0.10F, 0.29F, 0.48F, 0.61F, 0.81F, 0.88F };
    static final float[] STAR_Y = { 0.23F, 0.70F, 0.13F, 0.49F, 0.82F, 0.19F };
    private static final Color SCOPE = new Color(6, 14, 24);
    private static float craftX = -1, craftY = -1;
    private static long craftFrame;

    private ShipPuzzleGraphics() {
        /* w */
    }

    static void line(final GuiGraphicsExtractor graphics, final float fromX, final float fromY, final float toX, final float toY,
                     final float thickness, final Color color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(fromX, fromY);
        graphics.pose().rotate((float) Math.atan2(toY - fromY, toX - fromX));
        NovaRenderer.rect(graphics, 0, -thickness / 2, (float) Math.hypot(toX - fromX, toY - fromY), Math.max(1, thickness), color);
        graphics.pose().popMatrix();
    }

    private static void scope(final GuiGraphicsExtractor graphics, final float x, final float y, final float width, final float height,
                              final float cell, final Color tint) {
        NovaRenderer.slot(graphics, x, y, width, height, SCOPE, false);
        for (float column = x + cell; column < x + width - 2; column += cell)
            NovaRenderer.rect(graphics, column, y + 2, 1, height - 4, NovaRenderer.alpha(tint, 22));

        for (float row = y + cell; row < y + height - 2; row += cell)
            NovaRenderer.rect(graphics, x + 2, row, width - 4, 1, NovaRenderer.alpha(tint, 22));
    }

    static void docking(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int size) {
        final float scale = (size - 18) / 8F;
        final int horizontal = state.board() & 15, vertical = state.board() >> 4 & 15;
        final int velocityX = (state.board() >> 8 & 7) - 2, velocityY = (state.board() >> 11 & 7) - 2;
        final boolean inside = Math.abs(horizontal - 4) <= 1 && Math.abs(vertical - 4) <= 1, still = velocityX == 0 && velocityY == 0;

        final long now = System.nanoTime();
        final float blend = craftX < 0 || now - craftFrame > 500_000_000L ? 1 : 1 - (float) Math.exp(-(now - craftFrame) / 1_000_000_000.0 * 14);
        craftFrame = now;
        craftX += (horizontal - craftX) * blend;
        craftY += (vertical - craftY) * blend;

        scope(graphics, x, y, size, size, scale, ACCENT);
        final float center = size / 2F;
        NovaRenderer.rect(graphics, x + center, y + 3, 1, size - 6, NovaRenderer.alpha(ACCENT, 46));
        NovaRenderer.rect(graphics, x + 3, y + center, size - 6, 1, NovaRenderer.alpha(ACCENT, 46));

        final float portX = x + 9 + 3 * scale, portY = y + 9 + 3 * scale, portSize = scale * 2;
        final Color port = inside && still ? NovaRenderer.mix(SUCCESS, Color.WHITE, NovaRenderer.pulse(9) * 0.6F) : SUCCESS;
        NovaRenderer.rect(graphics, portX, portY, portSize, portSize, NovaRenderer.alpha(SUCCESS, inside ? 60 : 26));
        NovaRenderer.brackets(graphics, portX - 1, portY - 1, portSize + 2, portSize + 2, 5, port);
        NovaRenderer.stroke(graphics, x + center - 3, y + center - 3, 7, 7, 1, port);

        final float shipX = x + 9 + craftX * scale, shipY = y + 9 + craftY * scale;
        float dotX = horizontal, dotY = vertical;
        for (int step = 1; step <= 3 && !still; step++) {
            dotX = Math.clamp(dotX + velocityX, 0, 8);
            dotY = Math.clamp(dotY + velocityY, 0, 8);
            NovaRenderer.rect(graphics, x + 8 + dotX * scale, y + 8 + dotY * scale, 3, 3, NovaRenderer.alpha(WARNING, 230 - step * 55));
        }

        if (!still)
            line(graphics, shipX, shipY, shipX + velocityX * scale, shipY + velocityY * scale, 1, WARNING);

        NovaRenderer.rect(graphics, shipX - 4, shipY - 4, 9, 9, new Color(5, 7, 13));
        NovaRenderer.rect(graphics, shipX - 3, shipY - 3, 7, 7, ACCENT);
        NovaRenderer.rect(graphics, shipX - 3, shipY - 3, 7, 1, new Color(200, 248, 255));
        NovaRenderer.rect(graphics, shipX - 1, shipY - 1, 3, 3, new Color(10, 40, 60));
        NovaRenderer.text(graphics, inside && still ? "LOCK READY" : "PORT", x + 4, y + size - 12, 8, port);
    }

    static void stars(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width, final int height) {
        NovaRenderer.slot(graphics, x, y, width, height, SCOPE, false);
        NovaRenderer.gradient(graphics, x + 1, y + 1, width - 2, height - 2, new Color(10, 16, 40), new Color(4, 6, 14));
        for (int point = 0; point < 34; point++) {
            final float starX = x + 4 + Math.floorMod(point * 73 + 11, width - 8);
            final float starY = y + 4 + Math.floorMod(point * 37 + 19, height - 8);
            final float twinkle = 0.35F + 0.65F * (float) Math.abs(Math.sin(NovaRenderer.seconds() * (0.6 + point % 5 * 0.25) + point));
            NovaRenderer.rect(graphics, starX, starY, 1, 1, NovaRenderer.alpha(TEXT, Math.round(170 * twinkle)));
        }

        int previous = -1;
        for (int star = 0; star < 6; star++) {
            if ((state.board() & 1 << star) == 0) continue;
            if (previous >= 0) {
                line(graphics, x + STAR_X[previous] * width, y + STAR_Y[previous] * height,
                        x + STAR_X[star] * width, y + STAR_Y[star] * height, 3, NovaRenderer.alpha(ACCENT, 50));
                line(graphics, x + STAR_X[previous] * width, y + STAR_Y[previous] * height,
                        x + STAR_X[star] * width, y + STAR_Y[star] * height, 1, ACCENT);
            }
            previous = star;
        }
    }

    static void referenceStars(final GuiGraphicsExtractor graphics, final int mask, final int x, final int y, final int width, final int height) {
        NovaRenderer.slot(graphics, x, y + 11, width, height + 8, SCOPE, false);
        NovaRenderer.text(graphics, "STAR MAP", x, y, 8, WARNING);
        int previous = -1;
        for (int star = 0; star < 6; star++) {
            if ((mask & 1 << star) == 0) continue;
            final float pointX = x + 4 + STAR_X[star] * (width - 8), pointY = y + 15 + STAR_Y[star] * height;
            if (previous >= 0)
                line(graphics, x + 4 + STAR_X[previous] * (width - 8), y + 15 + STAR_Y[previous] * height, pointX, pointY, 1, WARNING);
            NovaRenderer.rect(graphics, pointX - 2, pointY - 2, 4, 4, WARNING);
            previous = star;
        }
    }

    static void burn(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width, final int height) {
        scope(graphics, x, y, width, height, 12, WARNING);
        final int units = state.board() & 255;
        final boolean running = (state.board() & 256) != 0, inCorridor = units >= 6 && units <= 8;
        final float margin = 14, chartWidth = width - margin * 2;
        final Color corridor = running && inCorridor ? NovaRenderer.mix(SUCCESS, Color.WHITE, NovaRenderer.pulse(12) * 0.5F) : SUCCESS;
        NovaRenderer.rect(graphics, x + margin + chartWidth * 0.6F, y + 3, chartWidth * 0.2F, height - 16, NovaRenderer.alpha(corridor, running && inCorridor ? 110 : 60));
        NovaRenderer.rect(graphics, x + margin + chartWidth * 0.6F, y + 3, 1, height - 16, corridor);
        NovaRenderer.rect(graphics, x + margin + chartWidth * 0.8F, y + 3, 1, height - 16, corridor);
        for (int index = 0; index <= 10; index++) {
            final float pointX = x + margin + chartWidth * index / 10F;
            NovaRenderer.rect(graphics, pointX, y + height - 16, 1, index % 2 == 0 ? 4 : 2, MUTED);
            if (index % 2 == 0)
                NovaRenderer.text(graphics, Integer.toString(index), pointX - (index == 10 ? 5 : 2), y + height - 11, 8, MUTED);
        }

        for (int point = 0; point < 30; point++) {
            final float first = point / 30F, next = (point + 1) / 30F;
            line(graphics, x + margin + first * chartWidth, y + height - 20 - first * first * (height - 34),
                    x + margin + next * chartWidth, y + height - 20 - next * next * (height - 34), 1.2F,
                    first * 10 <= units ? WARNING : NovaRenderer.alpha(MUTED, 150));
        }

        final float amount = Math.clamp(units / 10F, 0, 1), markerX = x + margin + chartWidth * amount;
        final float markerY = y + height - 20 - amount * amount * (height - 34);
        NovaRenderer.rect(graphics, markerX, y + 3, 1, height - 17, NovaRenderer.alpha(inCorridor ? SUCCESS : WARNING, 150));
        if (running) {
            final float flame = 4 + 4 * NovaRenderer.pulse(30);
            NovaRenderer.rect(graphics, markerX - flame - 3, markerY - 1, flame, 3, new Color(255, 150, 60));
            NovaRenderer.rect(graphics, markerX - flame / 2 - 3, markerY, flame / 2, 1, new Color(255, 240, 190));
        }

        NovaRenderer.rect(graphics, markerX - 3, markerY - 3, 7, 7, new Color(5, 7, 13));
        NovaRenderer.rect(graphics, markerX - 2, markerY - 2, 5, 5, inCorridor ? SUCCESS : WARNING);
        final int entry = state.board() >> 9 & 3;
        NovaRenderer.text(graphics, "ENTRY: " + new String[] { "CHOOSE ONE", "SHALLOW", "PLANNED", "STEEP" }[entry], x + 6, y + 5, 8,
                entry == 2 ? SUCCESS : MUTED);
        NovaRenderer.text(graphics, running && inCorridor ? "CUT OFF NOW" : "SAFE ZONE", x + margin + chartWidth * 0.6F + 3, y + 5, 8, corridor);
    }

    static void orbit(final GuiGraphicsExtractor graphics, final int profile, final int x, final int y, final int width, final int height) {
        scope(graphics, x, y, width, height, 12, ACCENT);
        final float moonX = x + width * 0.64F, moonY = y + height / 2F + 2;
        final Color path = profile == 1 ? SUCCESS : profile == 0 ? WARNING : new Color(255, 107, 107);
        NovaRenderer.chamfer(graphics, moonX - 9, moonY - 9, 19, 19, 6, new Color(150, 156, 170));
        NovaRenderer.rect(graphics, moonX - 5, moonY - 4, 4, 3, new Color(112, 118, 134));
        NovaRenderer.rect(graphics, moonX + 2, moonY + 1, 3, 3, new Color(112, 118, 134));
        NovaRenderer.rect(graphics, moonX - 2, moonY + 4, 2, 2, new Color(112, 118, 134));

        float previousX = x + 6, previousY = moonY - 18;
        final float ship = (float) (NovaRenderer.seconds() * 0.3 % 1);
        float shipX = previousX, shipY = previousY;
        for (int point = 1; point <= 48; point++) {
            final float amount = point / 48F, pointX, pointY;
            if (profile == 0) {
                pointX = x + 6 + amount * (width - 12);
                pointY = moonY - 18 + (float) Math.sin(amount * Math.PI) * 5;
            } else if (profile == 1 && amount > 0.3F) {
                final double angle = Math.PI * 1.25 + (amount - 0.3) / 0.7 * Math.PI * 2;
                pointX = moonX + (float) Math.cos(angle) * 20;
                pointY = moonY + (float) Math.sin(angle) * 15;
            } else if (profile == 1) {
                pointX = x + 6 + amount / 0.3F * (moonX - 14 - x - 6);
                pointY = moonY - 18 + amount / 0.3F * 7;
            } else {
                pointX = x + 6 + Math.min(1, amount / 0.8F) * (moonX - 6 - x - 6);
                pointY = moonY - 18 + Math.min(1, amount / 0.8F) * Math.min(1, amount / 0.8F) * 13;
            }

            if (point % 2 == 0 || profile == 1)
                line(graphics, previousX, previousY, pointX, pointY, 1, path);
            if (amount <= ship) {
                shipX = pointX;
                shipY = pointY;
            }

            previousX = pointX;
            previousY = pointY;
        }

        NovaRenderer.rect(graphics, shipX - 2, shipY - 2, 5, 5, new Color(5, 7, 13));
        NovaRenderer.rect(graphics, shipX - 1, shipY - 1, 3, 3, Color.WHITE);
        NovaRenderer.text(graphics, new String[] { "FLIES PAST", "STABLE ORBIT", "TOO LOW" }[profile], x + 5, y + height - 12, 8, path);
    }

    static void descent(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width) {
        final int vertical = state.board() & 15, lateral = state.board() >> 4 & 15, gaugeWidth = (width - 108) / 2;
        final boolean guided = (state.board() & 256) != 0, site = (state.board() & 512) != 0;
        final String[] names = { "DOWN SPEED", "SIDE SPEED" };
        for (int gauge = 0; gauge < 2; gauge++) {
            final int speed = gauge == 0 ? vertical : lateral;
            final float gaugeX = x + gauge * (gaugeWidth + 6);
            final Color color = speed <= 1 ? SUCCESS : WARNING;
            NovaRenderer.text(graphics, names[gauge], gaugeX, y, 8, MUTED);
            NovaRenderer.text(graphics, speed + (speed <= 1 ? " SAFE" : " FAST"), gaugeX + gaugeWidth - NovaRenderer.width(speed + (speed <= 1 ? " SAFE" : " FAST"), 8), y, 8, color);
            NovaRenderer.slot(graphics, gaugeX, y + 11, gaugeWidth, 12, SURFACE, false);
            NovaRenderer.rect(graphics, gaugeX + 2, y + 13, (gaugeWidth - 4) / 6F, 8, NovaRenderer.alpha(SUCCESS, 70));
            NovaRenderer.rect(graphics, gaugeX + 2, y + 14, (gaugeWidth - 4) * Math.min(6, speed) / 6F, 6, color);
        }

        NovaRenderer.text(graphics, (guided ? "Guidance OK" : "Check guidance") + " / " + (site ? "site chosen" : "pick a site") + " / fuel " + state.aux(),
                x, y + 28, 8, guided && site ? SUCCESS : MUTED);

        final float sceneX = x + width - 96;
        scope(graphics, sceneX, y, 96, 37, 12, ACCENT);
        NovaRenderer.rect(graphics, sceneX + 2, y + 29, 92, 6, new Color(120, 126, 140));
        for (int rock = 0; rock < 3; rock++)
            NovaRenderer.rect(graphics, sceneX + 10 + rock * 9, y + 26 - rock % 2, 5, 3 + rock % 2, new Color(84, 90, 104));

        NovaRenderer.text(graphics, "A", sceneX + 18, y + 3, 8, new Color(255, 107, 107));
        NovaRenderer.text(graphics, "B", sceneX + 68, y + 3, 8, site ? SUCCESS : MUTED);
        final float landerX = sceneX + (site ? 66 : 42) + lateral * 3, landerY = y + 19 - Math.min(9, vertical * 3);
        NovaRenderer.rect(graphics, landerX, landerY, 9, 6, TEXT);
        NovaRenderer.rect(graphics, landerX - 1, landerY + 6, 2, 3, MUTED);
        NovaRenderer.rect(graphics, landerX + 8, landerY + 6, 2, 3, MUTED);
        if (vertical > 1)
            NovaRenderer.rect(graphics, landerX + 3, landerY + 6, 3, 2 + Math.round(NovaRenderer.pulse(24) * 3), new Color(255, 150, 60));
    }

    static void signal(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width, final int height) {
        scope(graphics, x, y, width, height, 12, SUCCESS);
        final int frequency = state.board() & 15, gain = state.board() >> 4 & 15;
        final int target = state.stage().equals("RETURN") ? 5 : state.stage().equals("DESCENT") ? 4 : 3;
        final boolean clipped = (state.aux() & 256) != 0, locked = (state.aux() & 255) >= 90 && !clipped;
        final float amplitude = height * 0.21F, noise = (100 - (state.aux() & 255)) * 0.035F;
        final float phase = (float) (NovaRenderer.seconds() * 1.6 % (Math.PI * 2));
        final Color live = clipped ? WARNING : locked ? SUCCESS : ACCENT;
        final float middle = y + height / 2F, limit = height * 0.4F;

        NovaRenderer.rect(graphics, x + 3, middle, width - 6, 1, NovaRenderer.alpha(SUCCESS, 70));
        if (clipped) {
            NovaRenderer.rect(graphics, x + 3, middle - limit - 1, width - 6, 1, NovaRenderer.alpha(WARNING, 160));
            NovaRenderer.rect(graphics, x + 3, middle + limit + 1, width - 6, 1, NovaRenderer.alpha(WARNING, 160));
        }

        for (int point = 0; point < 96; point++) {
            final float first = point / 96F, next = (point + 1) / 96F;
            if (point % 2 == 0)
                line(graphics, x + 7 + first * (width - 14), middle + (float) Math.sin(first * Math.PI * 2 * (target + 1) + phase) * amplitude,
                        x + 7 + next * (width - 14), middle + (float) Math.sin(next * Math.PI * 2 * (target + 1) + phase) * amplitude, 1, MUTED);

            final float actualFirst = Math.clamp((float) Math.sin(first * Math.PI * 2 * (frequency + 1) + phase) * amplitude * gain / 2F
                    + (float) Math.sin(first * 113 + phase * 7) * noise, -limit, limit);
            final float actualNext = Math.clamp((float) Math.sin(next * Math.PI * 2 * (frequency + 1) + phase) * amplitude * gain / 2F
                    + (float) Math.sin(next * 113 + phase * 7) * noise, -limit, limit);
            line(graphics, x + 7 + first * (width - 14), middle + actualFirst, x + 7 + next * (width - 14), middle + actualNext, 3, NovaRenderer.alpha(live, 46));
            line(graphics, x + 7 + first * (width - 14), middle + actualFirst, x + 7 + next * (width - 14), middle + actualNext, 1, live);
        }

        final String status = clipped ? "CLIPPED" : locked ? "LOCKED" : "SEARCHING";
        NovaRenderer.chip(graphics, status, x + width - 5 - NovaRenderer.chipWidth(status), y + 4, live);
    }

    static void adapter(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width, final int height) {
        final int progress = state.board() & 7;
        NovaRenderer.slot(graphics, x, y, width, height, SCOPE, false);
        final int[] order = { 2, 0, 3, 1 };
        final String[] names = { "Seal", "Housing", "Cartridge", "Fan" };
        final float pipeY = y + height / 2F - 6;
        NovaRenderer.rect(graphics, x + 4, pipeY + 4, width - 8, 5, new Color(34, 44, 66));
        for (int index = 0; index < 4; index++) {
            final float partX = x + 10 + index * (width - 20) / 4F, partWidth = (width - 30) / 4F - 5, partHeight = height - 26;
            final boolean fitted = index < progress, next = index == progress;
            if (fitted) {
                final Color body = order[index] == 3 ? SUCCESS : ACCENT;
                NovaRenderer.rect(graphics, partX - 1, y + 7, partWidth + 2, partHeight + 2, new Color(5, 7, 13));
                NovaRenderer.gradient(graphics, partX, y + 8, partWidth, partHeight, NovaRenderer.mix(body, Color.WHITE, 0.2F), NovaRenderer.mix(body, Color.BLACK, 0.45F));
                for (int rib = 1; rib < 5 && order[index] == 3; rib++)
                    NovaRenderer.rect(graphics, partX + rib * partWidth / 5, y + 10, 1, partHeight - 4, new Color(5, 7, 13, 150));
            } else
                NovaRenderer.stroke(graphics, partX, y + 8, partWidth, partHeight, 1,
                        next ? NovaRenderer.mix(BORDER, WARNING, NovaRenderer.pulse(6)) : BORDER);

            NovaRenderer.text(graphics, index + 1 + " " + names[index], partX, y + height - 13, 8, fitted ? TEXT : next ? WARNING : MUTED);
        }

        if ((state.board() & 256) == 0 && state.aux() <= 0) return;
        final float settling = 1F / (state.aux() + 1);
        final Color wave = state.aux() >= 3 ? SUCCESS : WARNING;
        for (int point = 0; point < 45; point++) {
            final float first = point / 45F, next = (point + 1) / 45F;
            line(graphics, x + 7 + first * (width - 14), y + 5 + (float) Math.sin(first * Math.PI * 12 + NovaRenderer.seconds() * 6) * 3 * settling,
                    x + 7 + next * (width - 14), y + 5 + (float) Math.sin(next * Math.PI * 12 + NovaRenderer.seconds() * 6) * 3 * settling, 1, wave);
        }
    }

    static void heating(final GuiGraphicsExtractor graphics, final ShipPayload state, final int x, final int y, final int width, final int height) {
        final int mask = state.board();
        final boolean emergency = state.task().equals("ENGINE");
        final String[] names = emergency ? new String[] { "Life support", "Science", "Comfort heat" } : new String[] { "Guidance", "Air loop", "Laboratory" };
        final boolean[] essential = emergency ? new boolean[] { true, false, false } : new boolean[] { true, true, false };
        final float cardWidth = (width - 10) / 3F;
        for (int room = 0; room < 3; room++) {
            final float roomX = x + room * (cardWidth + 5);
            final boolean on = (mask & 1 << room) != 0;
            final Color lamp = on ? essential[room] ? SUCCESS : WARNING : new Color(46, 56, 80);
            NovaRenderer.slot(graphics, roomX, y, cardWidth, height - 10, on ? new Color(60, 44, 20) : SURFACE, false);
            NovaRenderer.rect(graphics, roomX + 5, y + 6, 6, 6, new Color(5, 7, 13));
            NovaRenderer.rect(graphics, roomX + 6, y + 7, 4, 4, on ? NovaRenderer.mix(lamp, Color.WHITE, NovaRenderer.pulse(5) * 0.4F) : lamp);
            NovaRenderer.text(graphics, names[room], roomX + 15, y + 5, 8, TEXT);
            NovaRenderer.text(graphics, on ? emergency ? "POWERED" : "HEATING" : emergency ? "ISOLATED" : "COLD", roomX + 5, y + 17, 8, on ? lamp : MUTED);
            NovaRenderer.text(graphics, essential[room] ? "must stay on" : "can wait", roomX + 5, y + 28, 8,
                    essential[room] ? NovaRenderer.alpha(SUCCESS, 200) : MUTED);
        }
    }
}
