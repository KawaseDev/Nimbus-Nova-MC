package cc.nimbusnova.mc.client.render;

import cc.nimbusnova.mc.client.render.fonts.MsdfFont;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.awt.Color;

public final class NovaRenderer {
    private static MsdfFont font;

    private NovaRenderer() {
        /* w */
    }

    public static void initialize() {
        NovaPrimitives.initialize();
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Identifier.parse("nimbus_nova:msdf_font"),
                (ResourceManagerReloadListener) resources -> font = new MsdfFont(resources, "geist"));
    }

    public static void rect(final GuiGraphicsExtractor graphics, final float x, final float y,
                            final float width, final float height, final float radius, final Color color) {
        NovaPrimitives.rounded(graphics, x, y, width, height, radius, color.getRGB());
    }

    public static void stroke(final GuiGraphicsExtractor graphics, final float x, final float y,
                              final float width, final float height, final float radius, final float thickness, final Color color) {
        NovaPrimitives.stroke(graphics, x, y, width, height, radius, thickness, color.getRGB());
    }

    public static void glow(final GuiGraphicsExtractor graphics, final float x, final float y,
                            final float width, final float height, final float radius, final float spread, final Color color) {
        NovaPrimitives.glow(graphics, x, y, width, height, radius, spread, color.getRGB());
    }

    public static void blur(final GuiGraphicsExtractor graphics) {
        graphics.blurBeforeThisStratum();
    }

    public static void text(final GuiGraphicsExtractor graphics, final String text, final float x,
                            final float y, final float size, final Color color) {
        if (font == null) return;
        font.draw(graphics, text, x, y, size, color.getRGB());
    }

    public static float width(final String text, final float size) {
        return font == null ? 0 : font.width(text, size);
    }

    public static void wrap(final GuiGraphicsExtractor graphics, final String text, final float x, final float y,
                            final float width, final float size, final int maxLines, final Color color) {
        if (font == null || maxLines <= 0) return;

        String line = "";
        int lineNumber = 0;
        for (final String word : text.split("\\s+")) {
            final String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.width(candidate, size) > width) {
                font.draw(graphics, line, x, y + lineNumber++ * (size + 3), size, color.getRGB());
                if (lineNumber >= maxLines) return;
                line = word;
            } else
                line = candidate;
        }

        font.draw(graphics, line, x, y + lineNumber * (size + 3), size, color.getRGB());
    }
}
