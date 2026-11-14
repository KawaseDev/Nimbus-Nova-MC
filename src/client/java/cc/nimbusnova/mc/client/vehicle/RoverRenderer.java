package cc.nimbusnova.mc.client.vehicle;

import cc.nimbusnova.mc.vehicle.MarsRover;
import cc.nimbusnova.mc.vehicle.RoverContent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class RoverRenderer extends EntityRenderer<MarsRover, RoverRenderer.RoverState> {
    private static final Identifier TEXTURE = Identifier.parse("nimbus_nova:textures/entity/rover.png");
    private final RoverModel model;

    public RoverRenderer(final EntityRendererProvider.Context context) {
        super(context);
        model = new RoverModel(createModel());
        shadowRadius = 1.25F;
    }

    public static void register() {
        EntityRenderers.register(RoverContent.ROVER, RoverRenderer::new);
    }

    @Override
    public RoverState createRenderState() {
        return new RoverState();
    }

    @Override
    public void extractRenderState(final MarsRover rover, final RoverState state, final float partialTick) {
        super.extractRenderState(rover, state, partialTick);
        state.yaw = rover.getYRot(partialTick);
        state.wheelRotation = rover.wheelRotation;
        state.steering = rover.steeringAngle();
    }

    @Override
    public void submit(final RoverState state, final PoseStack poses, final SubmitNodeCollector collector,
                       final CameraRenderState camera) {
        poses.pushPose();
        poses.rotateDegrees(Axis.YP, -state.yaw);
        poses.scale(1, -1, -1);
        collector.submitModel(model, state, poses, model.renderType(TEXTURE), state.lightCoords,
                OverlayTexture.NO_OVERLAY, state.outlineColor);
        poses.popPose();
        super.submit(state, poses, collector, camera);
    }

    private static ModelPart createModel() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("chassis", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-12, -12, -18, 24, 5, 36), PartPose.ZERO);

        root.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 42)
                        .addBox(-10, -17, -17, 20, 5, 10), PartPose.ZERO);

        root.addOrReplaceChild("roof", CubeListBuilder.create().texOffs(0, 42)
                        .addBox(-11, -36, -5, 22, 3, 22), PartPose.ZERO);

        root.addOrReplaceChild("roll_cage", CubeListBuilder.create().texOffs(48, 64)
                        .addBox(-11, -33, -5, 2, 21, 2)
                        .addBox(9, -33, -5, 2, 21, 2)
                        .addBox(-11, -33, 15, 2, 21, 2)
                        .addBox(9, -33, 15, 2, 21, 2), PartPose.ZERO);

        root.addOrReplaceChild("windscreen", CubeListBuilder.create().texOffs(0, 70)
                        .addBox(-9, -30, -5, 18, 13, 1), PartPose.ZERO);

        root.addOrReplaceChild("seat", CubeListBuilder.create().texOffs(48, 66)
                        .addBox(-6, -14, 0, 12, 2, 10)
                        .addBox(-6, -23, 8, 12, 9, 2), PartPose.ZERO);

        root.addOrReplaceChild("cargo", CubeListBuilder.create().texOffs(76, 64)
                        .addBox(-8, -18, 12, 16, 6, 5), PartPose.ZERO);

        root.addOrReplaceChild("antenna", CubeListBuilder.create().texOffs(110, 64)
                        .addBox(9, -44, 13, 1, 11, 1)
                        .addBox(7, -45, 11, 5, 1, 5), PartPose.ZERO);

        for (int side = 0; side < 2; side++) {
            for (int axle = 0; axle < 3; axle++) {
                root.addOrReplaceChild("wheel_" + side + "_" + axle,
                        CubeListBuilder.create().texOffs(0, 96).addBox(-3, -4, -5, 6, 8, 10)
                                .addBox(-3, -5, -4, 6, 10, 8),
                        PartPose.offset(side == 0 ? -15 : 15, -5, -13 + axle * 13));
            }
        }

        return LayerDefinition.create(mesh, 128, 128).bakeRoot();
    }

    public static final class RoverState extends EntityRenderState {
        float yaw, wheelRotation, steering;
    }

    private static final class RoverModel extends EntityModel<RoverState> {
        private final ModelPart[][] wheels = new ModelPart[2][3];

        private RoverModel(final ModelPart root) {
            super(root);
            for (int side = 0; side < 2; side++) {
                for (int axle = 0; axle < 3; axle++)
                    wheels[side][axle] = root.getChild("wheel_" + side + "_" + axle);
            }
        }

        @Override
        public void setupAnim(final RoverState state) {
            for (int side = 0; side < 2; side++) {
                for (int axle = 0; axle < 3; axle++) {
                    wheels[side][axle].xRot = state.wheelRotation;
                    wheels[side][axle].yRot = axle == 0 ? state.steering : 0;
                }
            }
        }
    }
}
