package cc.nimbusnova.mc.client.npc;

import cc.nimbusnova.mc.npc.MissionAstronaut;
import cc.nimbusnova.mc.npc.MissionNpcs;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MissionNpcRenderer extends HumanoidMobRenderer<MissionAstronaut, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private final Identifier skin;

    private MissionNpcRenderer(final EntityRendererProvider.Context context, final String skin) {
        super(context, new AstronautModel(LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0), 64, 64).bakeRoot()), 0.35F);
        this.skin = Identifier.parse("nimbus_nova:textures/entity/npc/" + skin + ".png");
    }

    public static void register() {
        EntityRenderers.register(MissionNpcs.GUIDE, context -> new MissionNpcRenderer(context, "commander_vega"));
        EntityRenderers.register(MissionNpcs.ENGINEER, context -> new MissionNpcRenderer(context, "engineer_sol"));
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(final HumanoidRenderState state) {
        return skin;
    }

    @Override
    protected boolean shouldShowName(final MissionAstronaut npc, final double distance) {
        return false;
    }

    private static final class AstronautModel extends HumanoidModel<HumanoidRenderState> {
        private AstronautModel(final ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(final HumanoidRenderState state) {
            super.setupAnim(state);
            rightArm.zRot += (float) Math.sin(state.ageInTicks * 0.05) * 0.035F;
            leftArm.zRot -= (float) Math.sin(state.ageInTicks * 0.05) * 0.035F;
            body.xRot += (float) Math.sin(state.ageInTicks * 0.035) * 0.008F;
        }
    }
}
