package cc.nimbusnova.mc.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class NovaPrimitives {
    private static final VertexFormat SHAPE_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("UV1", GpuFormat.RG16_SINT)
            .addAttribute("UV2", GpuFormat.RG16_SINT)
            .addAttribute("UV3", GpuFormat.RG32_FLOAT)
            .addAttribute("LineWidth", GpuFormat.R32_FLOAT)
            .build();
    private static final VertexFormat FONT_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("UV3", GpuFormat.RG32_FLOAT)
            .build();
    private static final RenderPipeline SHAPE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.parse("nimbus_nova:pipeline/rounded"))
            .withVertexShader(Identifier.parse("nimbus_nova:core/rounded"))
            .withFragmentShader(Identifier.parse("nimbus_nova:core/rounded"))
            .withVertexBinding(0, SHAPE_FORMAT)
            .build());
    private static final RenderPipeline MSDF = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.parse("nimbus_nova:pipeline/msdf"))
            .withVertexShader(Identifier.parse("nimbus_nova:core/msdf"))
            .withFragmentShader(Identifier.parse("nimbus_nova:core/msdf"))
            .withVertexBinding(0, FONT_FORMAT)
            .build());

    private NovaPrimitives() {
        /* w */
    }

    public static void initialize() {
        /* w */
    }

    public static void rounded(final GuiGraphicsExtractor graphics, final float x, final float y,
                               final float width, final float height, final float radius, final int color) {
        shape(graphics, x, y, width, height, radius, 0, 0, 0, color);
    }

    public static void stroke(final GuiGraphicsExtractor graphics, final float x, final float y,
                              final float width, final float height, final float radius, final float thickness, final int color) {
        if (!Float.isFinite(thickness) || thickness <= 0 || thickness > 2047)
            throw new IllegalArgumentException("Stroke thickness must be finite and between 0 and 2047 GUI pixels.");

        shape(graphics, x, y, width, height, radius, thickness, 0, 1, color);
    }

    public static void glow(final GuiGraphicsExtractor graphics, final float x, final float y,
                            final float width, final float height, final float radius, final float spread, final int color) {
        if (!Float.isFinite(spread) || spread <= 0 || spread > 2047)
            throw new IllegalArgumentException("Glow spread must be finite and between 0 and 2047 GUI pixels.");

        shape(graphics, x, y, width, height, radius, 0, spread, 2, color);
    }

    private static void shape(final GuiGraphicsExtractor graphics, final float x, final float y,
                              final float width, final float height, final float radius,
                              final float thickness, final float spread, final int mode, final int color) {
        validate(x, y, width, height);
        if (!Float.isFinite(radius) || radius < 0)
            throw new IllegalArgumentException("Corner radius must be finite and non-negative.");

        if (width <= 0 || height <= 0 || color >>> 24 == 0) return;

        final float padding = mode == 2 ? spread * 3.5F + 1 : 1;
        final Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        final ScreenRectangle scissor = graphics.scissorStack.peek();
        final ScreenRectangle bounds = bounds(pose, scissor, x - padding, y - padding,
                width + padding * 2, height + padding * 2);
        if (bounds == null) return;

        graphics.guiRenderState.addGuiElement(new Shape(pose, scissor, bounds, x, y, width, height,
                Math.min(radius, Math.min(width, height) / 2), thickness, spread, padding, mode, color));
    }

    public static void msdf(final GuiGraphicsExtractor graphics, final Identifier atlas,
                            final float x, final float y, final float width, final float height,
                            final float u0, final float v0, final float u1, final float v1,
                            final float distanceRange, final int atlasWidth, final int atlasHeight, final int color) {
        validate(x, y, width, height);
        if (!Float.isFinite(distanceRange) || distanceRange <= 0 || atlasWidth <= 0 || atlasHeight <= 0
                || !Float.isFinite(u0) || !Float.isFinite(v0) || !Float.isFinite(u1) || !Float.isFinite(v1))
            throw new IllegalArgumentException("MSDF atlas dimensions, range and UVs must be valid.");

        if (width <= 0 || height <= 0 || color >>> 24 == 0) return;

        final Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        final ScreenRectangle scissor = graphics.scissorStack.peek();
        final ScreenRectangle bounds = bounds(pose, scissor, x, y, width, height);
        if (bounds == null) return;

        final AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(atlas);
        graphics.guiRenderState.addGuiElement(new Glyph(pose, scissor, bounds,
                TextureSetup.singleTexture(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                x, y, width, height, u0, v0, u1, v1,
                distanceRange / atlasWidth, distanceRange / atlasHeight, color));
    }

    private static void validate(final float x, final float y, final float width, final float height) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height))
            throw new IllegalArgumentException("GUI coordinates must be finite.");
    }

    private static @Nullable ScreenRectangle bounds(final Matrix3x2fc pose, final @Nullable ScreenRectangle scissor,
                                                    final float x, final float y, final float width, final float height) {
        final ScreenRectangle transformed = new ScreenRectangle((int) Math.floor(x), (int) Math.floor(y),
                (int) Math.ceil(x + width) - (int) Math.floor(x),
                (int) Math.ceil(y + height) - (int) Math.floor(y)).transformMaxBounds(pose);
        return scissor == null ? transformed : transformed.intersection(scissor);
    }

    private record Shape(Matrix3x2fc pose, @Nullable ScreenRectangle scissorArea, ScreenRectangle bounds,
                         float x, float y, float width, float height, float radius, float thickness,
                         float spread, float padding, int mode, int color) implements GuiElementRenderState {
        @Override
        public void buildVertices(final VertexConsumer vertices) {
            vertex(vertices, x - padding, y - padding, -width / 2 - padding, -height / 2 - padding);
            vertex(vertices, x - padding, y + height + padding, -width / 2 - padding, height / 2 + padding);
            vertex(vertices, x + width + padding, y + height + padding, width / 2 + padding, height / 2 + padding);
            vertex(vertices, x + width + padding, y - padding, width / 2 + padding, -height / 2 - padding);
        }

        private void vertex(final VertexConsumer vertices, final float vertexX, final float vertexY, final float localX, final float localY) {
            vertices.addVertexWith2DPose(pose, vertexX, vertexY).setColor(color).setUv(localX, localY)
                    .setUv1(Math.round(thickness * 16), Math.round(spread * 16)).setUv2(mode, 0)
                    .setUv3(width / 2, height / 2).setLineWidth(radius);
        }

        @Override
        public RenderPipeline pipeline() {
            return SHAPE;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }
    }

    private record Glyph(Matrix3x2fc pose, @Nullable ScreenRectangle scissorArea, ScreenRectangle bounds, TextureSetup textureSetup,
                         float x, float y, float width, float height, float u0, float v0, float u1, float v1,
                         float rangeU, float rangeV, int color) implements GuiElementRenderState {
        @Override
        public void buildVertices(final VertexConsumer vertices) {
            vertex(vertices, x, y, u0, v0);
            vertex(vertices, x, y + height, u0, v1);
            vertex(vertices, x + width, y + height, u1, v1);
            vertex(vertices, x + width, y, u1, v0);
        }

        private void vertex(final VertexConsumer vertices, final float vertexX, final float vertexY, final float u, final float v) {
            vertices.addVertexWith2DPose(pose, vertexX, vertexY).setColor(color).setUv(u, v).setUv3(rangeU, rangeV);
        }

        @Override
        public RenderPipeline pipeline() {
            return MSDF;
        }
    }
}
