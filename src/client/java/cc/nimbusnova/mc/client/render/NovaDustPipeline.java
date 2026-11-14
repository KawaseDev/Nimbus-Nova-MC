package cc.nimbusnova.mc.client.render;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class NovaDustPipeline {
    public static final SingleQuadParticle.Layer LAYER;

    static {
        final Identifier fragmentShader = Identifier.parse("nimbus_nova:core/dust");
        final RenderPipeline translucent = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
                .withLocation(Identifier.parse("nimbus_nova:pipeline/dust"))
                .withFragmentShader(fragmentShader)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .build());

        final OitPipelineSet oit = RenderPipelines.register(OitPipelineSet.builder("nimbus_nova_dust",
                        RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET).withFragmentShader(fragmentShader))
                .withDepthBoundsModifier(builder -> builder.withLocation(Identifier.parse("nimbus_nova:pipeline/dust_depth_bounds")))
                .withTransmittanceModifier(builder -> builder.withLocation(Identifier.parse("nimbus_nova:pipeline/dust_transmittance")))
                .withAccumulateModifier(builder -> builder.withLocation(Identifier.parse("nimbus_nova:pipeline/dust_accumulate")))
                .build());

        LAYER = new SingleQuadParticle.Layer(true, SingleQuadParticle.Layer.TRANSLUCENT.textureAtlasLocation(), translucent, oit);
    }

    private NovaDustPipeline() {
        /* w */
    }

    public static void initialize() {
        /* w */
    }
}
