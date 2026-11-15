package cc.nimbusnova.mc.client.space;

import cc.nimbusnova.mc.world.ship.OrbitGeometry;
import cc.nimbusnova.mc.world.ship.ShipMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class OrbitScene {
    private static final Identifier WHITE = Identifier.withDefaultNamespace("textures/block/white_concrete.png");
    private static final OrbitGeometry.Mesh PLANET = OrbitGeometry.planet(), STARS = OrbitGeometry.stars();
    private static final RenderStateDataKey<Boolean> IN_ORBIT = RenderStateDataKey.create(() -> "nimbus_nova:orbit_scene");

    private OrbitScene() {
        /* w */
    }

    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            final boolean inOrbit = context.level().dimension().equals(ShipMap.DIMENSION);
            ((FabricRenderState) context.levelState()).setData(IN_ORBIT, inOrbit);
            if (!inOrbit) return;
            final var sky = context.levelState().skyRenderState;
            sky.skybox = DimensionType.Skybox.OVERWORLD;
            sky.skyColor = new Vector3f(0, 0, 0.003F);
            sky.sunriseAndSunsetColor = new Vector4f(0, 0, 0, 0);
            sky.starBrightness = 1;
            sky.starAngle = 0;
            sky.sunAngle = 180;
            sky.moonAngle = 180;
            context.levelState().cloudColor = 0;
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            if (!((FabricRenderState) context.levelState()).getDataOrDefault(IN_ORBIT, false)) return;
            final var poses = context.poseStack();
            context.submitNodeCollector().submitCustomGeometry(poses, RenderTypes.entityTranslucentEmissive(WHITE),
                    (pose, vertices) -> emit(STARS, pose, vertices));
            final var camera = context.levelState().cameraRenderState.pos;
            poses.pushPose();
            poses.translate(-camera.x, 90 - camera.y, 120 - camera.z);
            poses.scale(20, 20, 20);
            context.submitNodeCollector().submitCustomGeometry(poses, RenderTypes.entitySolid(WHITE),
                    (pose, vertices) -> emit(PLANET, pose, vertices));
            poses.popPose();
        });
    }

    private static void emit(final OrbitGeometry.Mesh mesh, final PoseStack.Pose pose, final VertexConsumer vertices) {
        for (int index = 0; index < mesh.colors().length; index++)
            vertices.addVertex(pose, mesh.positions()[index * 3], mesh.positions()[index * 3 + 1], mesh.positions()[index * 3 + 2])
                    .setColor(mesh.colors()[index]).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }
}
