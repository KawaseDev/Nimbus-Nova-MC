package cc.nimbusnova.mc.client.render.fonts;

import cc.nimbusnova.mc.client.render.NovaPrimitives;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class MsdfFont {
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final Map<Long, Float> kerning = new HashMap<>();
    private final Identifier texture;
    private final int atlasWidth, atlasHeight;
    private final float range, ascender;

    public MsdfFont(final ResourceManager resources, final String name) {
        texture = Identifier.parse("nimbus_nova:fonts/" + name + ".png");
        try (final var reader = new InputStreamReader(resources.open(Identifier.parse("nimbus_nova:fonts/" + name + ".json")), StandardCharsets.UTF_8)) {
            final JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            final JsonObject atlas = root.getAsJsonObject("atlas");
            atlasWidth = atlas.get("width").getAsInt();
            atlasHeight = atlas.get("height").getAsInt();
            range = atlas.get("distanceRange").getAsFloat();
            ascender = root.getAsJsonObject("metrics").get("ascender").getAsFloat();
            final boolean bottomOrigin = atlas.get("yOrigin").getAsString().equals("bottom");
            if (atlasWidth <= 0 || atlasHeight <= 0 || !Float.isFinite(range) || range <= 0)
                throw new IllegalArgumentException("Invalid MSDF atlas dimensions or distance range");

            for (final JsonElement entry : root.getAsJsonArray("glyphs")) {
                final JsonObject glyph = entry.getAsJsonObject();
                final JsonObject plane = glyph.getAsJsonObject("planeBounds"), bounds = glyph.getAsJsonObject("atlasBounds");
                glyphs.put(glyph.get("unicode").getAsInt(), plane == null
                        ? new Glyph(glyph.get("advance").getAsFloat(), 0, 0, 0, 0, 0, 0, 0, 0)
                        : new Glyph(glyph.get("advance").getAsFloat(), plane.get("left").getAsFloat(), plane.get("bottom").getAsFloat(),
                        plane.get("right").getAsFloat(), plane.get("top").getAsFloat(),
                        bounds.get("left").getAsFloat() / atlasWidth,
                        bottomOrigin ? 1 - bounds.get("top").getAsFloat() / atlasHeight : bounds.get("bottom").getAsFloat() / atlasHeight,
                        bounds.get("right").getAsFloat() / atlasWidth,
                        bottomOrigin ? 1 - bounds.get("bottom").getAsFloat() / atlasHeight : bounds.get("top").getAsFloat() / atlasHeight));
            }

            for (final JsonElement entry : root.getAsJsonArray("kerning")) {
                final JsonObject pair = entry.getAsJsonObject();
                kerning.put(((long) pair.get("unicode1").getAsInt() << 32) | pair.get("unicode2").getAsInt(), pair.get("advance").getAsFloat());
            }

            if (!glyphs.containsKey((int) '?'))
                throw new IllegalArgumentException("MSDF font requires a fallback '?' glyph");
        } catch (final IOException exception) {
            throw new UncheckedIOException("Unable to load required MSDF font: " + name, exception);
        }
    }

    public float width(final String text, final float size) {
        float advance = 0;
        int previous = -1;
        for (final int codepoint : text.codePoints().toArray()) {
            final int resolved = glyphs.containsKey(codepoint) ? codepoint : '?';
            advance += kerning.getOrDefault(((long) previous << 32) | resolved, 0F) + glyphs.get(resolved).advance;
            previous = resolved;
        }

        return advance * size;
    }

    public void draw(final GuiGraphicsExtractor graphics, final String text, final float x, final float y, final float size, final int color) {
        float penX = x;
        int previous = -1;
        for (final int codepoint : text.codePoints().toArray()) {
            final int resolved = glyphs.containsKey(codepoint) ? codepoint : '?';
            final Glyph glyph = glyphs.get(resolved);
            penX += kerning.getOrDefault(((long) previous << 32) | resolved, 0F) * size;
            if (glyph.right > glyph.left && glyph.top > glyph.bottom)
                NovaPrimitives.msdf(graphics, texture, penX + glyph.left * size, y + (ascender - glyph.top) * size,
                        (glyph.right - glyph.left) * size, (glyph.top - glyph.bottom) * size,
                        glyph.u0, glyph.v0, glyph.u1, glyph.v1, range, atlasWidth, atlasHeight, color);

            penX += glyph.advance * size;
            previous = resolved;
        }
    }

    private record Glyph(float advance, float left, float bottom, float right, float top,
                         float u0, float v0, float u1, float v1) {
        /* w */
    }
}
